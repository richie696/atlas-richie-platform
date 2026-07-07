package com.richie.component.web.core.interceptor;

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.registry.CircuitBreakerRegistry;
import com.richie.component.web.core.config.ratelimit.CircuitBreakerProperties;
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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 熔断拦截器（README.md §4.2）。
 * <p>
 * <strong>职责边界（关键非琐碎细节）</strong>：
 * <p>
 * 本拦截器<strong>仅做"快速失败"</strong>——即检查 {@link CircuitBreaker#state()}，
 * 若 {@code OPEN} 则立即 503 短路；否则放行。
 * <p>
 * 业务失败计数由业务层显式调用 {@code cb.execute(callable)} 更新——拦截器模式无法自动包 controller
 * 调用（{@link WebInterceptorChain#proceed(WebRequestContext)} 返回 void）。这是有意为之：
 * <ul>
 *   <li>优点：拦截器零侵入，熔断状态对业务透明</li>
 *   <li>代价：业务层需要主动接入 {@code CircuitBreaker}——详见 §4.2 备注</li>
 * </ul>
 *
 * <h2>按接口粒度配置</h2>
 * <p>熔断按"被保护资源"分：CB key = {@code matchedPattern}（未命中 routes 时为 {@link #GLOBAL_PATTERN}），
 * 与 {@link RateLimitInterceptor} 的 {@code clientKey + "::" + pattern} 复合 key 解耦。同一 path 的
 * 所有 clientKey 共享同一 CB 状态机——这符合经典 CB 语义（保护被调用资源，而非限制调用方）。命中 path
 * 路由后，使用该路由专属的阈值创建独立 CB。
 *
 * <h2>拦截器顺序</h2>
 * <p>{@link #getOrder()} 返回 {@code 400}（晚于 RateLimit），见 §4 SPI 注释。
 *
 * <h2>HookBus 衔接</h2>
 * <p>同 {@link RateLimitInterceptor}：本拦截器只写 attribute，A-4 HookBus 读取。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class CircuitBreakerInterceptor implements WebInterceptor, Ordered {

    /**
     * 默认拦截器顺序：400（晚于 RateLimit，早于 HangDetection）。
     */
    public static final int ORDER = 400;

    /**
     * 拒绝决策 attribute key。
     */
    public static final String ATTR_DECISION = "circuit_breaker.decision";

    /**
     * 熔断 key attribute key。
     */
    public static final String ATTR_KEY = "circuit_breaker.key";

    /** 未命中 routes 时的默认 pattern 标记。 */
    static final String GLOBAL_PATTERN = "__global__";

    private static final AntPathMatcher ANT_MATCHER = new AntPathMatcher();

    private final CircuitBreakerRegistry registry;
    private final CircuitBreakerProperties properties;
    private final KeyResolver keyResolver;
    private final WebMetrics metrics;

    public CircuitBreakerInterceptor(CircuitBreakerRegistry registry,
                                     CircuitBreakerProperties properties,
                                     KeyResolver keyResolver) {
        this(registry, properties, keyResolver, WebMetrics.noop());
    }

    public CircuitBreakerInterceptor(CircuitBreakerRegistry registry,
                                     CircuitBreakerProperties properties,
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
            return;
        }

        MatchedRoute matched = matchRoute(ctx.path());
        String cbKey = (matched.routeConfig == null) ? clientKey : matched.pattern;
        CircuitBreaker cb = registry.getOrCreate(cbKey, k -> buildBreaker(matched.routeConfig));
        metrics.registerGauge(WebMetrics.CB_STATE, cb,
                c -> (double) ((CircuitBreaker) c).state().ordinal(),
                "key", cbKey);
        CircuitBreaker.State state = cb.state();

        if (state == CircuitBreaker.State.OPEN) {
            metrics.cbNotPermitted(matched.pattern);
            applyDeny(ctx, clientKey, matched.routeConfig);
            log.debug("CircuitBreakerInterceptor deny (open): key={} pattern={} path={}",
                    clientKey, matched.pattern, ctx.path());
            return;
        }

        ctx.setAttribute(ATTR_KEY, clientKey);
        chain.proceed(ctx);
    }

    /**
     * 匹配 path 对应的 route 配置。
     * <p>优先级：精确匹配 → Ant 通配（按 List 顺序）→ 未匹配（{@code routeConfig=null, pattern=GLOBAL_PATTERN}）。
     */
    MatchedRoute matchRoute(String path) {
        if (path == null || path.isBlank()) {
            return new MatchedRoute(GLOBAL_PATTERN, null);
        }
        List<CircuitBreakerProperties.RouteConfig> routes = properties.getRoutes();
        if (routes == null || routes.isEmpty()) {
            return new MatchedRoute(GLOBAL_PATTERN, null);
        }
        for (CircuitBreakerProperties.RouteConfig rc : routes) {
            String pattern = rc.getPattern();
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (pattern.equals(path)) {
                return new MatchedRoute(pattern, rc);
            }
        }
        for (CircuitBreakerProperties.RouteConfig rc : routes) {
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

    private CircuitBreaker buildBreaker(CircuitBreakerProperties.RouteConfig routeConfig) {
        int failureRate = (routeConfig != null)
                ? routeConfig.getFailureRateThreshold()
                : properties.getFailureRateThreshold();
        Duration window = (routeConfig != null && routeConfig.getSlidingWindowDuration() != null)
                ? routeConfig.getSlidingWindowDuration()
                : properties.getSlidingWindowDuration();
        Duration waitInOpen = (routeConfig != null && routeConfig.getWaitDurationInOpenState() != null)
                ? routeConfig.getWaitDurationInOpenState()
                : properties.getWaitDurationInOpenState();
        return CircuitBreaker.ofRate(failureRate, window, waitInOpen);
    }

    private void applyDeny(WebRequestContext ctx, String clientKey, CircuitBreakerProperties.RouteConfig routeConfig) {
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

        String msg = msgTemplate.replace("{key}", clientKey);
        ApiResult<Void> api = ApiResult.error(code, msg);
        ctx.markShortCircuit(status, api.toJson());
        headers.forEach(ctx::addResponseHeader);
        ctx.setAttribute(ATTR_KEY, clientKey);
        ctx.setAttribute(ATTR_DECISION, WebFilterDecision.circuitBreakerDeny(
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
    record MatchedRoute(String pattern, CircuitBreakerProperties.RouteConfig routeConfig) {}
}