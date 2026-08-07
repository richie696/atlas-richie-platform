/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.gateway.filter.thirdparty.auth;

import cn.richie696.component.i18n.resolver.I18nResolver;
import cn.richie696.component.oauth.gateway.OAuthGatewayAdapter;
import cn.richie696.gateway.config.GatewayConfig;
import cn.richie696.gateway.filter.AbstractBaseFilter;
import cn.richie696.gateway.filter.FilterOrder;
import cn.richie696.gateway.service.AuditService;
import cn.richie696.gateway.utils.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway 资源访问审计过滤器。
 *
 * <p>Token 颁发、刷新、撤销和客户端管理审计由 Authorization Server 负责；
 * Gateway 仅审计资源访问结果，主体来自 Resource Server 适配器注入的可信请求头。</p>
 */
@Slf4j
@Component
@ConditionalOnBean(OAuthGatewayAdapter.class)
public class OAuth2AuditFilter extends AbstractBaseFilter {

    private final AuditService auditService;

    public OAuth2AuditFilter(GatewayConfig config, I18nResolver i18n, AuditService auditService) {
        super(config, i18n);
        this.auditService = auditService;
    }

    @Override
    public int getOrder() {
        return FilterOrder.OAUTH2_AUDIT_FILTER.getOrder();
    }

    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/api/oauth2")) {
            return chain.filter(exchange);
        }

        String clientId = exchange.getRequest().getHeaders().getFirst(OAuthGatewayAdapter.CLIENT_ID_HEADER);
        if (clientId == null || clientId.isBlank()) {
            return chain.filter(exchange);
        }

        String ip = NetworkUtils.getIP(exchange.getRequest());
        String userAgent = NetworkUtils.getUserAgent(exchange.getRequest());
        ServerHttpResponseDecorator response = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> setComplete() {
                audit(exchange, getStatusCode(), clientId, path, ip, userAgent);
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(response).build())
                .doFinally(signal -> audit(exchange, response.getStatusCode(), clientId, path, ip, userAgent));
    }

    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        return config.getOauth2() != null && config.getOauth2().isEnabled();
    }

    private void audit(ServerWebExchange exchange, HttpStatusCode status, String clientId, String path,
                       String ip, String userAgent) {
        if (status == null || exchange.getAttributes().putIfAbsent("oauth2.audit.recorded", Boolean.TRUE) != null) {
            return;
        }
        if (status.is2xxSuccessful()) {
            auditService.auditAccessGranted(clientId, path,
                    exchange.getRequest().getMethod().name(), ip, userAgent);
        } else {
            auditService.auditAccessDenied(clientId, path,
                    exchange.getRequest().getMethod().name(), ip, userAgent,
                    "downstream_status", String.valueOf(status.value()), "下游服务返回非成功状态");
        }
    }
}
