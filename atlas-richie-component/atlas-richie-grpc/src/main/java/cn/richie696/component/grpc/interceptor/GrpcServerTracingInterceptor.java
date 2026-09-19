/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.grpc.interceptor;

import cn.richie696.component.observability.core.DependencyMetricsRecorder;
import cn.richie696.component.observability.core.ObservabilityContext;
import cn.richie696.component.observability.core.ObservabilityState;
import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * gRPC server boundary instrumentation with W3C extraction and complete call lifecycle.
 */
public final class GrpcServerTracingInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> REQUEST_ID_HEADER =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    private static final TextMapGetter<Metadata> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Metadata carrier) {
            return carrier.keys();
        }

        @Override
        public String get(Metadata carrier, String key) {
            return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        }
    };

    private final Tracer tracer;
    private final TextMapPropagator propagator;
    private final DependencyMetricsRecorder metrics;
    private final boolean enabled;

    public GrpcServerTracingInterceptor() {
        this(GlobalOpenTelemetry.get(), null, null);
    }

    public GrpcServerTracingInterceptor(OpenTelemetry openTelemetry) {
        this(openTelemetry, null, null);
    }

    public GrpcServerTracingInterceptor(
            OpenTelemetry openTelemetry,
            DependencyMetricsRecorder metrics,
            ObservabilityState state) {
        this.tracer = openTelemetry.getTracer("atlas-richie-grpc", "1.0.0");
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.metrics = metrics;
        this.enabled = state == null || state.enabled();
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        if (!enabled) {
            return next.startCall(call, headers);
        }

        String method = call.getMethodDescriptor().getFullMethodName();
        String requestId = headers.get(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        Context extracted = propagator.extract(Context.current(), headers, GETTER);
        Span span = tracer.spanBuilder(method)
                .setParent(extracted)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("rpc.system", "grpc")
                .setAttribute("rpc.method", method)
                .setAttribute("request_id", requestId)
                .startSpan();
        Context spanContext = ObservabilityContext.withRequestId(extracted.with(span), requestId);
        SpanLifecycle lifecycle = new SpanLifecycle(span, spanContext, method, requestId);

        ServerCall<ReqT, RespT> tracedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                lifecycle.status.set(status);
                super.close(status, trailers);
            }
        };

        ServerCall.Listener<ReqT> listener;
        try (Scope ignored = spanContext.makeCurrent()) {
            putMdc(lifecycle);
            try {
                listener = next.startCall(tracedCall, headers);
            } finally {
                clearMdc();
            }
        }

        ServerCall.Listener<ReqT> delegate = listener;
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                withContext(lifecycle, () -> super.onMessage(message));
            }

            @Override
            public void onHalfClose() {
                try {
                    withContext(lifecycle, super::onHalfClose);
                } catch (RuntimeException | Error error) {
                    lifecycle.span.recordException(error);
                    lifecycle.span.setStatus(StatusCode.ERROR, error.getMessage());
                    throw error;
                }
            }

            @Override
            public void onComplete() {
                try {
                    withContext(lifecycle, super::onComplete);
                } finally {
                    lifecycle.finish(lifecycle.status.get());
                }
            }

            @Override
            public void onCancel() {
                try {
                    withContext(lifecycle, super::onCancel);
                } finally {
                    lifecycle.finish(Status.CANCELLED);
                }
            }

            @Override
            public void onReady() {
                withContext(lifecycle, super::onReady);
            }
        };
    }

    private static void withContext(SpanLifecycle lifecycle, Runnable action) {
        try (Scope ignored = lifecycle.context.makeCurrent()) {
            putMdc(lifecycle);
            try {
                action.run();
            } finally {
                clearMdc();
            }
        }
    }

    private static void putMdc(SpanLifecycle lifecycle) {
        MDC.put("trace_id", lifecycle.span.getSpanContext().getTraceId());
        MDC.put("span_id", lifecycle.span.getSpanContext().getSpanId());
        MDC.put("request_id", lifecycle.requestId);
    }

    private static void clearMdc() {
        MDC.remove("trace_id");
        MDC.remove("span_id");
        MDC.remove("request_id");
    }

    private final class SpanLifecycle {
        private final Span span;
        private final Context context;
        private final String method;
        private final String requestId;
        private final long started = System.nanoTime();
        private final AtomicReference<Status> status = new AtomicReference<>(Status.OK);
        private final AtomicBoolean finished = new AtomicBoolean();

        private SpanLifecycle(Span span, Context context, String method, String requestId) {
            this.span = span;
            this.context = context;
            this.method = method;
            this.requestId = requestId;
        }

        private void finish(Status finalStatus) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            try (Scope ignored = context.makeCurrent()) {
                span.setAttribute("rpc.grpc.status_code", (long) finalStatus.getCode().value());
                if (finalStatus.isOk()) {
                    span.setStatus(StatusCode.OK);
                } else {
                    span.setStatus(StatusCode.ERROR, finalStatus.getDescription());
                }
                if (metrics != null) {
                    metrics.recordRequest("grpc", "grpc.server", method,
                            finalStatus.getCode().name(), System.nanoTime() - started);
                }
                span.end();
            } finally {
                clearMdc();
            }
        }
    }
}
