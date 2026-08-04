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
package cn.richie696.component.nats.pipeline;

import cn.richie696.component.nats.NatsConstants;
import cn.richie696.component.nats.strategy.NatsIdempotentChecker;
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

    /** 用于登记/查询/清除消息去重记录的策略实现。 */
    private final NatsIdempotentChecker idempotentChecker;
    /** 去重记录的 TTL（毫秒），由上游配置传入。 */
    private final long ttlMillis;

    /**
     * @param idempotentChecker 幂等去重检查器
     * @param ttlMillis        去重 TTL（毫秒）
     */
    public IdempotentMessageDecorator(NatsIdempotentChecker idempotentChecker, long ttlMillis) {
        this.idempotentChecker = idempotentChecker;
        this.ttlMillis = ttlMillis;
    }

    /**
     * 创建装饰器函数：按消息 ID 进行去重判断；命中重复则在 JetStream 场景下 ack 后直接返回
     * （否则会被 broker 反复投递），未命中则放行给内层 Handler；Handler 抛异常时清除记录以便重试。
     *
     * @param inner 内层 Handler
     * @return 包装后的 Handler
     */
    public NatsMessageHandler decorate(NatsMessageHandler inner) {
        return message -> {
            String messageId = extractMessageId(message);
            if (messageId != null && !idempotentChecker.isFirstTime(messageId, ttlMillis)) {
                log.debug("NATS idempotent: duplicate message [{}], skipping", messageId);
                // JetStream 场景：重复消息也需要 ack，避免反复投递
                message.ack();
                return;
            }

            try {
                inner.handle(message);
            } catch (Exception e) {
                // 处理失败，清除去重记录，允许重试（否则下一次 Redeliver 会被误判为重复）
                if (messageId != null) {
                    idempotentChecker.clear(messageId);
                }
                throw e;
            }
        };
    }

    private String extractMessageId(Message message) {
        // 三级 fallback：显式 ID > JetStream 元信息 > subject+载荷 哈希，覆盖所有可用的可识别信息：
        // 1) 生产者主动写入的 Message-Id Header（NATS 标准）
        // 2) JetStream 提供的 stream+seq（broker 保证单调递增，跨实例安全）
        // 3) subject + 载荷内容哈希（兜底：保证极端情况下也有可重复的 key）
        Headers headers = message.getHeaders();
        if (headers != null) {
            var msgIdValues = headers.get(NatsConstants.HEADER_MESSAGE_ID);
            if (msgIdValues != null && !msgIdValues.isEmpty()) {
                return msgIdValues.getFirst();
            }
        }

        if (message.isJetStream()) {
            try {
                var meta = message.metaData();
                // stream + sequence 组合在单 stream 内天然单调，足以唯一标识消息。
                return meta.getStream() + "-" + meta.streamSequence();
            } catch (Exception ignored) {
                // 非 JetStream 消息或 metadata 获取失败（部分发布场景可能不携带），继续往下走兜底分支。
            }
        }

        // 兜底：UUID.nameUUIDFromBytes 使用 UUID v3（MD5），对同一份载荷始终产出相同 key。
        return message.getSubject() + "-" + UUID.nameUUIDFromBytes(message.getData());
    }
}
