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
package com.richie.component.nats.strategy;

import io.nats.client.impl.Headers;
import io.opentelemetry.api.trace.Span;

/**
 * NATS 链路追踪策略接口
 *
 * <p>封装 OpenTelemetry span 的创建、W3C trace context 注入/提取和 span 生命周期管理。
 * 不同场景使用不同的 span kind：</p>
 * <ul>
 *   <li>PRODUCER — 发布消息时</li>
 *   <li>CONSUMER — 异步消费消息时</li>
 *   <li>CLIENT — RPC 请求端</li>
 *   <li>SERVER — RPC 服务端</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface NatsTracingSupport {

    /**
     * 创建 PRODUCER span（发布消息）
     *
     * @param subject NATS subject
     * @param headers NATS Headers（用于 W3C 注入）
     * @return 新创建的 span
     */
    Span startProducerSpan(String subject, Headers headers);

    /**
     * 创建 CONSUMER span（异步消费消息）
     *
     * @param subject NATS subject
     * @param headers NATS Headers（用于 W3C 提取）
     * @return 新创建的 span
     */
    Span startConsumerSpan(String subject, Headers headers);

    /**
     * 创建 CLIENT span（RPC 请求端）
     *
     * @param subject NATS subject
     * @param headers NATS Headers（用于 W3C 注入）
     * @return 新创建的 span
     */
    Span startClientSpan(String subject, Headers headers);

    /**
     * 创建 SERVER span（RPC 服务端）
     *
     * @param subject NATS subject
     * @param headers NATS Headers（用于 W3C 提取）
     * @return 新创建的 span
     */
    Span startServerSpan(String subject, Headers headers);

    /**
     * 结束 span，记录成功/失败状态
     *
     * @param span     待结束的 span
     * @param success  是否成功
     * @param errorMsg 失败时的错误信息（成功时传 null）
     */
    void finishSpan(Span span, boolean success, String errorMsg);
}
