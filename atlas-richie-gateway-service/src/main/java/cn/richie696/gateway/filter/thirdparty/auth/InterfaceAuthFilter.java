/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.gateway.filter.thirdparty.auth;

import cn.richie696.component.i18n.resolver.I18nResolver;
import cn.richie696.component.oauth.core.ScopeResolver;
import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import cn.richie696.component.oauth.gateway.OAuthGatewayAdapter;
import cn.richie696.contract.constant.GlobalConstants;
import cn.richie696.contract.gateway.model.OAuth2Constants;
import cn.richie696.gateway.config.GatewayConfig;
import cn.richie696.gateway.filter.AbstractBaseFilter;
import cn.richie696.gateway.filter.FilterOrder;
import cn.richie696.gateway.service.AuditService;
import cn.richie696.gateway.utils.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gateway Resource Server 过滤器。
 *
 * <p>令牌签发、客户端注册、TokenStore 和客户端权威配置均不在 Gateway 中实现，
 * 认证统一委托给 {@link OAuthGatewayAdapter}。Gateway 只负责边缘策略、scope 路由
 * 授权和可信主体头传播。</p>
 */
@Slf4j
@Component
@ConditionalOnBean(OAuthGatewayAdapter.class)
public class InterfaceAuthFilter extends AbstractBaseFilter {

    private final OAuthGatewayAdapter oauthGatewayAdapter;
    private final AuditService auditService;
    private final ScopeResolver scopeResolver;

    public InterfaceAuthFilter(GatewayConfig config,
                               I18nResolver i18n,
                               OAuthGatewayAdapter oauthGatewayAdapter,
                               AuditService auditService,
                               ScopeResolver scopeResolver) {
        super(config, i18n);
        this.oauthGatewayAdapter = oauthGatewayAdapter;
        this.auditService = auditService;
        this.scopeResolver = scopeResolver;
    }

    @Override
    public int getOrder() {
        return FilterOrder.INTERFACE_AUTH_FILTER.getOrder();
    }

    @Override
    protected Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        String path = request.getURI().getPath();

        if (path.startsWith(OAuth2Constants.OAUTH2_BASE)) {
            return chain.filter(exchange);
        }

        String clientIp = NetworkUtils.getIP(request);
        String userAgent = NetworkUtils.getUserAgent(request);
        String method = request.getMethod().name();
        String authorization = request.getHeaders().getFirst(OAuth2Constants.HEADER_AUTHORIZATION);

        if (StringUtils.isBlank(authorization)) {
            return accessDenied(response, null, path, method, clientIp, userAgent,
                    "token_missing", OAuth2Constants.ERROR_INVALID_TOKEN, "访问令牌缺失");
        }

        final OAuthPrincipal principal;
        try {
            principal = oauthGatewayAdapter.authenticate(authorization);
        } catch (RuntimeException e) {
            log.debug("Resource Server 校验失败: path={}", path, e);
            return accessDenied(response, null, path, method, clientIp, userAgent,
                    "token_invalid", OAuth2Constants.ERROR_INVALID_TOKEN, "访问令牌无效或已过期");
        }

        String clientId = StringUtils.defaultIfBlank(principal.clientId(), principal.subject());
        if (!verifyScopePermission(principal, path, method, clientId)) {
            return accessDenied(response, clientId, path, method, clientIp, userAgent,
                    "insufficient_scope", OAuth2Constants.ERROR_INVALID_SCOPE, "权限不足");
        }

        ServerWebExchange propagated = oauthGatewayAdapter.propagate(exchange, principal);
        ServerHttpRequest mutatedRequest = propagated.getRequest().mutate()
                .header(OAuth2Constants.HEADER_X_THIRD_PARTY_CLIENT_ID, clientId)
                .header(GlobalConstants.X_RD_REQUEST_FLAG,
                        Base64.getEncoder().encodeToString(clientIp.getBytes(StandardCharsets.UTF_8)))
                .build();

        return chain.filter(propagated.mutate().request(mutatedRequest).build());
    }

    @Override
    protected boolean enableVerifyFilter(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return !path.startsWith(OAuth2Constants.OAUTH2_BASE)
                && config.getOauth2() != null && config.getOauth2().isEnabled();
    }

    private boolean verifyScopePermission(OAuthPrincipal principal, String path, String method, String clientId) {
        List<String> requiredScopes = scopeResolver.getRequiredScopes(path, method);
        if (requiredScopes == null || requiredScopes.isEmpty()) {
            return true;
        }
        Set<String> tokenScopes = new HashSet<>(principal.scopes());
        boolean allowed = scopeResolver.verifyScope(tokenScopes, requiredScopes);
        if (!allowed) {
            log.warn("Scope 权限验证失败: clientId={}, path={}, method={}, tokenScopes={}, requiredScopes={}",
                    clientId, path, method, tokenScopes, requiredScopes);
        }
        return allowed;
    }

    private Mono<Void> accessDenied(ServerHttpResponse response, String clientId, String path, String method,
                                    String ip, String userAgent, String reason, String error, String description) {
        recordAccessDenied(clientId, path, method, ip, userAgent, reason, error, description);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        String docs = config.getOauth2() == null ? null : config.getOauth2().getErrorDocsBaseUri();
        String errorJson = StringUtils.isBlank(docs)
                ? String.format("{\"error\":\"%s\",\"error_description\":\"%s\"}", error, description)
                : String.format("{\"error\":\"%s\",\"error_description\":\"%s\",\"error_uri\":\"%s%s\"}", error, description, docs, error);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorJson.getBytes(StandardCharsets.UTF_8))));
    }

    private void recordAccessDenied(String clientId, String path, String method, String ip, String userAgent,
                                    String reason, String errorCode, String errorMsg) {
        try {
            auditService.auditAccessDenied(StringUtils.defaultIfBlank(clientId, "unknown"), path, method,
                    ip, userAgent, reason, errorCode, errorMsg);
        } catch (Exception e) {
            log.warn("记录审计日志失败", e);
        }
    }

}
