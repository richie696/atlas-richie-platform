/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.observability.logging;

import cn.richie696.component.observability.core.CorrelationIds;
import cn.richie696.component.observability.core.ObservabilityContext;
import cn.richie696.component.observability.core.ObservabilityState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebFlux 请求的 request_id、MDC 和响应头关联过滤器。
 *
 * <p>不创建 Server Span，Server Span 由官方 OTel WebFlux instrumentation 负责。本过滤器
 * 只负责业务 request_id、日志字段和请求完成日志，并通过 Reactor context-propagation
 * 把 MDC 传播到异步线程。</p>
 */
public final class ObservabilityWebFluxFilter implements WebFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityWebFluxFilter.class);

    private final ObservabilityState state;

    public ObservabilityWebFluxFilter(ObservabilityState state) {
        this.state = state;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (state.disabled()) {
            return chain.filter(exchange);
        }

        String requestId = ObservabilityMdcFilter.safeRequestId(
                exchange.getRequest().getHeaders().getFirst(ObservabilityMdcFilter.REQUEST_ID_HEADER));
        exchange.getResponse().getHeaders().set(ObservabilityMdcFilter.REQUEST_ID_HEADER, requestId);
        long startedAt = System.nanoTime();
        AtomicBoolean completed = new AtomicBoolean();

        return Mono.defer(() -> {
            RequestScope scope = RequestScope.open(requestId, operation(exchange));
            Mono<Void> source;
            try {
                source = chain.filter(exchange)
                        .doOnSuccess(ignoredValue -> completeLog(
                                exchange, startedAt, null, completed))
                        .doOnError(error -> completeLog(
                                exchange, startedAt, error, completed))
                        .contextCapture();
            } catch (Throwable failure) {
                scope.close();
                return Mono.error(failure);
            }
            return Mono.create(sink -> {
                Disposable subscription = source.subscribe(
                        ignoredValue -> sink.success(),
                        sink::error,
                        sink::success);
                sink.onCancel(subscription);
                scope.close();
            });
        });
    }

    private static void completeLog(
            ServerWebExchange exchange,
            long startedAt,
            Throwable failure,
            AtomicBoolean completed) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        ObservabilityMdc.put("status", Integer.toString(
                exchange.getResponse().getStatusCode() == null
                        ? 200
                        : exchange.getResponse().getStatusCode().value()));
        ObservabilityMdc.put("duration_ms", Long.toString(
                Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L)));
        if (failure == null) {
            LOGGER.info("HTTP request completed");
            return;
        }
        ObservabilityMdc.put("error.type", failure.getClass().getName());
        ObservabilityMdc.put("error.stage", "webflux.filter");
        LOGGER.warn("HTTP request completed with error");
    }

    private static String operation(ServerWebExchange exchange) {
        Object pattern = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern instanceof String routePattern && !routePattern.isBlank()
                ? routePattern
                : exchange.getRequest().getPath().value();
        return exchange.getRequest().getMethod() + " " + route;
    }

    private static final class RequestScope implements AutoCloseable {

        private final io.opentelemetry.context.Scope contextScope;
        private final ObservabilityMdc.Scope mdcScope;

        private RequestScope(
                io.opentelemetry.context.Scope contextScope,
                ObservabilityMdc.Scope mdcScope) {
            this.contextScope = contextScope;
            this.mdcScope = mdcScope;
        }

        private static RequestScope open(String requestId, String operation) {
            io.opentelemetry.context.Scope contextScope =
                    ObservabilityContext.withRequestId(requestId);
            ObservabilityMdc.Scope mdcScope =
                    ObservabilityMdc.install(CorrelationIds.current());
            ObservabilityMdc.put("operation", operation);
            return new RequestScope(contextScope, mdcScope);
        }

        @Override
        public void close() {
            mdcScope.close();
            contextScope.close();
        }
    }
}
