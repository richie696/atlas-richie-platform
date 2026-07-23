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
package com.richie.component.ai.config.keypool;

import lombok.Data;

/**
 * API Key 池全局配置 — 映射 {@code platform.component.ai.key-pool}。
 *
 * <p>所有能力(LLM / Rerank / Image / TTS / STT / VoiceChat)的 key 池都共用此配置 —
 * 没必要每个能力单独配。例外情况:业务侧可在 {@code @ConfigurationProperties} 之外
 * 通过代码动态覆盖。
 *
 * <h2>典型场景</h2>
 * 用户购买 N 个 Token Plan key(同一厂商 / 同一账号),配置进 {@code api-keys: [...]} 池。
 * 组件调用时:
 * <ol>
 *   <li>从池中取一个 key 调用</li>
 *   <li>命中限流(429 / "rate limit")→ 池中标记失效,冷却 cooldown-seconds</li>
 *   <li>自动换下一个 key 重试,最多 retry-rounds 轮</li>
 *   <li>所有 key 都不可用 → 抛 {@code KeyPoolExhaustedException},业务方降级</li>
 * </ol>
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class KeyPoolProperties {

    /** 是否启用 Key 池。false 时回退为单 key 行为(取 apiKeys 第一个)。 */
    private boolean enabled = true;

    /**
     * 限流后重试轮数。
     * <p>用户决策: 2 轮 — 即"遍历 2 遍"语义。第 1 轮每个 key 试一次;若全部限流,第 2 轮
     * 再次尝试(期望冷却已生效);仍失败则抛 {@code KeyPoolExhaustedException}。
     */
    private int retryRounds = 2;

    /**
     * 限流后单个 key 的冷却时间(秒)。
     * <p>key 被 {@code invalidate} 后,在 cooldown-seconds 内不再被借出。
     * 冷却结束后,key 自动回到池中(由 Pool 内部 lazy 检测,无需定时任务)。
     */
    private int cooldownSeconds = 60;

    /**
     * borrow 超时(毫秒) — 池元素全被借出且配置为阻塞时,等待新 key 的最长时间。
     * <p>超时时抛 {@code KeyPoolExhaustedException}(区别于"全部限流",后者是业务可重试场景)。
     */
    private long maxWaitMillis = 3000L;

    /**
     * 池耗尽时是否阻塞等待。
     * <ul>
     *   <li>true(默认):borrowObject 阻塞直到有可用 key 或超时</li>
     *   <li>false:立即抛 {@code NoSuchElementException} — 适合高 QPS + 短超时场景</li>
     * </ul>
     */
    private boolean blockWhenExhausted = true;
}