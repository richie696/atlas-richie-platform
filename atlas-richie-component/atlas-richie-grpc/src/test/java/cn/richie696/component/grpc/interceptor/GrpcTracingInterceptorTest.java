/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.grpc.interceptor;

import cn.richie696.component.observability.core.DependencyMetricsRecorder;
import cn.richie696.component.observability.core.ObservabilityContext;
import cn.richie696.component.observability.core.ObservabilityState;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrpcTracingInterceptorTest {

    private OpenTelemetrySdk sdk;

    @AfterEach
    void tearDown() {
        if (sdk != null) {
            sdk.close();
        }
    }

    @Test
    void clientInjectsW3cAndRequestIdAndClosesOneClientSpan() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = sdk(exporter);
        RecordingMetrics metrics = new RecordingMetrics();
        RecordingClientCall<String, String> rawCall = new RecordingClientCall<>();
        Channel channel = new Channel() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
                return (ClientCall<ReqT, RespT>) rawCall;
            }

            @Override
            public String authority() {
                return "orders-grpc:8080";
            }
        };
        GrpcClientTracingInterceptor interceptor = new GrpcClientTracingInterceptor(
                sdk, metrics, ObservabilityState.enabledState());

        Span parent = sdk.getTracer("test").spanBuilder("parent").startSpan();
        try (Scope ignored = parent.makeCurrent(); Scope request = ObservabilityContext.withRequestId("req-grpc")) {
            ClientCall<String, String> call = interceptor.interceptCall(method(), CallOptions.DEFAULT, channel);
            call.start(new ClientCall.Listener<>() { }, new Metadata());
            rawCall.close(Status.OK, new Metadata());
        } finally {
            parent.end();
        }

        Metadata headers = rawCall.headers.get();
        assertThat(headers.get(Metadata.Key.of("traceparent", Metadata.ASCII_STRING_MARSHALLER)))
                .startsWith("00-");
        assertThat(headers.get(Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("req-grpc");
        assertThat(metrics.requests.get()).isEqualTo(1);
        var clientSpan = exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getKind() == SpanKind.CLIENT)
                .findFirst()
                .orElseThrow();
        assertThat(clientSpan.getName()).isEqualTo("orders.OrderService/Get");
        assertThat(clientSpan.getSpanContext().getTraceId())
                .isEqualTo(parent.getSpanContext().getTraceId());
    }

    @Test
    void serverExtractsW3cAndClosesOneServerSpanWithoutLegacyTraceHeader() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        sdk = sdk(exporter);
        RecordingMetrics metrics = new RecordingMetrics();
        ServerCall<String, String> call = mock(ServerCall.class);
        when(call.getMethodDescriptor()).thenReturn(method());
        AtomicReference<ServerCall.Listener<String>> listenerRef = new AtomicReference<>();
        ServerCallHandler<String, String> next = (ignoredCall, ignoredHeaders) -> {
            ServerCall.Listener<String> listener = new ServerCall.Listener<>() { };
            listenerRef.set(listener);
            return listener;
        };

        Span parent = sdk.getTracer("test").spanBuilder("parent").startSpan();
        Metadata headers = new Metadata();
        sdk.getPropagators().getTextMapPropagator().inject(
                parent.storeInContext(io.opentelemetry.context.Context.current()),
                headers,
                (metadata, key, value) -> metadata.put(
                        Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value));
        headers.put(Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER), "req-server");
        GrpcServerTracingInterceptor interceptor = new GrpcServerTracingInterceptor(
                sdk, metrics, ObservabilityState.enabledState());
        ServerCall.Listener<String> tracedListener = interceptor.interceptCall(call, headers, next);
        tracedListener.onComplete();
        parent.end();

        assertThat(listenerRef.get()).isNotNull();
        assertThat(metrics.requests.get()).isEqualTo(1);
        var serverSpan = exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getKind() == SpanKind.SERVER)
                .findFirst()
                .orElseThrow();
        assertThat(serverSpan.getName()).isEqualTo("orders.OrderService/Get");
        assertThat(serverSpan.getSpanContext().getTraceId())
                .isEqualTo(parent.getSpanContext().getTraceId());
    }

    private static OpenTelemetrySdk sdk(InMemorySpanExporter exporter) {
        return OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
    }

    private static MethodDescriptor<String, String> method() {
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public java.io.InputStream stream(String value) {
                return new java.io.ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(java.io.InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("orders.OrderService/Get")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private static final class RecordingClientCall<ReqT, RespT> extends ClientCall<ReqT, RespT> {
        private final AtomicReference<Metadata> headers = new AtomicReference<>();
        private final AtomicReference<Listener<RespT>> listener = new AtomicReference<>();

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            this.listener.set(responseListener);
            this.headers.set(headers);
        }

        private void close(Status status, Metadata trailers) {
            listener.get().onClose(status, trailers);
        }

        @Override public void request(int count) { }
        @Override public void cancel(String message, Throwable cause) { }
        @Override public void halfClose() { }
        @Override public void sendMessage(ReqT message) { }
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
