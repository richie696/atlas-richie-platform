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

import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.config.keypool.KeyPoolProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * API Key 池管理器 — 按"业务名"懒加载创建 {@link ApiKeyPool}。
 *
 * <p>Spring 组件,各工厂 / 服务通过 {@code @Autowired} 注入,然后调
 * {@link #getPool(String, Set)} 拿到对应池。
 *
 * <h2>业务名约定</h2>
 * <ul>
 *   <li>LLM: 业务名 = {@code AiModelProperties.models.<key>}</li>
 *   <li>Rerank: 业务名 = {@code AiModelProperties.rerank.<key>}</li>
 *   <li>TTS / STT / Image / VoiceChat: 同上</li>
 * </ul>
 *
 * <p>池 key = {@code 业务名} + {@code "_"} + {@code capabilities} — 同一业务名 + 不同能力
 * 可独立配置(虽然当前所有能力共用同一 {@link KeyPoolProperties})。
 *
 * @author richie696
 */
@Component
public class ApiKeyPoolManager {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyPoolManager.class);

    private final KeyPoolProperties properties;
    private final ConcurrentMap<String, ApiKeyPoolImpl> pools = new ConcurrentHashMap<>();

    public ApiKeyPoolManager(AiModelProperties aiModelProperties) {
        this.properties = aiModelProperties.getKeyPool();
        log.info("ApiKeyPoolManager 初始化完成, enabled={}, retryRounds={}, cooldownSeconds={}",
                properties.isEnabled(), properties.getRetryRounds(), properties.getCooldownSeconds());
    }

    /**
     * 获取或创建业务名对应的池。
     *
     * @param businessName 业务名(对应 application.yml 的 key)
     * @param apiKeys      该业务的所有 key(去重)
     * @return 对应的 {@link ApiKeyPool}
     * @throws IllegalStateException apiKeys 为空
     */
    public ApiKeyPool getPool(String businessName, Set<String> apiKeys) {
        if (apiKeys == null || apiKeys.isEmpty()) {
            throw new IllegalStateException(
                    "ApiKeyPool[" + businessName + "] 无法创建, apiKeys 为空 — 请检查 application.yml");
        }
        if (!properties.isEnabled()) {
            // 池关闭 — 用 NoOpPool 让调用方直接拿到第一个 key,不做轮询
            return new NoOpApiKeyPool(businessName, apiKeys);
        }
        return pools.computeIfAbsent(poolKey(businessName), name -> {
            log.info("ApiKeyPool[{}] 首次创建, totalKeys={}", name, apiKeys.size());
            return new ApiKeyPoolImpl(name, apiKeys, properties);
        });
    }

    /** 关闭所有池 — Spring bean 销毁时调用。 */
    public void closeAll() {
        pools.forEach((name, p) -> {
            try {
                p.close();
            } catch (Exception e) {
                log.warn("ApiKeyPool[{}] 关闭失败", name, e);
            }
        });
        pools.clear();
    }

    /** 健康检查:列出所有池状态。 */
    public java.util.Map<String, PoolStats> stats() {
        java.util.Map<String, PoolStats> result = new java.util.LinkedHashMap<>();
        pools.forEach((name, p) -> result.put(name, new PoolStats(p.getTotalKeys(), p.getNumActive(), p.getNumCooldown())));
        return result;
    }

    public static String poolKey(String businessName) {
        return businessName;
    }

    /** 池统计快照。 */
    public record PoolStats(int totalKeys, int numActive, int numCooldown) {}

    /**
     * 关闭(池 enabled=false)时的退化实现 — 每次取第一个 key,不做冷却/限流处理。
     * <p>适用场景:旧业务 YAML 还在用单 key 配置,需要平滑过渡。
     */
    private static final class NoOpApiKeyPool implements ApiKeyPool {
        private final String name;
        private final java.util.List<ApiKey> keys;

        NoOpApiKeyPool(String name, Set<String> apiKeys) {
            this.name = name;
            this.keys = new java.util.ArrayList<>();
            for (String k : apiKeys) {
                this.keys.add(new ApiKey(k));
            }
        }

        @Override
        public ApiKey borrow() {
            return keys.get(0);
        }

        @Override
        public void returnObject(ApiKey key) {
        }

        @Override
        public void invalidate(ApiKey key) {
            log.warn("ApiKeyPool[{}] 处于关闭状态, invalidate 忽略", name);
        }

        @Override
        public int getNumActive() {
            return 0;
        }

        @Override
        public int getTotalKeys() {
            return keys.size();
        }

        @Override
        public int getNumCooldown() {
            return 0;
        }
    }
}