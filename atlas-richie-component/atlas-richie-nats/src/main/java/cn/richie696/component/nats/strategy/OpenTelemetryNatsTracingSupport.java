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
package cn.richie696.component.nats.strategy;

import cn.richie696.component.nats.NatsConstants;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import java.lang.Iterable;

/**
 * 基于 OpenTelemetry 的 NATS 链路追踪实现
 *
 * <p>通过 W3C trace context 标准实现跨服务链路追踪。关键链路：</p>
 * <ul>
 *   <li>W3C 注入（{@code SETTER}）：将当前 span 的 trace context 写入 NATS Headers，
 *       让对端消费者能把本条消息纳入同一 trace；</li>
 *   <li>W3C 提取（{@code GETTER}）：从 NATS Headers 还原上游 trace context，
 *       作为新建 span 的 parent；</li>
 *   <li>MDC 注入（{@code injectMdc}）：把 traceId/spanId 放入 SLF4J MDC，
 *       让业务日志自动与 trace 关联；{@code finishSpan} 时清理避免线程池复用泄漏。</li>
 * </ul>
 *
 * <p>SpanKind 区分：发布与 RPC 客户端用 {@code PRODUCER/CLIENT}（主动发起的角色），
 * 消费与 RPC 服务端用 {@code CONSUMER/SERVER}（被驱动、会从 Headers 提取 parent）。
 * 参考 gRPC 组件的 {@code GrpcClientTracingInterceptor} / {@code GrpcServerTracingInterceptor} 模式。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class OpenTelemetryNatsTracingSupport implements NatsTracingSupport {

    private static final AttributeKey<String> MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system");
    private static final AttributeKey<String> MESSAGING_DESTINATION = AttributeKey.stringKey("messaging.destination.name");
    private static final AttributeKey<String> MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation");

    /**
     * W3C 标准 TextMap 注入器 — 将 trace context 写入 NATS Headers。
     * 直接方法引用 {@code Headers::put}，因为 NATS {@code Headers.put(String, String)}
     * 的签名（接收 carrier + key + value）与 OpenTelemetry {@link TextMapSetter} 一致，
     * 避免额外写一行 lambda。
     */
    private static final TextMapSetter<Headers> SETTER = Headers::put;

    /**
     * W3C 标准 TextMap 提取器 — 从 NATS Headers 中提取 trace context。
     * 实现需保证：{@link #keys} 返回所有可能携带 trace context 的 Header 名
     * （NATS Headers 一律全大写），{@link #get} 按名取值（NATS 同名 Header 可出现多值，
     * 取首个即可，遵循 W3C 单 trace 假设）。
     */
    private static final TextMapGetter<Headers> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers headers) {
            return headers.keySet();
        }

        @Override
        public String get(Headers headers, String key) {
            if (headers == null) {
                return null;
            }
            var values = headers.get(key);
            return (values != null && !values.isEmpty()) ? values.getFirst() : null;
        }
    };

    /** OpenTelemetry Tracer，由 {@code openTelemetry.getTracer(...)} 解析而来，注入到所有新建 span。 */
    private final Tracer tracer;
    /** {@code false} 时所有 {@code startXxxSpan} 直接返回 {@link Span#getInvalid()}，不再访问 Headers。 */
    private final boolean enabled;

    /**
     * 默认构造：从全局 {@link GlobalOpenTelemetry} 取 {@link Tracer}。
     *
     * @param enabled 是否启用链路追踪；{@code false} 时返回不可用的占位 span
     */
    public OpenTelemetryNatsTracingSupport(boolean enabled) {
        this(enabled, GlobalOpenTelemetry.get());
    }

    /**
     * 显式注入 {@link OpenTelemetry} 的构造器（便于测试或使用独立 SDK 实例）。
     *
     * @param enabled        是否启用链路追踪
     * @param openTelemetry  提供 {@code Tracer} 的 SDK 实例
     */
    public OpenTelemetryNatsTracingSupport(boolean enabled, OpenTelemetry openTelemetry) {
        this.enabled = enabled;
        this.tracer = openTelemetry.getTracer(NatsConstants.TRACER_NAME, NatsConstants.TRACER_VERSION);
    }

    /**
     * 启动发布端 PRODUCER span，并把当前 trace context 通过 W3C 注入到 NATS Headers。
     *
     * @param subject NATS subject
     * @param headers NATS Headers（用于 W3C 注入）
     * @return 新创建的 span；若未启用则返回 {@link Span#getInvalid()}
     */
    @Override
    public Span startProducerSpan(String subject, Headers headers) {
        if (!enabled) {
            return Span.getInvalid();
        }
        var span = tracer.spanBuilder(subject + " publish")
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute(MESSAGING_SYSTEM, "nats")
                .setAttribute(MESSAGING_DESTINATION, subject)
                .setAttribute(MESSAGING_OPERATION, "publish")
                .startSpan();

        // 顺序：先 MDC 注入，方便后续 injectW3C 内若打日志也能正确关联；最后 W3C 注入，让对端把消息纳入同一 trace。
        injectMdc(span);
        injectW3C(headers, span);
        return span;
    }

    /**
     * 启动消费端 CONSUMER span，从 NATS Headers 提取上游 trace context 作为 parent。
     *
     * @param subject NATS subject
     * @param headers NATS Headers（用于 W3C 提取）
     * @return 新创建的 span；若未启用则返回 {@link Span#getInvalid()}
     */
    @Override
    public Span startConsumerSpan(String subject, Headers headers) {
        if (!enabled) {
            return Span.getInvalid();
        }
        var extracted = extractW3C(headers);
        var span = tracer.spanBuilder(subject + " receive")
                .setParent(extracted)
                .setSpanKind(SpanKind.CONSUMER)
                .setAttribute(MESSAGING_SYSTEM, "nats")
                .setAttribute(MESSAGING_DESTINATION, subject)
                .setAttribute(MESSAGING_OPERATION, "receive")
                .startSpan();

        // 消费端不调用 injectW3C：trace 已经由 parent 决定，无需再注入 Headers。
        injectMdc(span);
        return span;
    }

    /**
     * 启动 RPC 客户端 CLIENT span，并向 NATS Headers 注入 trace context。
     *
     * @param subject NATS subject
     * @param headers NATS Headers（用于 W3C 注入）
     * @return 新创建的 span；若未启用则返回 {@link Span#getInvalid()}
     */
    @Override
    public Span startClientSpan(String subject, Headers headers) {
        if (!enabled) {
            return Span.getInvalid();
        }
        var span = tracer.spanBuilder(subject + " request")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(MESSAGING_SYSTEM, "nats")
                .setAttribute(MESSAGING_DESTINATION, subject)
                .setAttribute(MESSAGING_OPERATION, "request")
                .startSpan();

        injectMdc(span);
        injectW3C(headers, span);
        return span;
    }

    /**
     * 启动 RPC 服务端 SERVER span，从 NATS Headers 提取上游 trace context 作为 parent。
     *
     * @param subject NATS subject
     * @param headers NATS Headers（用于 W3C 提取）
     * @return 新创建的 span；若未启用则返回 {@link Span#getInvalid()}
     */
    @Override
    public Span startServerSpan(String subject, Headers headers) {
        if (!enabled) {
            return Span.getInvalid();
        }
        var extracted = extractW3C(headers);
        var span = tracer.spanBuilder(subject + " handle")
                .setParent(extracted)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(MESSAGING_SYSTEM, "nats")
                .setAttribute(MESSAGING_DESTINATION, subject)
                .setAttribute(MESSAGING_OPERATION, "handle")
                .startSpan();

        injectMdc(span);
        return span;
    }

    /**
     * 结束 span：根据 {@code success} 设置状态码；finally 强制 {@code span.end()}
     * 并清理 MDC 中的 traceId/spanId，防止线程池复用时携带上个消息的痕迹。
     *
     * @param span     待结束的 span，为 {@code null} 或 {@link Span#getInvalid()} 时直接返回
     * @param success  处理是否成功
     * @param errorMsg 失败时的错误信息（成功时传 {@code null}）
     */
    @Override
    public void finishSpan(Span span, boolean success, String errorMsg) {
        if (span == null || span == Span.getInvalid()) {
            return;
        }
        try {
            if (success) {
                span.setStatus(StatusCode.OK);
            } else {
                span.setStatus(StatusCode.ERROR, errorMsg);
            }
        } finally {
            // 必须 close span，否则 exporter 拿不到完整 span；MDC 同步清理，避免下个消息混入旧 trace。
            span.end();
            MDC.remove(NatsConstants.MDC_TRACE_ID);
            MDC.remove(NatsConstants.MDC_SPAN_ID);
        }
    }

    // ===== 内部方法 =====

    private void injectW3C(Headers headers, Span span) {
        // 把 span 加入当前 OTel Context 后再 makeCurrent，使 propagator 能从“当前上下文”读到正确的 trace flags。
        var otelContext = Context.current().with(span);
        try (Scope ignored = otelContext.makeCurrent()) {
            // 由全局配置的 propagator（默认 W3C TraceContext+Baggage）负责把 context 序列化为 header。
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                    .inject(otelContext, headers, SETTER);
        }
        // try-with-resources 会在结束时把 Context 复位，避免污染调用线程的 OTel Context。
    }

    private Context extractW3C(Headers headers) {
        // 即使 headers 为空也要走一遍 extract，返回的 Context 就是“空的 parent”，保证 spanBuilder 拿到非 null 的 parent。
        return GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), headers, GETTER);
    }

    private void injectMdc(Span span) {
        var spanContext = span.getSpanContext();
        // 把 traceId/spanId 写入 SLF4J MDC，方便业务侧日志框架（logback/log4j）的 pattern 直接打印 %X{traceId} 关联 trace。
        MDC.put(NatsConstants.MDC_TRACE_ID, spanContext.getTraceId());
        MDC.put(NatsConstants.MDC_SPAN_ID, spanContext.getSpanId());
    }
}
