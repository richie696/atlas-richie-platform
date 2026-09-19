/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.core;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * 当前观测上下文中的三类关联 ID。
 *
 * <p>trace_id 和 span_id 只能来自 OTel SpanContext；request_id 是独立的业务关联 ID。
 * 没有有效 Span 时返回空字符串，不伪造 trace_id 或 span_id。</p>
 */
public record CorrelationIds(String traceId, String spanId, String requestId) {

    public CorrelationIds {
        traceId = normalize(traceId);
        spanId = normalize(spanId);
        requestId = normalize(requestId);
    }

    public static CorrelationIds current() {
        return ObservabilityContext.currentCorrelation();
    }

    static CorrelationIds from(Span span, String requestId) {
        SpanContext spanContext = span == null ? SpanContext.getInvalid() : span.getSpanContext();
        if (spanContext == null || !spanContext.isValid()) {
            return new CorrelationIds("", "", requestId);
        }
        return new CorrelationIds(spanContext.getTraceId(), spanContext.getSpanId(), requestId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
