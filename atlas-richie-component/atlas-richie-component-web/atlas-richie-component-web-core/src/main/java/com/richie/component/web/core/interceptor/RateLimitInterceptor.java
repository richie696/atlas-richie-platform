/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.interceptor;

import com.richie.component.concurrency.algorithm.RateLimiter;
import com.richie.component.concurrency.registry.RateLimiterRegistry;
import com.richie.component.web.core.config.ratelimit.RateLimitProperties;
import com.richie.component.web.core.metrics.WebMetrics;
import com.richie.component.web.core.spi.KeyResolver;
import com.richie.component.web.core.spi.WebFilterDecision;
import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 限流拦截器（README.md §4.1）。
 * <p>
 * 工作流：
 * <ol>
 *   <li>{@link KeyResolver} 解析 clientKey（null → 401 短路）</li>
 *   <li>按 path 匹配 {@link RateLimitProperties#getRoutes()}：
 *       <ul>
 *         <li>命中：使用路由专属 {@code permitsPerSecond} / deny 配置，registry key = {@code clientKey + "::" + pattern}</li>
 *         <li>未命中：使用全局配置，registry key = {@code clientKey + "::__global__"}</li>
 *       </ul></li>
 *   <li>{@link RateLimiterRegistry} 按 registry key 获取/创建独立 {@link RateLimiter}</li>
 *   <li>{@link RateLimiter#tryAcquire()} 非阻塞取令牌；false → 短路响应</li>
 *   <li>放行 → ctx.attribute("rate_limit.key", key) 供下游使用；WebFilterDecision attribute 供 A-4 HookBus</li>
 * </ol>
 *
 * <h2>拦截器顺序</h2>
 * <p>{@link #getOrder()} 返回 {@code 300}（README.md §4 SPI 注释中的第 3 位），
 * 晚于 OtelSpan / RequestLifecycleHook，早于 CircuitBreaker / HangDetection。
 *
 * <h2>HookBus 衔接</h2>
 * <p>本拦截器不直接发布 HookBus 事件——A-4 阶段实现 HookBus 后，由 {@code RequestLifecycleHookInterceptor}
 * 从 {@link WebRequestContext#attribute(String)} 读取 {@code "rate_limit.decision"} 并 publish。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class RateLimitInterceptor implements WebInterceptor, Ordered {

    /**
     * 默认拦截器顺序：300（晚于 Otel/Hook，早于 CB/HangDetection）。
     */
    public static final int ORDER = 300;

    /**
     * 拒绝决策 attribute key（A-4 HookBus 读取用）。
     */
    public static final String ATTR_DECISION = "rate_limit.decision";

    /**
     * 限流 key attribute key（供下游业务查询）。
     */
    public static final String ATTR_KEY = "rate_limit.key";

    /** 未命中 routes 时的默认 pattern 标记。 */
    static final String GLOBAL_PATTERN = "__global__";

    private static final AntPathMatcher ANT_MATCHER = new AntPathMatcher();

    private final RateLimiterRegistry registry;
    private final RateLimitProperties properties;
    private final KeyResolver keyResolver;
    private final WebMetrics metrics;

    public RateLimitInterceptor(RateLimiterRegistry registry,
                                RateLimitProperties properties,
                                KeyResolver keyResolver) {
        this(registry, properties, keyResolver, WebMetrics.noop());
    }

    public RateLimitInterceptor(RateLimiterRegistry registry,
                                RateLimitProperties properties,
                                KeyResolver keyResolver,
                                WebMetrics metrics) {
        this.registry = registry;
        this.properties = properties;
        this.keyResolver = keyResolver;
        this.metrics = metrics;
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        String clientKey = keyResolver.resolve(ctx);
        if (clientKey == null) {
            ctx.markShortCircuit(401, "{\"error\":\"client_unidentified\"}");
            ctx.setAttribute(ATTR_DECISION, new WebFilterDecision(
                    getClass().getSimpleName(), "unidentified", 401, "client_unidentified"));
            metrics.rateLimitReject("client_unidentified", GLOBAL_PATTERN);
            log.debug("RateLimitInterceptor deny: client unidentified path={}", ctx.path());
            return;
        }

        MatchedRoute matched = matchRoute(ctx.path());
        // 未命中 routes 时用纯 clientKey（向后兼容：现有测试/部署预热的 RateLimiter 仍能命中）；
        // 命中 routes 时用 clientKey + pattern 复合 key（按 path 隔离桶）。
        String registryKey = (matched.routeConfig == null)
                ? clientKey
                : clientKey + "::" + matched.pattern;
        int permits = matched.routeConfig != null
                ? matched.routeConfig.getPermitsPerSecond()
                : properties.getPermitsPerSecond();

        RateLimiter limiter = registry.getOrCreate(registryKey,
                k -> RateLimiter.ofTokensPerSecond(permits));

        if (!limiter.tryAcquire()) {
            applyDeny(ctx, clientKey, matched.routeConfig, matched.pattern);
            log.debug("RateLimitInterceptor deny: key={} pattern={} path={}",
                    clientKey, matched.pattern, ctx.path());
            return;
        }

        metrics.rateLimitAllow();
        ctx.setAttribute(ATTR_KEY, clientKey);
        chain.proceed(ctx);
    }

    /**
     * 匹配 path 对应的 route 配置。
     * <p>优先级：精确匹配 → Ant 通配（按 List 顺序，先到先得）→ 未匹配（返回 {@link MatchedRoute}
     * 含 {@code routeConfig=null, pattern=GLOBAL_PATTERN}）。
     */
    MatchedRoute matchRoute(String path) {
        if (path == null || path.isBlank()) {
            return new MatchedRoute(GLOBAL_PATTERN, null);
        }
        List<RateLimitProperties.RouteConfig> routes = properties.getRoutes();
        if (routes == null || routes.isEmpty()) {
            return new MatchedRoute(GLOBAL_PATTERN, null);
        }
        for (RateLimitProperties.RouteConfig rc : routes) {
            String pattern = rc.getPattern();
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (pattern.equals(path)) {
                return new MatchedRoute(pattern, rc);
            }
        }
        for (RateLimitProperties.RouteConfig rc : routes) {
            String pattern = rc.getPattern();
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (pattern.contains("*") || pattern.contains("?") || pattern.contains("{")) {
                if (ANT_MATCHER.match(pattern, path)) {
                    return new MatchedRoute(pattern, rc);
                }
            }
        }
        return new MatchedRoute(GLOBAL_PATTERN, null);
    }

    private void applyDeny(WebRequestContext ctx, String clientKey,
                           RateLimitProperties.RouteConfig routeConfig, String pattern) {
        int status = (routeConfig != null && routeConfig.getDenyStatus() != null)
                ? routeConfig.getDenyStatus() : properties.getDenyStatus();
        String code = (routeConfig != null && routeConfig.getDenyCode() != null)
                ? routeConfig.getDenyCode() : properties.getDenyCode();
        String msgTemplate = (routeConfig != null && routeConfig.getDenyMsg() != null)
                ? routeConfig.getDenyMsg() : properties.getDenyMsg();
        Map<String, String> headers = (routeConfig != null && routeConfig.getDenyHeaders() != null)
                ? routeConfig.getDenyHeaders() : properties.getDenyHeaders();
        if (headers == null) {
            headers = new HashMap<>();
        }

        metrics.rateLimitReject("rate_limited", pattern);
        String msg = msgTemplate.replace("{key}", clientKey);
        ApiResult<Void> api = ApiResult.error(code, msg);
        ctx.markShortCircuit(status, api.toJson());
        headers.forEach(ctx::addResponseHeader);
        ctx.setAttribute(ATTR_KEY, clientKey);
        ctx.setAttribute(ATTR_DECISION, WebFilterDecision.rateLimitDeny(
                getClass().getSimpleName(), clientKey, status));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * route 匹配结果：{@code pattern} 始终非 null（未匹配时为 {@link #GLOBAL_PATTERN}）；
     * {@code routeConfig} 在未匹配或 routes 为空时为 null。
     */
    record MatchedRoute(String pattern, RateLimitProperties.RouteConfig routeConfig) {}
}