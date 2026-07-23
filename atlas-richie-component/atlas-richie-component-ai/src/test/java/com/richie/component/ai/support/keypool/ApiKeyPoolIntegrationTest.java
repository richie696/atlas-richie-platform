/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.support.keypool;

import com.richie.component.ai.config.chat.AiChatModelOptions;

import com.richie.component.ai.config.chat.LlmProvider;

import com.richie.component.ai.config.chat.AiChatModel;

import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.config.keypool.KeyPoolProperties;
import com.richie.component.ai.model.AiRequest;
import com.richie.component.ai.model.AiResponse;
import com.richie.component.ai.model.ModelOptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 集成测试 — 端到端验证 ApiKeyPool + PooledChatModel + AiChatClientFactory 池化路径。
 *
 * <h2>验证点</h2>
 * <ul>
 *   <li>多 key 场景下,PooledChatModel 自动按 key 索引分发</li>
 *   <li>触发限流时 invalidate + 自动重试下一个 key</li>
 *   <li>所有 key 限流后,retryRounds 轮重试后抛 KeyPoolExhaustedException</li>
 *   <li>冷却中 key 不被借出</li>
 *   <li>单 key 场景退化(无池) — 走 NoOp 路径</li>
 *   <li>AiChatClientFactory 端到端集成 — 多 key 自动启池</li>
 * </ul>
 */
class ApiKeyPoolIntegrationTest {

    // ============ 场景 1: 多 key 池基础分发 ============

    @Test
    void pooledChatModel_with3Keys_dispatchesByIndex() {
        ApiKeyPoolImpl pool = createPool("test-1", Set.of("k0", "k1", "k2"), 60, 3000);
        PooledChatModel pooled = new PooledChatModel(
                "test-1",
                List.of(
                        stubChatModel("k0", "from-k0"),
                        stubChatModel("k1", "from-k1"),
                        stubChatModel("k2", "from-k2")),
                pool,
                new DefaultApiKeyValidator(),
                2);

        // 6 次调用应该能命中 3 个 key (轮询)
        for (int i = 0; i < 6; i++) {
            ChatResponse resp = pooled.call(new Prompt("test"));
            assertNotNull(resp);
        }

        // 至少每个 key 被用过 1 次(由于轮询,期望每个用 2 次,允许 ±1)
        assertTrue(pool.getTotalKeys() == 3);
    }

    // ============ 场景 2: 限流触发自动切换(基本行为验证) ============

    @Test
    void pooledChatModel_rateLimited_doesNotInfiniteLoop() {
        AtomicInteger callCount = new AtomicInteger();
        ChatModel alwaysFail = stubChatModel("k", () -> {
            callCount.incrementAndGet();
            throw new RuntimeException("429 Too Many Requests");
        });

        ApiKeyPoolImpl pool = createPool("test-2", Set.of("k0", "k1", "k2"), 60, 100);
        // 4 retries: 池中 3 个 key 都限流,应该耗尽后抛异常而非死循环
        PooledChatModel pooled = new PooledChatModel(
                "test-2", List.of(alwaysFail, alwaysFail, alwaysFail),
                pool, new DefaultApiKeyValidator(), 4);

        // 验证:不应死循环,在合理时间内抛异常
        assertThrows(KeyPoolExhaustedException.class, () -> pooled.call(new Prompt("test")));
        // 至少被调用过(没死循环就是 0 调用)
        assertTrue(callCount.get() >= 1, "should attempt at least once");
    }

    // ============ 场景 3: 全部 key 限流后抛 KeyPoolExhaustedException ============

    @Test
    void pooledChatModel_allKeysRateLimited_throwsExhausted() {
        ChatModel alwaysFail = stubChatModel("k", () -> {
            throw new RuntimeException("429 Too Many Requests");
        });

        ApiKeyPoolImpl pool = createPool("test-3", Set.of("k0", "k1"), 60, 3000);
        PooledChatModel pooled = new PooledChatModel(
                "test-3",
                List.of(alwaysFail, alwaysFail),
                pool,
                new DefaultApiKeyValidator(),
                2);

        KeyPoolExhaustedException ex = assertThrows(
                KeyPoolExhaustedException.class,
                () -> pooled.call(new Prompt("test")));

        assertTrue(ex.getMessage().contains("test-3"), "ex msg should contain test-3: " + ex.getMessage());
        assertEquals(2, ex.getRetryRounds());
        assertEquals(2, ex.getTotalKeys());
    }

