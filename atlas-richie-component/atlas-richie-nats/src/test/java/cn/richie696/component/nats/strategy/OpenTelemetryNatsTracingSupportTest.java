/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.nats.strategy;

import cn.richie696.component.nats.NatsConstants;
import cn.richie696.component.observability.core.DependencyMetricsRecorder;
import cn.richie696.component.observability.core.ObservabilityContext;
import cn.richie696.component.observability.core.ObservabilityState;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryNatsTracingSupportTest {

    private OpenTelemetrySdk sdk;

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
    }

    @Test
    void producerAndConsumerPropagateW3cAndRequestIdAndRecordMetrics() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = sdk(exporter);
        RecordingMetrics metrics = new RecordingMetrics();
        OpenTelemetryNatsTracingSupport support = new OpenTelemetryNatsTracingSupport(
                true, sdk, metrics, ObservabilityState.enabledState());

        Span parent = sdk.getTracer("test").spanBuilder("parent").startSpan();
        Headers headers = new Headers();
        try (Scope parentScope = parent.makeCurrent();
             Scope requestScope = ObservabilityContext.withRequestId("nats-request")) {
            Span producer = support.startProducerSpan("orders.created", headers);
            support.finishSpan(producer, true, null);
        }

        assertThat(headers.get("traceparent")).isNotNull().isNotEmpty();
        assertThat(headers.get(NatsConstants.HEADER_REQUEST_ID)).containsExactly("nats-request");

        Span consumer = support.startConsumerSpan("orders.created", headers);
        try (Scope ignored = support.contextFor(consumer, headers).makeCurrent()) {
            assertThat(ObservabilityContext.currentRequestId()).isEqualTo("nats-request");
        }
        support.finishSpan(consumer, true, null);
        parent.end();

        var spans = exporter.getFinishedSpanItems();
        assertThat(spans).filteredOn(span -> span.getKind() == SpanKind.PRODUCER).singleElement();
        assertThat(spans).filteredOn(span -> span.getKind() == SpanKind.CONSUMER).singleElement();
        assertThat(metrics.requests.get()).isEqualTo(2);
    }

    @Test
    void disabledStateKeepsHeadersAndExporterUntouched() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = sdk(exporter);
        OpenTelemetryNatsTracingSupport support = new OpenTelemetryNatsTracingSupport(
                true, sdk, new RecordingMetrics(), ObservabilityState.disabledState());
        Headers headers = new Headers();

        Span span = support.startClientSpan("orders.request", headers);
        support.finishSpan(span, false, "disabled");

        assertThat(span.getSpanContext().isValid()).isFalse();
        assertThat(headers.get("traceparent")).isNullOrEmpty();
        assertThat(headers.get(NatsConstants.HEADER_REQUEST_ID)).isNullOrEmpty();
        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    private OpenTelemetrySdk sdk(InMemorySpanExporter exporter) {
        return OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
    }

    private static final class RecordingMetrics implements DependencyMetricsRecorder {
        private final AtomicInteger requests = new AtomicInteger();

        @Override
        public void recordRequest(String dependencyType, String targetService, String operation,
                                  String status, long durationNanos) {
            requests.incrementAndGet();
        }

        @Override
        public void recordConnection(String dependencyType, String targetService, long activeConnections) {
        }
    }
}
