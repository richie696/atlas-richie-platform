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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 池化 ChatModel — 持 N 个 per-key ChatModel 实例,按 {@link ApiKeyPool} 借/还 + 限流时切换 key 重试。
 *
 * <h2>工作模式</h2>
 * <pre>{@code
 * for (int round = 0; round < retryRounds; round++) {
 *     ApiKey key = pool.borrow();
 *     ChatModel perKeyModel = perKeyModels.get(key.getCreateIndex());
 *     try {
 *         ChatResponse resp = perKeyModel.call(prompt);
 *         pool.returnObject(key);
 *         return resp;
 *     } catch (Exception e) {
 *         if (validator.isKeyInvalidating(e)) {
 *             pool.invalidate(key);   // 限流 → 池移除 + 冷却
 *             continue;               // 下一轮换 key
 *         }
 *         pool.returnObject(key);
 *         throw e;                  // 非限流错误 — 立即抛
 *     }
 * }
 * throw new KeyPoolExhaustedException(...);
 * }</pre>
 *
 * <h2>为何按 ChatModel 粒度而不是 ChatClient 粒度</h2>
 * <ul>
 *   <li>Spring AI 的 ChatModel 接口最薄(2 方法: {@code call} + {@code stream}),容易装饰</li>
 *   <li>per-key ChatModel 的构造需要把 apiKey 烧进底层 OpenAiApi(不可热改),所以必须预建 N 个</li>
 *   <li>外层 {@code ChatClient.builder(pooledChatModel).build()} 包装一次,业务侧拿到的还是标准 ChatClient</li>
 * </ul>
 *
 * @author richie696
 */
public class PooledChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(PooledChatModel.class);

    private final List<ChatModel> perKeyModels;
    private final ApiKeyPool pool;
    private final ApiKeyValidator validator;
    private final int retryRounds;
    private final String businessName;

    public PooledChatModel(String businessName,
                           List<ChatModel> perKeyModels,
                           ApiKeyPool pool,
                           ApiKeyValidator validator,
                           int retryRounds) {
        if (perKeyModels == null || perKeyModels.isEmpty()) {
            throw new IllegalArgumentException("perKeyModels 不能为空");
        }
        this.businessName = businessName;
        this.perKeyModels = List.copyOf(perKeyModels);
        this.pool = pool;
        this.validator = validator;
        this.retryRounds = Math.max(1, retryRounds);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Throwable lastError = null;
        for (int round = 0; round < retryRounds; round++) {
            ApiKey key;
            try {
                key = pool.borrow();
            } catch (KeyPoolExhaustedException e) {
                if (lastError != null) {
                    throw new KeyPoolExhaustedException(businessName, retryRounds, e.getTotalKeys(), e.getNumCooldown(), lastError);
                }
                throw e;
            }
            ChatModel perKeyModel = perKeyModels.get(key.getCreateIndex() >= 0 ? key.getCreateIndex() : 0);
            try {
                ChatResponse resp = perKeyModel.call(prompt);
                pool.returnObject(key);
                if (round > 0) {
                    log.info("PooledChatModel[{}] 第 {} 轮重试成功", businessName, round + 1);
                }
                return resp;
            } catch (RuntimeException e) {
                if (validator.isKeyInvalidating(e)) {
                    log.warn("PooledChatModel[{}] key[{}] 触发限流, invalidate 并切换下一个 key (round={})",
                            businessName, key.value(), round + 1);
                    pool.invalidate(key);
                    lastError = e;
                    continue;
                }
                pool.returnObject(key);
                throw e;
            }
        }
        log.error("PooledChatModel[{}] {} 轮重试全部失败, 池中 {} 个 key 共 {} 个在冷却",
                businessName, retryRounds, pool.getTotalKeys(), pool.getNumCooldown());
        throw new KeyPoolExhaustedException(businessName, retryRounds, pool.getTotalKeys(), pool.getNumCooldown(), lastError);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 流式模式:借 1 个 key 用到底,过程中切换复杂(订阅方已订阅),遇到限流直接抛 — 上层决定 retry
        ApiKey key = pool.borrow();
        try {
            ChatModel perKeyModel = perKeyModels.get(key.getCreateIndex() >= 0 ? key.getCreateIndex() : 0);
            return perKeyModel.stream(prompt)
                    .doOnError(e -> {
                        if (validator.isKeyInvalidating(e)) {
                            pool.invalidate(key);
                        } else {
                            pool.returnObject(key);
                        }
                    })
                    .doOnComplete(() -> pool.returnObject(key))
                    .doOnCancel(() -> pool.returnObject(key));
        } catch (RuntimeException e) {
            pool.returnObject(key);
            throw e;
        }
    }
}