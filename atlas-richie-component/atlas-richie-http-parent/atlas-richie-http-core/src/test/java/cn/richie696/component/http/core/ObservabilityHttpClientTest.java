/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.http.core;

import cn.richie696.component.observability.core.DependencyMetricsRecorder;
import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityHttpClientTest {

    private OpenTelemetrySdk sdk;

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
    }

    @Test
    void createsClientSpanInjectsW3cContextAndRecordsDependencyMetric() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        RecordingMetrics metrics = new RecordingMetrics();
        RecordingClient delegate = new RecordingClient();
        ObservabilityHttpClient client = (ObservabilityHttpClient) ObservabilityHttpClient.wrap(
                delegate, sdk, metrics, ObservabilityState.enabledState());

        Span parent = sdk.getTracer("test").spanBuilder("parent").startSpan();
        try (Scope ignored = parent.makeCurrent()) {
            HttpResponse response = client.get("http://orders.internal/orders/42").execute();
            assertThat(response.statusCode()).isEqualTo(200);
        } finally {
            parent.end();
        }

        assertThat(delegate.traceparent.get()).startsWith("00-");
        assertThat(metrics.requestCount.get()).isEqualTo(1);
        assertThat(metrics.operation.get()).isEqualTo("GET");
        assertThat(metrics.target.get()).isEqualTo("orders.internal");
        assertThat(exporter.getFinishedSpanItems()).anySatisfy(span -> {
            assertThat(span.getName()).isEqualTo("GET");
            assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
            assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.OK);
            assertThat(span.getAttributes().get(AttributeKey.stringKey("server.address")))
                    .isEqualTo("orders.internal");
        });
    }

    @Test
    void restoresContextForFutureCompletionAndDoesNotWrapWhenDisabled() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        RecordingClient delegate = new RecordingClient();
        HttpClient disabled = ObservabilityHttpClient.wrap(
                delegate, sdk, null, ObservabilityState.disabledState());
        assertThat(disabled).isSameAs(delegate);

        RecordingMetrics metrics = new RecordingMetrics();
        HttpClient client = ObservabilityHttpClient.wrap(
                delegate, sdk, metrics, ObservabilityState.enabledState());
        CompletableFuture<String> result = client.get("http://catalog.internal/items")
                .future(String.class);

        assertThat(result).isCompletedWithValue("ok");
        assertThat(metrics.requestCount.get()).isEqualTo(1);
        assertThat(exporter.getFinishedSpanItems()).anySatisfy(span ->
                assertThat(span.getName()).isEqualTo("GET"));
    }

    @Test
    void observesAsyncCallbacksAndSseLifecycle() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        RecordingMetrics metrics = new RecordingMetrics();
        RecordingClient delegate = new RecordingClient();
        HttpClient client = ObservabilityHttpClient.wrap(
                delegate, sdk, metrics, ObservabilityState.enabledState());

        AtomicReference<String> asyncValue = new AtomicReference<>();
        client.async(client.post("http://orders.internal/orders"), new AsyncCallback<>() {
            @Override
            public void onResponse(HttpResponse response, String data) {
                asyncValue.set(data);
            }

            @Override
            public void onFailure(java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        }, String.class);
        assertThat(asyncValue).hasValue("ok");

        AtomicReference<SseEvent> event = new AtomicReference<>();
        AtomicInteger opened = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        SseConnection connection = client.sse(
                "http://events.internal/stream",
                Map.of("Authorization", "Bearer test"),
                new SseListener() {
                    @Override
                    public void onOpen(SseConnection value) {
                        opened.incrementAndGet();
                    }

                    @Override
                    public void onEvent(SseConnection value, SseEvent valueEvent) {
                        event.set(valueEvent);
                    }

                    @Override
                    public void onClosed(SseConnection value) {
                        closed.incrementAndGet();
                    }
                });
        assertThat(connection.statusCode()).isEqualTo(200);
        assertThat(connection.headers()).isEmpty();
        assertThat(connection.isOpen()).isTrue();
        delegate.sseListener.onOpen(delegate.sseConnection);
        delegate.sseListener.onEvent(delegate.sseConnection, SseEvent.of("payload"));
        delegate.sseListener.onClosed(delegate.sseConnection);
        assertThat(opened).hasValue(1);
        assertThat(event).hasValue(SseEvent.of("payload"));
        assertThat(closed).hasValue(1);
        connection.close();
        connection.close();
        assertThat(metrics.connectionCount).hasValue(0);
    }

    private static final class RecordingMetrics implements DependencyMetricsRecorder {
        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicReference<String> operation = new AtomicReference<>();
        private final AtomicReference<String> target = new AtomicReference<>();
        private final AtomicReference<Integer> connectionCount = new AtomicReference<>(-1);

        @Override
        public void recordRequest(String dependencyType, String targetService, String operation,
                                  String status, long durationNanos) {
            requestCount.incrementAndGet();
            this.operation.set(operation);
            this.target.set(targetService);
        }

        @Override
        public void recordConnection(String dependencyType, String targetService, long activeConnections) {
            connectionCount.set((int) activeConnections);
        }
    }

    private static final class RecordingClient implements HttpClient {
        private final AtomicReference<String> traceparent = new AtomicReference<>();
        private SseListener sseListener;
        private SseConnection sseConnection;

        @Override
        public HttpRequest get(String url) {
            return new HttpRequest(url, HttpMethod.GET, null).client(this);
        }

        @Override
        public HttpRequest post(String url, Object body) {
            return new HttpRequest(url, HttpMethod.POST, body).client(this);
        }

        @Override
        public HttpRequest post(String url) {
            return post(url, null);
        }

        @Override
        public HttpRequest put(String url, Object body) {
            return new HttpRequest(url, HttpMethod.PUT, body).client(this);
        }

        @Override
        public HttpRequest delete(String url, Object body) {
            return new HttpRequest(url, HttpMethod.DELETE, body).client(this);
        }

        @Override
        public HttpRequest delete(String url) {
            return delete(url, null);
        }

        @Override
        public SseConnection sse(String url, SseListener listener) {
            return sse(url, null, listener);
        }

        @Override
        public SseConnection sse(String url, Map<String, String> headers, SseListener listener) {
            sseListener = listener;
            sseConnection = new SseConnection() {
                @Override public int statusCode() { return 200; }
                @Override public Map<String, List<String>> headers() { return Map.of(); }
                @Override public boolean isOpen() { return true; }
                @Override public void close() { }
            };
            return sseConnection;
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            traceparent.set(request.headers().get("traceparent"));
            return HttpResponse.of(200, Map.of(), "ok".getBytes());
        }

        @Override
        public <T> T execute(HttpRequest request, Class<T> type) {
            return execute(request).bodyAs(type);
        }

        @Override
        public <T> T execute(HttpRequest request, tools.jackson.core.type.TypeReference<T> typeRef) {
            return execute(request).bodyAs(typeRef);
        }

        @Override
        public <T> void async(HttpRequest request, AsyncCallback<T> callback, Class<T> type) {
            callback.onResponse(execute(request), type.cast("ok"));
        }

        @Override
        public <T> void async(HttpRequest request, AsyncCallback<T> callback,
                              tools.jackson.core.type.TypeReference<T> typeRef) {
            callback.onResponse(execute(request), null);
        }

        @Override
        public <T> CompletableFuture<T> future(HttpRequest request, Class<T> type) {
            return CompletableFuture.completedFuture(type.cast("ok"));
        }

        @Override
        public <T> CompletableFuture<T> future(HttpRequest request,
                                               tools.jackson.core.type.TypeReference<T> typeRef) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
