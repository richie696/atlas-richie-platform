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
package com.richie.component.nats.strategy;

import com.richie.component.nats.strategy.NatsErrorStrategy;
import io.nats.client.Message;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认 NATS 错误处理策略实现
 *
 * <p>记录错误日志并按条件决策重试。默认仅对非业务异常（如网络超时）进行重试。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class DefaultNatsErrorStrategy implements NatsErrorStrategy {

    @Override
    public void onPublishError(String subject, byte[] data, Exception e) {
        log.error("NATS publish error on subject [{}], data length={}", subject,
                data != null ? data.length : 0, e);
    }

    @Override
    public void onConsumeError(String subject, Message msg, Exception e) {
        log.error("NATS consume error on subject [{}]", subject, e);
    }

    @Override
    public boolean shouldRetry(Exception e, int attempt, int maxAttempts) {
        if (attempt >= maxAttempts) {
            log.warn("NATS error: max retries ({}) reached, giving up", maxAttempts);
            return false;
        }
        // 默认对所有异常进行重试（排除中断异常）
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
        log.warn("NATS error: attempt {}/{}, will retry", attempt, maxAttempts);
        return true;
    }
}
