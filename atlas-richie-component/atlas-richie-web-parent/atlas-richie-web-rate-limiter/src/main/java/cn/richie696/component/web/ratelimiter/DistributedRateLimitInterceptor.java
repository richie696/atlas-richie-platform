/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.web.ratelimiter;

import cn.richie696.component.cache.ops.LimiterOps;
import cn.richie696.component.web.core.config.ratelimit.RateLimitProperties;
import cn.richie696.component.web.core.metrics.WebMetrics;
import cn.richie696.component.web.core.spi.KeyResolver;
import cn.richie696.component.web.core.spi.WebFilterDecision;
import cn.richie696.component.web.core.spi.WebInterceptor;
import cn.richie696.component.web.core.spi.WebInterceptorChain;
import cn.richie696.component.web.core.spi.WebRequestContext;
import cn.richie696.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Redis 原子窗口限流：每个实例共享同一计数，不创建本地用户桶或后台线程。 */
@Slf4j
public final class DistributedRateLimitInterceptor implements WebInterceptor, Ordered {

    public static final int ORDER = 300;
    public static final String ATTR_DECISION = "rate_limit.decision";
    public static final String ATTR_KEY = "rate_limit.key";
    private static final AntPathMatcher ANT_MATCHER = new AntPathMatcher();

    private final LimiterOps limiter;
    private final RateLimitProperties properties;
    private final KeyResolver keyResolver;
    private final WebMetrics metrics;

    public DistributedRateLimitInterceptor(LimiterOps limiter, RateLimitProperties properties,
                                           KeyResolver keyResolver, WebMetrics metrics) {
        this.limiter = limiter;
        this.properties = properties;
        this.keyResolver = keyResolver;
        this.metrics = metrics;
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        MatchedRoute matched = matchRoute(ctx.path());
        if (matched.config == null) {
            chain.proceed(ctx);
            return;
        }
        if (properties.isRequireGatewayIdentity()
                && isBlank(ctx.header(properties.getGatewayIdentityHeader()))) {
            denyUnidentified(ctx, "gateway_identity_missing", matched.pattern);
            return;
        }
        String clientKey = keyResolver.resolve(ctx);
        if (!isValidClientKey(clientKey)) {
            denyUnidentified(ctx, "client_unidentified", matched.pattern);
            return;
        }
        String key = buildRedisKey(clientKey, matched.pattern);
        int permits = matched.config.getPermitsPerSecond();
        int windowSeconds = matched.config.getWindowSeconds() == null
                ? properties.getWindowSeconds() : matched.config.getWindowSeconds();
        if (permits < 1 || windowSeconds < 1) {
            throw new IllegalStateException("rate-limit route must define positive permits-per-second and window-seconds");
        }
        if (!limiter.tryAcquire(key, permits, windowSeconds)) {
            denyRateLimited(ctx, clientKey, matched.config, matched.pattern);
            return;
        }
        metrics.rateLimitAllow();
        ctx.setClientKey(clientKey);
        ctx.setAttribute(ATTR_KEY, clientKey);
        chain.proceed(ctx);
    }

    private MatchedRoute matchRoute(String path) {
        List<RateLimitProperties.RouteConfig> routes = properties.getRoutes();
        if (isBlank(path) || routes == null) return new MatchedRoute(null, null);
        for (RateLimitProperties.RouteConfig route : routes) {
            if (route != null && path.equals(route.getPattern())) return new MatchedRoute(route.getPattern(), route);
        }
        for (RateLimitProperties.RouteConfig route : routes) {
            String pattern = route == null ? null : route.getPattern();
            if (!isBlank(pattern) && (pattern.contains("*") || pattern.contains("?") || pattern.contains("{"))
                    && ANT_MATCHER.match(pattern, path)) return new MatchedRoute(pattern, route);
        }
        return new MatchedRoute(null, null);
    }

    private boolean isValidClientKey(String key) {
        return !isBlank(key) && key.length() <= properties.getMaxClientKeyLength();
    }

    private String buildRedisKey(String clientKey, String pattern) {
        // route pattern is configuration-controlled; retaining it avoids a hash collision sharing quotas.
        return properties.getKeyPrefix() + ':' + clientKey + ':' + pattern;
    }

    private void denyUnidentified(WebRequestContext ctx, String reason, String pattern) {
        ctx.markShortCircuit(401, "{\"error\":\"" + reason + "\"}");
        ctx.setAttribute(ATTR_DECISION, new WebFilterDecision(getClass().getSimpleName(), reason, 401, pattern));
        metrics.rateLimitReject(reason, pattern);
    }

    private void denyRateLimited(WebRequestContext ctx, String clientKey, RateLimitProperties.RouteConfig route, String pattern) {
        int status = route.getDenyStatus() == null ? properties.getDenyStatus() : route.getDenyStatus();
        String code = route.getDenyCode() == null ? properties.getDenyCode() : route.getDenyCode();
        String message = (route.getDenyMsg() == null ? properties.getDenyMsg() : route.getDenyMsg()).replace("{key}", clientKey);
        Map<String, String> headers = route.getDenyHeaders() == null ? properties.getDenyHeaders() : route.getDenyHeaders();
        if (headers == null) headers = new HashMap<>();
        ctx.markShortCircuit(status, ApiResult.error(code, message).toJson());
        headers.forEach(ctx::addResponseHeader);
        ctx.setClientKey(clientKey);
        ctx.setAttribute(ATTR_KEY, clientKey);
        ctx.setAttribute(ATTR_DECISION, WebFilterDecision.rateLimitDeny(getClass().getSimpleName(), clientKey, status));
        metrics.rateLimitReject("rate_limited", pattern);
        log.debug("DistributedRateLimitInterceptor deny: key={} pattern={} path={}", clientKey, pattern, ctx.path());
    }

    @Override public int getOrder() { return ORDER; }
    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
    private record MatchedRoute(String pattern, RateLimitProperties.RouteConfig config) { }
}
