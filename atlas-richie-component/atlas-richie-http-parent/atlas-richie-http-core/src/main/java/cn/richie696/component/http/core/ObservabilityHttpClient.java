/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.http.core;

import cn.richie696.component.observability.core.DependencyMetricsRecorder;
import cn.richie696.component.observability.core.ObservabilityState;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP client facade decorator for the unified observability contract.
 *
 * <p>The provider adapters remain responsible for wire I/O. This decorator owns
 * the protocol boundary: one client span, W3C header injection, low-cardinality
 * dependency metrics, async context restoration, and SSE connection lifetime.</p>
 *
 * <p>It never creates an SDK or provider. The {@link OpenTelemetry} instance is
 * supplied by the application starter, so a provider can still be used without
 * the observability starter and retain its original behavior.</p>
 */
public final class ObservabilityHttpClient implements HttpClient {

    private static final String DEPENDENCY_TYPE = "http";
    private static final String INSTRUMENTATION_NAME = "atlas-richie-http";

    private final HttpClient delegate;
    private final OpenTelemetry openTelemetry;
    private final DependencyMetricsRecorder metrics;
    private final boolean enabled;
    private final TextMapPropagator propagator;
    private final io.opentelemetry.api.trace.Tracer tracer;
    private final AtomicLong activeSseConnections = new AtomicLong();

