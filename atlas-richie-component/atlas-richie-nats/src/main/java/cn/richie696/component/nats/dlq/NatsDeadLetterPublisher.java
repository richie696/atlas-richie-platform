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
package cn.richie696.component.nats.dlq;

import cn.richie696.component.nats.config.NatsProperties;
import io.nats.client.JetStream;
import io.nats.client.impl.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DLQ 消息发布器 — 向 DLQ stream 投递原 payload + 注入 NATS-DLQ-* 元数据 headers。
 *
 * <p>职责边界:
 * <ul>
 *   <li>透传原 payload + 原 headers(traceparent 在原 headers 中已存在,不重复注入)</li>
 *   <li>注入 NATS-DLQ-Source-Stream / NATS-DLQ-Retry-Count / NATS-DLQ-Original-Seq 三个
 *       NATS-DLQ-* 元数据 header(便于 DLQ consumer 解析)</li>
 *   <li>同步阻塞 publish,失败 rethrow {@code RuntimeException}(由 advisory consumer 决定 nak advisory)</li>
 * </ul>
 *
 * <p>不做什么:不重试 / 不 metrics / 不 fallback 落盘 / 不静默吞异常。
 */
public class NatsDeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(NatsDeadLetterPublisher.class);

    static final String HEADER_SOURCE_STREAM = "NATS-DLQ-Source-Stream";
    static final String HEADER_RETRY_COUNT = "NATS-DLQ-Retry-Count";
    static final String HEADER_ORIGINAL_SEQ = "NATS-DLQ-Original-Seq";

    private final JetStream jetStream;
    private final NatsProperties properties;

    /**
     * 创建 DLQ 消息发布器。
     *
     * @param jetStream JetStream 发布客户端
     * @param properties NATS 组件配置
     */
    public NatsDeadLetterPublisher(JetStream jetStream, NatsProperties properties) {
        this.jetStream = jetStream;
        this.properties = properties;
    }

    /**
     * 发布 DLQ 消息 — 把原 payload + 注入 NATS-DLQ-* headers 后发到 DLQ subject。
     *
     * <p>{@code dlqSubject} 由调用方(advisory consumer)按 {@code info.getSubject() + dlq.subjectSuffix}
     * 派生(如 {@code orders.persistent.dlq});{@code NatsProperties.Dlq.subjectSuffix} 默认 {@code .dlq}。
     *
     * @param sourceStream    业务原 stream 名(如 {@code ORDERS})
     * @param dlqSubject      DLQ subject(如 {@code orders.persistent.dlq})
     * @param originalPayload 业务原 payload 字节
     * @param originalHeaders 业务原 headers(可为 {@code null};traceparent 已在其中)
     * @param dlqMeta         DLQ 消息元数据
     * @throws RuntimeException 当 {@link JetStream#publish} 抛任何异常时(原异常透传)
     */
    public void publish(String sourceStream,
                        String dlqSubject,
                        byte[] originalPayload,
                        Headers originalHeaders,
                        NatsDeadLetterMessage dlqMeta) {
        Headers dlqHeaders = buildDlqHeaders(originalHeaders, sourceStream, dlqMeta);
        try {
            jetStream.publish(dlqSubject, dlqHeaders, originalPayload);
            log.warn("DLQ publish ok: sourceStream={} dlqSubject={} retryCount={} originalSeq={}",
                    sourceStream, dlqSubject, dlqMeta.retryCount(), dlqMeta.originalStreamSeq());
        } catch (Exception e) {
            // rethrow: advisory consumer catches and decides nak advisory + log
            log.error("DLQ publish failed: sourceStream={} dlqSubject={}", sourceStream, dlqSubject, e);
            throw new RuntimeException("DLQ publish failed: " + dlqSubject, e);
        }
    }

    private Headers buildDlqHeaders(Headers originalHeaders,
                                    String sourceStream,
                                    NatsDeadLetterMessage dlqMeta) {
        Headers dlqHeaders = new Headers();
        // 逐值复制可保留同名多值 header，避免覆盖原消息的追踪或业务上下文。
        if (originalHeaders != null) {
            for (String key : originalHeaders.keySet()) {
                for (String value : originalHeaders.get(key)) {
                    dlqHeaders.add(key, value);
                }
            }
        }
        // DLQ 元数据后写入，以确保消费者能读取本次转存对应的权威值。
        dlqHeaders.add(HEADER_SOURCE_STREAM, sourceStream);
        dlqHeaders.add(HEADER_RETRY_COUNT, Long.toString(dlqMeta.retryCount()));
        dlqHeaders.add(HEADER_ORIGINAL_SEQ, Long.toString(dlqMeta.originalStreamSeq()));
        return dlqHeaders;
    }
}