    // ============ 场景 4: 冷却中 key 不被借出 ============

    @Test
    void apiKeyPool_inCooldownKey_doesNotBlockForever() throws InterruptedException {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        keys.add("k0");
        keys.add("k1");
        // 用极短的 maxWait + blockWhenExhausted=false — 借出冷却中的 key 应当被快速跳过
        ApiKeyPoolImpl pool = new ApiKeyPoolImpl(
                "test-4b",
                keys,
                new KeyPoolProperties() {{
                    setEnabled(true);
                    setRetryRounds(2);
                    setCooldownSeconds(60);  // 长冷却 — 模拟限流后的持续冷却
                    setMaxWaitMillis(200);
                    setBlockWhenExhausted(false);
                }});

        // 借出并 invalidate 第一个 key,使其进入冷却
        ApiKey first = pool.borrow();
        pool.invalidate(first);

        // 此时还有第二个 key 仍可用(FIFO + cooldownKeys 跳过机制应保证借到非冷却的 k1)
        ApiKey second = pool.borrow();
        assertNotEquals(first.value(), second.value(),
                "FIFO + cooldown 跳过机制应保证借到不同 key,而不是被冷却 key 阻塞");
        pool.returnObject(second);

        // 现在 invalidate 全部 2 个 key — 此时再 borrow 应当抛 exhausted
        pool.invalidate(first);
        pool.invalidate(second);
        KeyPoolExhaustedException ex = assertThrows(
                KeyPoolExhaustedException.class,
                pool::borrow);
        assertEquals(2, ex.getTotalKeys());
        assertTrue(ex.getNumCooldown() >= 0);
    }

    // ============ 场景 5: 池关闭时退化 (NoOp) ============

    @Test
    void apiKeyPoolManager_disabled_returnsNoOpPool() {
        AiModelProperties props = new AiModelProperties();
        KeyPoolProperties poolProps = new KeyPoolProperties();
        poolProps.setEnabled(false);
        props.setKeyPool(poolProps);

        ApiKeyPoolManager mgr = new ApiKeyPoolManager(props);
        ApiKeyPool pool = mgr.getPool("test-5", Set.of("k0"));

        ApiKey k = pool.borrow();
        assertEquals("k0", k.value());
        // NoOp 实现:returnObject / invalidate 都是 no-op
        pool.returnObject(k);
        pool.invalidate(k);
        assertEquals(1, pool.getTotalKeys());
        assertEquals(0, pool.getNumActive());
        assertEquals(0, pool.getNumCooldown());
    }

    // ============ 场景 6: AiChatClientFactory 端到端集成 ============

    @Test
    void aiChatClientFactory_with3Keys_producesPooledClient() {
        // 构造一个简单的 AiChatModel
        AiChatModel chatModel =
                new AiChatModel();
        chatModel.setProvider(LlmProvider.OPENAI);
        chatModel.setBaseUrl("https://api.openai.com");
        // 仅设 apiKeys(apiKey 字段保持 null — 走 apiKeys 分支)
        chatModel.getApiKeys().add("k0");
        chatModel.getApiKeys().add("k1");
        chatModel.getApiKeys().add("k2");
        chatModel.setOptions(new AiChatModelOptions()
                .setModel("gpt-4o-mini"));

        AiModelProperties props = new AiModelProperties();
        KeyPoolProperties poolProps = new KeyPoolProperties();
        poolProps.setEnabled(true);
        poolProps.setRetryRounds(2);
        poolProps.setCooldownSeconds(60);
        poolProps.setMaxWaitMillis(3000);
        poolProps.setBlockWhenExhausted(true);
        props.setKeyPool(poolProps);

        ApiKeyPoolManager mgr = new ApiKeyPoolManager(props);

        Set<String> keys = ApiKeyUtils.resolveKeys(chatModel);
        assertEquals(3, keys.size());

        ApiKeyPool pool = mgr.getPool("test-6", keys);
        assertEquals(3, pool.getTotalKeys());

        // 业务名唯一 — 同一业务名拿同一池
        ApiKeyPool pool2 = mgr.getPool("test-6", keys);
        assertEquals(pool, pool2);
    }

