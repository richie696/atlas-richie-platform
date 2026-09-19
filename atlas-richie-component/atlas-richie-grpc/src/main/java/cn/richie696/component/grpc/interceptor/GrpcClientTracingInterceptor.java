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
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.MDC;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * gRPC client boundary instrumentation.
 *
 * <p>Creates one CLIENT span per call, injects W3C context, restores context
 * around callbacks, and closes the span automatically from the call lifecycle.</p>
 */
public final class GrpcClientTracingInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> REQUEST_ID_HEADER =
            Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER);

    private final Tracer tracer;
    private final TextMapPropagator propagator;
    private final DependencyMetricsRecorder metrics;
    private final boolean enabled;
    private final ConcurrentMap<ClientCall<?, ?>, SpanState> activeSpans = new ConcurrentHashMap<>();

    public GrpcClientTracingInterceptor() {
        this(GlobalOpenTelemetry.get(), null, null);
    }

    public GrpcClientTracingInterceptor(OpenTelemetry openTelemetry) {
        this(openTelemetry, null, null);
    }

    public GrpcClientTracingInterceptor(
            OpenTelemetry openTelemetry,
            DependencyMetricsRecorder metrics,
            ObservabilityState state) {
        this.tracer = openTelemetry.getTracer("atlas-richie-grpc", "1.0.0");
        this.propagator = openTelemetry.getPropagators().getTextMapPropagator();
        this.metrics = metrics;
        this.enabled = state == null || state.enabled();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        if (!enabled) {
            return next.newCall(method, callOptions);
        }

        String methodName = method.getFullMethodName();
        String target = next.authority() == null ? "unknown" : next.authority();
        String requestId = ObservabilityContext.currentRequestId();
        Span span = tracer.spanBuilder(methodName)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "grpc")
                .setAttribute("rpc.method", methodName)
                .startSpan();
        if (requestId != null) {
            span.setAttribute("request_id", requestId);
        }
        Context spanContext = Context.current().with(span);
        long started = System.nanoTime();

        ClientCall<ReqT, RespT> delegate;
        try (Scope ignored = spanContext.makeCurrent()) {
            delegate = next.newCall(method, callOptions);
        }

        ClientCall<ReqT, RespT> wrapped = new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                try (Scope ignored = spanContext.makeCurrent()) {
                    propagator.inject(spanContext, headers, (metadata, key, value) ->
                            metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value));
                    if (requestId != null && headers.get(REQUEST_ID_HEADER) == null) {
                        headers.put(REQUEST_ID_HEADER, requestId);
                    }
                    SpanState state = new SpanState(span, spanContext, started, methodName, target, requestId);
                    activeSpans.put(this, state);
                    Listener<RespT> listener = new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                        @Override
                        public void onClose(Status status, Metadata trailers) {
                            try {
                                withContext(state, () -> super.onClose(status, trailers));
                            } finally {
                                finish(thisCall(), status);
                            }
                        }
                    };
                    super.start(listener, headers);
                } catch (RuntimeException | Error error) {
                    finish(this, Status.fromThrowable(error));
                    throw error;
                }
            }

            private ClientCall<ReqT, RespT> thisCall() {
                return this;
            }
        };
        return wrapped;
    }

    /** Compatibility hook for callers of the former manual lifecycle API. */
    public <ReqT, RespT> void finishSpan(ClientCall<ReqT, RespT> call, Status status) {
        finish(call, status);
    }

    private void finish(ClientCall<?, ?> call, Status status) {
        SpanState state = activeSpans.remove(call);
        if (state == null || !state.finished.compareAndSet(false, true)) {
            return;
        }
        withContext(state, () -> {
            state.span.setAttribute("rpc.grpc.status_code", (long) status.getCode().value());
            if (status.isOk()) {
                state.span.setStatus(StatusCode.OK);
            } else {
                state.span.setStatus(StatusCode.ERROR, status.getDescription());
            }
            if (metrics != null) {
                metrics.recordRequest("grpc", state.target, state.method, status.getCode().name(),
                        System.nanoTime() - state.started);
            }
            state.span.end();
        });
    }

    private static void withContext(SpanState state, Runnable action) {
        try (Scope ignored = state.context.makeCurrent()) {
            MDC.put("trace_id", state.span.getSpanContext().getTraceId());
            MDC.put("span_id", state.span.getSpanContext().getSpanId());
            if (state.requestId != null) {
                MDC.put("request_id", state.requestId);
            }
            try {
                action.run();
            } finally {
                MDC.remove("trace_id");
                MDC.remove("span_id");
                MDC.remove("request_id");
            }
        }
    }

    private static final class SpanState {
        private final Span span;
        private final Context context;
        private final long started;
        private final String method;
        private final String target;
        private final String requestId;
        private final AtomicBoolean finished = new AtomicBoolean();

        private SpanState(Span span, Context context, long started, String method, String target, String requestId) {
            this.span = span;
            this.context = context;
            this.started = started;
            this.method = method;
            this.target = target;
            this.requestId = requestId;
        }
    }
}
