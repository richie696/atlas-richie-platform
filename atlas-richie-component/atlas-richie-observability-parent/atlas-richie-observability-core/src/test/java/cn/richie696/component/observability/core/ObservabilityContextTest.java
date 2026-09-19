package cn.richie696.component.observability.core;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityContextTest {

    @Test
    void requestIdScopeIsRestoredAfterClose() {
        assertThat(ObservabilityContext.currentRequestId()).isNull();
        try (var ignored = ObservabilityContext.withRequestId("request-a")) {
            assertThat(ObservabilityContext.currentRequestId()).isEqualTo("request-a");
            try (var nested = ObservabilityContext.withRequestId("request-b")) {
                assertThat(ObservabilityContext.currentRequestId()).isEqualTo("request-b");
            }
            assertThat(ObservabilityContext.currentRequestId()).isEqualTo("request-a");
        }
        assertThat(ObservabilityContext.currentRequestId()).isNull();
    }

    @Test
    void traceAndRequestIdsRemainIndependent() {
        SpanContext spanContext = SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        Span span = Span.wrap(spanContext);
        try (var ignored = Context.current().with(span).makeCurrent();
             var requestScope = ObservabilityContext.withRequestId("business-request")) {
            CorrelationIds ids = CorrelationIds.current();
            assertThat(ids.traceId()).isEqualTo(spanContext.getTraceId());
            assertThat(ids.spanId()).isEqualTo(spanContext.getSpanId());
            assertThat(ids.requestId()).isEqualTo("business-request");
        }
    }

    @Test
    void nullContextAndBlankRequestIdKeepTheCurrentContext() {
        Context base = ObservabilityContext.withRequestId(Context.current(), "base");
        assertThat(ObservabilityContext.withRequestId(base, " ")).isSameAs(base);
        try (var ignored = ObservabilityContext.makeCurrent(null)) {
            assertThat(ObservabilityContext.current()).isNotNull();
        }
    }

    @Test
    void invalidSpanProducesEmptyTraceAndSpanIds() {
        CorrelationIds ids = CorrelationIds.from(null, null);
        assertThat(ids.traceId()).isEmpty();
        assertThat(ids.spanId()).isEmpty();
        assertThat(ids.requestId()).isEmpty();
    }
}