    private ObservabilityHttpClient(
            HttpClient delegate,
            OpenTelemetry openTelemetry,
            DependencyMetricsRecorder metrics,
            ObservabilityState state) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.openTelemetry = openTelemetry;
        this.metrics = metrics;
        this.enabled = openTelemetry != null && (state == null || state.enabled());
        this.propagator = enabled
                ? openTelemetry.getPropagators().getTextMapPropagator()
                : null;
        this.tracer = enabled ? openTelemetry.getTracer(INSTRUMENTATION_NAME, "1.0.0") : null;
    }

    /**
     * Wraps a provider only when the unified observability runtime is enabled.
     */
    public static HttpClient wrap(
            HttpClient delegate,
            OpenTelemetry openTelemetry,
            DependencyMetricsRecorder metrics,
            ObservabilityState state) {
        if (openTelemetry == null || (state != null && state.disabled())) {
            return delegate;
        }
        return new ObservabilityHttpClient(delegate, openTelemetry, metrics, state);
    }

    @Override
    public HttpRequest get(String url) {
        return bind(delegate.get(url));
    }

    @Override
    public HttpRequest post(String url, Object body) {
        return bind(delegate.post(url, body));
    }

    @Override
    public HttpRequest post(String url) {
        return bind(delegate.post(url));
    }

    @Override
    public HttpRequest put(String url, Object body) {
        return bind(delegate.put(url, body));
    }

    @Override
    public HttpRequest delete(String url, Object body) {
        return bind(delegate.delete(url, body));
    }

    @Override
    public HttpRequest delete(String url) {
        return bind(delegate.delete(url));
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        if (!enabled) {
            return delegate.execute(request);
        }
        Span span = startSpan(request);
        long started = System.nanoTime();
        try (Scope ignored = span.makeCurrent()) {
            inject(request);
            HttpResponse response = delegate.execute(request);
            finish(span, response.statusCode(), null, started, request);
            return response;
        } catch (RuntimeException | Error error) {
            finish(span, -1, error, started, request);
            throw error;
        }
    }

    @Override
    public <T> T execute(HttpRequest request, Class<T> type) {
        return execute(request).bodyAs(type);
    }

    @Override
    public <T> T execute(HttpRequest request, TypeReference<T> typeRef) {
        return execute(request).bodyAs(typeRef);
    }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, Class<T> type) {
        if (!enabled) {
            delegate.async(request, callback, type);
            return;
        }
        executeAsync(request, callback, (carrier, observingCallback) ->
                delegate.async(carrier, observingCallback, type));
    }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, TypeReference<T> typeRef) {
        if (!enabled) {
            delegate.async(request, callback, typeRef);
            return;
        }
        executeAsync(request, callback, (carrier, observingCallback) ->
                delegate.async(carrier, observingCallback, typeRef));
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, Class<T> type) {
        return futureInternal(request, (carrier) -> delegate.future(carrier, type));
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, TypeReference<T> typeRef) {
        return futureInternal(request, (carrier) -> delegate.future(carrier, typeRef));
    }

    @Override
    public SseConnection sse(String url, SseListener listener) {
        return sse(url, null, listener);
    }

    @Override
    public SseConnection sse(String url, Map<String, String> headers, SseListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (!enabled) {
            return delegate.sse(url, headers, listener);
        }

        HttpRequest request = new HttpRequest(url, HttpMethod.GET, null);
        if (headers != null) {
            request.headers(headers);
        }
        Span span = startSpan(request);
        Context spanContext = Context.current().with(span);
        long started = System.nanoTime();
        SseConnectionHandle handle = new SseConnectionHandle(span, spanContext, started, request);
        SseListener observingListener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                handle.delegate(connection);
                activeSseConnections.incrementAndGet();
                recordConnections(url);
                withContext(spanContext, () -> listener.onOpen(handle));
            }

            @Override
            public void onEvent(SseConnection connection, SseEvent event) {
                handle.delegate(connection);
                withContext(spanContext, () -> listener.onEvent(handle, event));
            }

            @Override
            public void onClosed(SseConnection connection) {
                handle.delegate(connection);
                try {
                    withContext(spanContext, () -> listener.onClosed(handle));
                } finally {
                    handle.finish(connection.statusCode(), null);
                }
            }

            @Override
            public void onFailure(SseConnection connection, Throwable cause) {
                handle.delegate(connection);
                try {
                    withContext(spanContext, () -> listener.onFailure(handle, cause));
                } finally {
                    handle.finish(connection.statusCode(), cause);
                }
            }
        };

        try (Scope ignored = spanContext.makeCurrent()) {
            inject(request);
            SseConnection connection = delegate.sse(url, request.headers(), observingListener);
            handle.delegate(connection);
            return handle;
        } catch (RuntimeException | Error error) {
            handle.finish(-1, error);
            throw error;
        }
    }

    private HttpRequest bind(HttpRequest request) {
        return request.client(this);
    }

    private <T> void executeAsync(
            HttpRequest request,
            AsyncCallback<T> callback,
            AsyncInvoker<T> invoker) {
        Span span = startSpan(request);
        Context spanContext = Context.current().with(span);
        long started = System.nanoTime();
        try (Scope ignored = spanContext.makeCurrent()) {
            inject(request);
            invoker.invoke(request, new AsyncCallback<>() {
                @Override
                public void onResponse(HttpResponse response, T value) {
                    try {
                        withContext(spanContext, () -> callback.onResponse(response, value));
                    } finally {
                        finish(span, response.statusCode(), null, started, request);
                    }
                }


                @Override
                public void onFailure(IOException exception) {
                    try {
                        withContext(spanContext, () -> callback.onFailure(exception));
                    } finally {
                        finish(span, -1, exception, started, request);
                    }
                }
            });
        } catch (RuntimeException | Error error) {
            finish(span, -1, error, started, request);
            throw error;
        }
    }

    private <T> CompletableFuture<T> futureInternal(HttpRequest request, FutureInvoker<T> invoker) {
        if (!enabled) {
            return invoker.invoke(request);
        }
        Span span = startSpan(request);
        Context spanContext = Context.current().with(span);
        long started = System.nanoTime();
        try (Scope ignored = spanContext.makeCurrent()) {
            inject(request);
            return invoker.invoke(request)
                    .whenComplete((_, error) -> withContext(spanContext, () -> finish(span, -1, error, started, request)));
        } catch (RuntimeException | Error error) {
            finish(span, -1, error, started, request);
            throw error;
        }
    }

    private Span startSpan(HttpRequest request) {
        Span span = tracer.spanBuilder(request.method().name())
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        span.setAttribute("http.request.method", request.method().name());
        host(request.url()).ifPresent(value -> span.setAttribute("server.address", value));
        return span;
    }

    private void inject(HttpRequest request) {
        propagator.inject(Context.current(), request, (carrier, key, value) -> carrier.header(key, value));
    }

    private void finish(
            Span span,
            int statusCode,
            Throwable error,
            long started,
            HttpRequest request) {
        if (error == null && statusCode >= 200 && statusCode < 400) {
            span.setStatus(StatusCode.OK);
        } else if (error != null || statusCode >= 400 || statusCode < 0) {
            span.setStatus(StatusCode.ERROR);
            if (error != null) {
                span.recordException(error);
            }
        }
        if (statusCode >= 0) {
            span.setAttribute("http.response.status_code", statusCode);
        }
        metrics(request, statusCode, error, started);
        span.end();
    }

    private void metrics(HttpRequest request, int statusCode, Throwable error, long started) {
        if (metrics == null) {
            return;
        }
        String status = error != null ? "error" : statusCode >= 0 ? Integer.toString(statusCode) : "unknown";
        metrics.recordRequest(
                DEPENDENCY_TYPE,
                host(request.url()).orElse("unknown"),
                request.method().name(),
                status,
                System.nanoTime() - started);
    }

    private void recordConnections(String url) {
        if (metrics != null) {
            metrics.recordConnection(DEPENDENCY_TYPE, host(url).orElse("unknown"), activeSseConnections.get());
        }
    }

    private static java.util.Optional<String> host(String url) {
        try {
            return java.util.Optional.ofNullable(URI.create(url).getHost());
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static void withContext(Context context, Runnable action) {
        try (Scope ignored = context.makeCurrent()) {
            action.run();
        }
    }

    @FunctionalInterface
    private interface AsyncInvoker<T> {
        void invoke(HttpRequest request, AsyncCallback<T> callback);
    }

    @FunctionalInterface
    private interface FutureInvoker<T> {
        CompletableFuture<T> invoke(HttpRequest request);
    }

    private final class SseConnectionHandle implements SseConnection {
        private final Span span;
        private final Context spanContext;
        private final long started;
        private final HttpRequest request;
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile SseConnection delegate;

        private SseConnectionHandle(Span span, Context spanContext, long started, HttpRequest request) {
            this.span = span;
            this.spanContext = spanContext;
            this.started = started;
            this.request = request;
        }

        private void delegate(SseConnection delegate) {
            this.delegate = delegate;
        }

        private void finish(int statusCode, Throwable error) {
            if (!finished.compareAndSet(false, true)) {
                return;
            }
            activeSseConnections.updateAndGet(value -> Math.max(0, value - 1));
            recordConnections(request.url());
            withContext(spanContext, () -> ObservabilityHttpClient.this.finish(
                    span, statusCode, error, started, request));
        }

        @Override
        public int statusCode() {
            SseConnection current = delegate;
            return current == null ? -1 : current.statusCode();
        }

        @Override
        public Map<String, List<String>> headers() {
            SseConnection current = delegate;
            return current == null ? Collections.emptyMap() : current.headers();
        }

        @Override
        public boolean isOpen() {
            SseConnection current = delegate;
            return current != null && current.isOpen();
        }

        @Override
        public void close() {
            SseConnection current = delegate;
            try {
                if (current != null) {
                    current.close();
                }
            } finally {
                finish(statusCode(), null);
            }
        }
    }
}
