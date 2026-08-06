/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.gateway.filter.common.infrastructure;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/** Assigns and propagates an ID for both routed requests and gateway-generated 404s. */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, WebFilter, Ordered {
    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_KEY = "requestId";

    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
    @Override public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return apply(exchange, chain::filter);
    }
    @Override public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return apply(exchange, chain::filter);
    }
    private Mono<Void> apply(ServerWebExchange exchange, Function<ServerWebExchange, Mono<Void>> next) {
        String requestId = exchange.getAttribute(ATTRIBUTE_KEY);
        if (requestId == null || requestId.isBlank()) {
            requestId = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        }
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString().replace("-", "");
        final String resolvedRequestId = requestId;
        exchange.getAttributes().put(ATTRIBUTE_KEY, resolvedRequestId);
        ServerWebExchange mutated = exchange.mutate().request(exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_NAME, resolvedRequestId)).build()).build();
        mutated.getResponse().getHeaders().set(HEADER_NAME, resolvedRequestId);
        return next.apply(mutated);
    }
}
