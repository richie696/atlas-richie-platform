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

import java.util.List;
import java.util.function.Function;

/**
 * 通用池化执行器 — 借 key → 调 lambda → 限流时换 key 重试。
 *
 * <p>用于 {@code RerankModel} / {@code ImageModel} / {@code TextToSpeechModel} /
 * {@code TranscriptionModel} 等多模态能力 — 各能力的 call() 签名差异大,
 * 用一个通用模板统一池化行为。
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 * List<RerankModel> perKeyModels = List.of(
 *     new BailianRerankModel(httpClient, "sk-1", null),
 *     new BailianRerankModel(httpClient, "sk-2", null)
 * );
 * RerankModel pooled = new PooledExecutor<>("product-search", perKeyModels, pool, validator, 2)
 *     .wrap("rerank", model -> model.call(query, docs));
 * }</pre>
 *
 * <p>注意:返回的 wrapper 是"延迟执行"语义 — 真正的 borrow/call/return 发生在每次调用
 * {@code wrapper.call(...)} 时,不是构造时。
 *
 * @author richie696
 */
public class PooledExecutor<T> {

    private static final Logger log = LoggerFactory.getLogger(PooledExecutor.class);

    private final String businessName;
    private final List<T> perKeyModels;
    private final ApiKeyPool pool;
    private final ApiKeyValidator validator;
    private final int retryRounds;

    public PooledExecutor(String businessName,
                          List<T> perKeyModels,
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

    /**
     * 借 key → 调 lambda → 限流切换 key → 重试 retryRounds 轮。
     *
     * @param capability 日志标签 (e.g., "rerank" / "image" / "tts" / "stt")
     * @param call       实际调用 lambda, 入参为 per-key model
     * @param <R>        调用返回类型
     * @return 调用的返回值
     * @throws KeyPoolExhaustedException 池耗尽(所有 key 在冷却)
     */
    public <R> R execute(String capability, CallFunction<T, R> call) {
        Throwable lastError = null;
        for (int round = 0; round < retryRounds; round++) {
            ApiKey key;
            try {
                key = pool.borrow();
            } catch (KeyPoolExhaustedException e) {
                if (lastError != null) {
                    throw new KeyPoolExhaustedException(businessName, retryRounds,
                            e.getTotalKeys(), e.getNumCooldown(), lastError);
                }
                throw e;
            }
            int idx = key.getCreateIndex() >= 0 ? key.getCreateIndex() : 0;
            T perKeyModel = perKeyModels.get(idx);
            try {
                R result;
                try {
                    result = call.apply(perKeyModel);
                } catch (Exception e) {
                    if (e instanceof RuntimeException re) throw re;
                    throw new RuntimeException("PooledExecutor[" + businessName + "] " + capability + " 调用异常", e);
                }
                pool.returnObject(key);
                if (round > 0) {
                    log.info("PooledExecutor[{}] {} 第 {} 轮重试成功", businessName, capability, round + 1);
                }
                return result;
            } catch (RuntimeException e) {
                if (validator.isKeyInvalidating(e)) {
                    log.warn("PooledExecutor[{}] {} key[{}] 触发限流, invalidate 并切换下一个 key (round={})",
                            businessName, capability, key.value(), round + 1);
                    pool.invalidate(key);
                    lastError = e;
                    continue;
                }
                pool.returnObject(key);
                throw e;
            }
        }
        log.error("PooledExecutor[{}] {} {} 轮重试全部失败, 池中 {} 个 key 共 {} 个在冷却",
                businessName, capability, retryRounds, pool.getTotalKeys(), pool.getNumCooldown());
        throw new KeyPoolExhaustedException(businessName, retryRounds,
                pool.getTotalKeys(), pool.getNumCooldown(), lastError);
    }

    /**
     * 与 {@link Function} 语义一致,但允许抛出业务异常(Checked Exception 不抛)。
     */
    @FunctionalInterface
    public interface CallFunction<T, R> {
        R apply(T model) throws Exception;
    }

    // 内部访问器 — 给 PooledXxxModel.stream() 等流式 API 复用池组件
    public List<T> perKeyModels() { return perKeyModels; }
    public ApiKeyPool pool() { return pool; }
    public ApiKeyValidator validator() { return validator; }

    /** 借一个 key — 供流式 API 使用(不立即归还,等订阅结束)。 */
    public ApiKey borrow() {
        return pool.borrow();
    }
}