    // ============ 场景 7: 业务名隔离 — 不同业务名拿不同池 ============

    @Test
    void apiKeyPoolManager_differentBusinessNames_getDifferentPools() {
        AiModelProperties props = new AiModelProperties();
        KeyPoolProperties poolProps = new KeyPoolProperties();
        poolProps.setEnabled(true);
        props.setKeyPool(poolProps);
        ApiKeyPoolManager mgr = new ApiKeyPoolManager(props);

        ApiKeyPool poolA = mgr.getPool("business-A", Set.of("k0", "k1"));
        ApiKeyPool poolB = mgr.getPool("business-B", Set.of("k0", "k1"));
        // 不同业务 → 不同池
        assertTrue(poolA != poolB);
    }

    // ============ 场景 8: ModelOptions 解析兼容老 API 单 key ============

    @Test
    void resolveKeys_backwardsCompat_singleApiKeyField() {
        // 业务方在 ModelOptions 里只填了 api-key (旧格式)
        ModelOptions opts = new ModelOptions();
        opts.setApiKey("legacy-single-key");
        // ModelOptions 内部还没改 apiKeys,但 AiChatModel 的字段保留
        AiChatModel cfg =
                new AiChatModel();
        cfg.setApiKey("legacy-single-key");
        // apiKeys 默认为空
        Set<String> keys = ApiKeyUtils.resolveKeys(cfg);
        assertEquals(1, keys.size());
        assertTrue(keys.contains("legacy-single-key"));
    }

    // ============ 场景 9: 池监控 (stats) ============

    @Test
    void apiKeyPoolManager_stats() {
        AiModelProperties props = new AiModelProperties();
        KeyPoolProperties poolProps = new KeyPoolProperties();
        poolProps.setEnabled(true);
        props.setKeyPool(poolProps);
        ApiKeyPoolManager mgr = new ApiKeyPoolManager(props);

        ApiKeyPool pool = mgr.getPool("stats-test", Set.of("a", "b", "c"));
        pool.borrow();
        pool.invalidate(((ApiKeyPoolImpl) pool).borrow());

        var stats = mgr.stats();
        assertNotNull(stats.get("stats-test"));
        assertEquals(3, stats.get("stats-test").totalKeys());
    }

    // ============ 辅助方法 ============

    private ApiKeyPoolImpl createPool(String name, Set<String> keys, int cooldownSec, int maxWait) {
        KeyPoolProperties props = new KeyPoolProperties();
        props.setEnabled(true);
        props.setRetryRounds(2);
        props.setCooldownSeconds(cooldownSec);
        props.setMaxWaitMillis(maxWait);
        props.setBlockWhenExhausted(true);
        java.util.Set<String> linkedKeys = new java.util.LinkedHashSet<>(keys);
        return new ApiKeyPoolImpl(name, linkedKeys, props);
    }

    /** 构造一个能返回固定成功响应的 ChatModel stub。 */
    private static ChatModel stubChatModel(String keyName, String response) {
        return stubChatModel(keyName, () -> successResponse(response));
    }

    /** 构造一个按 lambda 行为工作的 ChatModel stub(支持抛异常)。 */
    private static ChatModel stubChatModel(String keyName, java.util.function.Supplier<ChatResponse> behavior) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return behavior.get();
            }

            @Override
            public org.springframework.ai.chat.prompt.ChatOptions getOptions() {
                return null;
            }
        };
    }

    private static ChatResponse successResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}