/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.nats.strategy;

import com.richie.component.nats.NatsConstants;
import com.richie.component.nats.strategy.NatsTracingSupport;
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

/**
 * 基于 OpenTelemetry 的 NATS 链路追踪实现
 *
 * <p>通过 W3C trace context 标准实现跨服务链路追踪。参考 gRPC 组件的
 * {@code GrpcClientTracingInterceptor} / {@code GrpcServerTracingInterceptor} 模式。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class OpenTelemetryNatsTracingSupport implements NatsTracingSupport {

    private static final AttributeKey<String> MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system");
    private static final AttributeKey<String> MESSAGING_DESTINATION = AttributeKey.stringKey("messaging.destination.name");
    private static final AttributeKey<String> MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation");

    /** W3C 标准 TextMap 注入器 — 将 trace context 写入 NATS Headers */
    private static final TextMapSetter<Headers> SETTER = Headers::put;

    /** W3C 标准 TextMap 提取器 — 从 NATS Headers 中提取 trace context */
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

    private final Tracer tracer;
    private final boolean enabled;

    public OpenTelemetryNatsTracingSupport(boolean enabled) {
        this(enabled, GlobalOpenTelemetry.get());
    }

    public OpenTelemetryNatsTracingSupport(boolean enabled, OpenTelemetry openTelemetry) {
        this.enabled = enabled;
        this.tracer = openTelemetry.getTracer(NatsConstants.TRACER_NAME, NatsConstants.TRACER_VERSION);
    }

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

        injectMdc(span);
        injectW3C(headers, span);
        return span;
    }

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

        injectMdc(span);
        return span;
    }

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
            span.end();
            MDC.remove(NatsConstants.MDC_TRACE_ID);
            MDC.remove(NatsConstants.MDC_SPAN_ID);
        }
    }

    // ===== 内部方法 =====

    private void injectW3C(Headers headers, Span span) {
        var otelContext = Context.current().with(span);
        try (Scope ignored = otelContext.makeCurrent()) {
            GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                    .inject(otelContext, headers, SETTER);
        }
    }

    private Context extractW3C(Headers headers) {
        return GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), headers, GETTER);
    }

    private void injectMdc(Span span) {
        var spanContext = span.getSpanContext();
        MDC.put(NatsConstants.MDC_TRACE_ID, spanContext.getTraceId());
        MDC.put(NatsConstants.MDC_SPAN_ID, spanContext.getSpanId());
    }
}
