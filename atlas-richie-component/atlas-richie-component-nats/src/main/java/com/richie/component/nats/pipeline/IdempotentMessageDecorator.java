/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.nats.pipeline;

import com.richie.component.nats.NatsConstants;
import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.strategy.NatsIdempotentChecker;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * 幂等去重装饰器
 *
 * <p>基于消息 ID 进行去重检查。首次处理放行到内层 Handler，
 * 重复消息直接跳过（JetStream 场景自动 ack 确认）。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class IdempotentMessageDecorator {

    private final NatsIdempotentChecker idempotentChecker;
    private final long ttlMillis;

    public IdempotentMessageDecorator(NatsIdempotentChecker idempotentChecker, long ttlMillis) {
        this.idempotentChecker = idempotentChecker;
        this.ttlMillis = ttlMillis;
    }

    /**
     * 创建装饰器函数
     *
     * @param inner 内层 Handler
     * @return 包装后的 Handler
     */
    public NatsMessageHandler decorate(NatsMessageHandler inner) {
        return message -> {
            String messageId = extractMessageId(message);
            if (messageId != null && !idempotentChecker.isFirstTime(
                    NatsConstants.IDEMPOTENT_KEY_PREFIX + messageId, ttlMillis)) {
                log.debug("NATS idempotent: duplicate message [{}], skipping", messageId);
                // JetStream 场景：重复消息也需要 ack，避免反复投递
                message.ack();
                return;
            }

            try {
                inner.handle(message);
            } catch (Exception e) {
                // 处理失败，清除去重记录，允许重试
                if (messageId != null) {
                    idempotentChecker.clear(NatsConstants.IDEMPOTENT_KEY_PREFIX + messageId);
                }
                throw e;
            }
        };
    }

    private String extractMessageId(Message message) {
        // 优先使用 NATS 内置的 Message-Id Header
        Headers headers = message.getHeaders();
        if (headers != null) {
            var msgIdValues = headers.get(NatsConstants.HEADER_MESSAGE_ID);
            if (msgIdValues != null && !msgIdValues.isEmpty()) {
                return msgIdValues.getFirst();
            }
        }

        // 其次使用 JetStream metadata 中的 stream sequence
        if (message.isJetStream()) {
            try {
                var meta = message.metaData();
                return meta.getStream() + "-" + meta.streamSequence();
            } catch (Exception ignored) {
                // 非 JetStream 消息或 metadata 获取失败
            }
        }

        // 最终兜底：使用 subject + 数据哈希作为去重 key
        return message.getSubject() + "-" + UUID.nameUUIDFromBytes(message.getData());
    }
}
