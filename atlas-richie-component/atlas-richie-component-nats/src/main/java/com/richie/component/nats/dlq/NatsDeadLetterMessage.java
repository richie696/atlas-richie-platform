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
package com.richie.component.nats.dlq;

import io.nats.client.api.MessageInfo;
import io.nats.client.impl.Headers;

import java.util.Objects;

/**
 * DLQ 消息元数据 record (锁死 4 字段,防 AI-slop 字段膨胀)。
 *
 * <p>字段来源契约:
 * <ul>
 *   <li>{@code retryCount} — 来自 {@link MessageInfo} 在 jnats 2.25.3 不暴露
 *       {@code metaData().deliveredCount()};{@code MessageInfo} 仅承载 advisory payload,
 *       不含 broker 端真实重投计数。本字段降级为 {@code 0L} 兜底;真实 delivery count 应由
 *       上游 consumer 在 {@link io.nats.client.Message} 阶段抓取并显式传入
 *       (或后续 Todo 通过 advisory JSON 反序列化提取)</li>
 *   <li>{@code traceparent} — 来自原消息 {@link Headers},大小写无关匹配
 *       {@code traceparent}/{@code Traceparent}/{@code TRACEPARENT};
 *       原消息无 traceparent 则为 {@code null}</li>
 *   <li>{@code originalStreamSeq} — 来自 {@link MessageInfo#getSeq()}
 *       (jnats 2.25.3 MessageInfo 无 {@code getStreamSequence()};{@code getSeq()}
 *       在 advisory 解码后等价于 stream sequence)</li>
 *   <li>{@code advisoryType} — 当前仅 {@link AdvisoryType#MAX_DELIVERIES} 实际触发,
 *       {@link AdvisoryType#MSG_TERMINATED} 留作未来扩展</li>
 * </ul>
 */
public record NatsDeadLetterMessage(
        long retryCount,
        String traceparent,
        long originalStreamSeq,
        AdvisoryType advisoryType
) {

    /**
     * 从 {@link MessageInfo} + 原 headers 构建 DLQ 消息元数据。
     *
     * @param info 业务原始消息元数据(由 {@code JetStreamManagement.getMessage} 返回)
     * @param originalHeaders 业务原始消息 headers(可为 {@code null})
     * @param type advisory 类型
     */
    public static NatsDeadLetterMessage from(MessageInfo info, Headers originalHeaders, AdvisoryType type) {
        Objects.requireNonNull(info, "info");
        long retryCount = 0L;
        String traceparent = extractTraceparentIgnoreCase(originalHeaders);
        long originalStreamSeq = info.getSeq();
        return new NatsDeadLetterMessage(retryCount, traceparent, originalStreamSeq, type);
    }

    /**
     * 大小写无关查找 traceparent header (NATS server 2.11+ 不再改写大小写,但 OTel SDK
     * 不同版本注入的 header name 大小写不一致,必须兜底)。
     *
     * @param headers 原消息 headers(可为 {@code null})
     * @return 第一个匹配的 traceparent 值;无匹配返回 {@code null}
     */
    private static String extractTraceparentIgnoreCase(Headers headers) {
        if (headers == null) {
            return null;
        }
        for (String key : headers.keySet()) {
            if ("traceparent".equalsIgnoreCase(key)) {
                var values = headers.get(key);
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }
        return null;
    }
}

/**
 * NATS JetStream advisory 类型枚举。
 *
 * <p>当前实现仅处理 {@link #MAX_DELIVERIES};{@link #MSG_TERMINATED} 由业务侧主动
 * 调用 {@code msg.term()} 触发,本期不接管,留作未来扩展。
 *
 * <p>对应 NATS 官方 advisory subject:
 * <ul>
 *   <li>{@code $JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.<stream>.<consumer>}</li>
 *   <li>{@code $JS.EVENT.ADVISORY.CONSUMER.MSG_TERMINATED.<stream>.<consumer>}</li>
 * </ul>
 */
enum AdvisoryType {
    MAX_DELIVERIES,
    MSG_TERMINATED
}