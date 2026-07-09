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

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import com.richie.component.concurrency.algorithm.RateLimiter;
import com.richie.component.concurrency.registry.CircuitBreakerRegistry;
import com.richie.component.concurrency.registry.DefaultCircuitBreakerRegistry;
import com.richie.component.concurrency.registry.DefaultRateLimiterRegistry;
import com.richie.component.concurrency.registry.RateLimiterRegistry;
import com.richie.component.web.core.config.ratelimit.CircuitBreakerProperties;
import com.richie.component.web.core.config.ratelimit.RateLimitProperties;
import com.richie.component.web.core.spi.KeyResolver;
import com.richie.component.web.core.spi.WebFilterDecision;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-3 集成测试（README.md §5.2 "A-3 集成测试覆盖 4+4 场景"）：
 * <ol>
 *   <li><b>RateLimit #1 allow</b>：permits 充足 → 放行，downstream 被调用，key 写入 ctx attribute</li>
 *   <li><b>RateLimit #2 deny</b>：同 key 第 N+1 次请求 → 429 短路，body 含 key，attribute 含 decision</li>
 *   <li><b>RateLimit #3 不同 key 独立</b>：key A 限流耗尽不影响 key B</li>
 *   <li><b>RateLimit #4 unidentified</b>：KeyResolver 返回 null → 401 短路（不放行下游）</li>
 *   <li><b>CircuitBreaker #5 closed</b>：state=CLOSED → 放行</li>
 *   <li><b>CircuitBreaker #6 open</b>：state=OPEN → 503 短路</li>
 *   <li><b>CircuitBreaker #7 不同 key 独立</b>：key A open 不影响 key B</li>
 *   <li><b>CircuitBreaker #8 unidentified</b>：KeyResolver 返回 null → 401</li>
 * </ol>
 */
class RateLimitAndCircuitBreakerIntegrationTest {

    private RateLimiterRegistry rateLimiterRegistry;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RateLimitProperties rateLimitProperties;
    private CircuitBreakerProperties circuitBreakerProperties;

    @BeforeEach
    void setUp() {
        rateLimiterRegistry = new DefaultRateLimiterRegistry();
        circuitBreakerRegistry = new DefaultCircuitBreakerRegistry();

        rateLimitProperties = new RateLimitProperties();
        rateLimitProperties.setPermitsPerSecond(2);  // 小窗口便于触发 deny

        circuitBreakerProperties = new CircuitBreakerProperties();
        circuitBreakerProperties.setFailureRateThreshold(50);
        circuitBreakerProperties.setSlidingWindowDuration(Duration.ofSeconds(60));
        circuitBreakerProperties.setWaitDurationInOpenState(Duration.ofSeconds(30));
    }

    // ───────────────────────── RateLimit 场景 ─────────────────────────

    @Test
    void rateLimit_01_allow_acquiresPermitAndProceeds() throws Exception {
        AtomicBoolean downstreamInvoked = new AtomicBoolean(false);
        KeyResolver keyResolver = ctx -> "client-A";
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimiterRegistry, rateLimitProperties, keyResolver);

        WebRequestContext ctx = ctx("GET", "/api/v1/users");
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of((c, ch) -> {
            downstreamInvoked.set(true);
        }));

        interceptor.intercept(ctx, chain);

        assertThat(downstreamInvoked).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
        Object rateLimitKey = ctx.attribute(RateLimitInterceptor.ATTR_KEY);
        assertThat(rateLimitKey).isEqualTo("client-A");
    }

    @Test
    void rateLimit_02_deny_marks429AndRecordsDecision() throws Exception {
        AtomicBoolean downstreamInvoked = new AtomicBoolean(false);
        KeyResolver keyResolver = ctx -> "client-burst";
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimiterRegistry, rateLimitProperties, keyResolver);

        // 预热：连续 3 次拿令牌（permitsPerSecond=2，仅 2 次成功）
        for (int i = 0; i < 3; i++) {
            RateLimiter rl = rateLimiterRegistry.getOrCreate("client-burst",
                    k -> RateLimiter.ofTokensPerSecond(rateLimitProperties.getPermitsPerSecond()));
            rl.tryAcquire();
        }

        WebRequestContext ctx = ctx("GET", "/api/v1/users");
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of((c, ch) -> {
            downstreamInvoked.set(true);
        }));

        interceptor.intercept(ctx, chain);

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(429);
        assertThat(ctx.shortCircuitBody()).contains("client-burst");
        assertThat(downstreamInvoked).isFalse();
        Object decisionObj = ctx.attribute(RateLimitInterceptor.ATTR_DECISION);
        assertThat(decisionObj).isNotNull();
        WebFilterDecision decision = (WebFilterDecision) decisionObj;
        assertThat(decision.reason()).isEqualTo("rate_limit.exceeded");
        assertThat(decision.status()).isEqualTo(429);
        assertThat(decision.interceptor()).isEqualTo("RateLimitInterceptor");
    }

    @Test
    void rateLimit_03_differentKeysAreIndependent() throws Exception {
        KeyResolver keyResolver = ctx -> {
            // 同一 ctx 复用，但通过 attribute 模拟不同 key
            Object k = ctx.attribute("simulate.key");
            return k == null ? "default" : (String) k;
        };
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimiterRegistry, rateLimitProperties, keyResolver);

        // 预热 client-X 桶
        RateLimiter rlX = rateLimiterRegistry.getOrCreate("client-X",
                k -> RateLimiter.ofTokensPerSecond(rateLimitProperties.getPermitsPerSecond()));
        for (int i = 0; i < 5; i++) rlX.tryAcquire();

        // 验证 client-Y 桶独立：5 次还能拿令牌
        RateLimiter rlY = rateLimiterRegistry.getOrCreate("client-Y",
                k -> RateLimiter.ofTokensPerSecond(rateLimitProperties.getPermitsPerSecond()));
        int acquiredY = 0;
        for (int i = 0; i < 5; i++) {
            if (rlY.tryAcquire()) acquiredY++;
        }

        assertThat(acquiredY).isGreaterThan(0);  // client-Y 不受 client-X 影响
        assertThat(rateLimiterRegistry.size()).isEqualTo(2);
    }

    @Test
    void rateLimit_04_unidentifiedClient_shortCircuits401() throws Exception {
        AtomicBoolean downstreamInvoked = new AtomicBoolean(false);
        KeyResolver keyResolver = ctx -> null;  // 模拟无法识别
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimiterRegistry, rateLimitProperties, keyResolver);

        WebRequestContext ctx = ctx("GET", "/api/v1/users");
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of((c, ch) -> {
            downstreamInvoked.set(true);
        }));

        interceptor.intercept(ctx, chain);

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(401);
        assertThat(downstreamInvoked).isFalse();
    }

    @Test
    void rateLimit_05_routeMatch_usesRoutePermitsAndIsolatesBucket() throws Exception {
        // 配置 /api/v1/orders/** 用 1 permitsPerSecond（严格）；其它走全局 2
        RateLimitProperties.RouteConfig ordersRoute = new RateLimitProperties.RouteConfig();
        ordersRoute.setPattern("/api/v1/orders/**");
        ordersRoute.setPermitsPerSecond(1);
        ordersRoute.setDenyCode("ORDER_LIMITED");
        rateLimitProperties.getRoutes().add(ordersRoute);

        KeyResolver keyResolver = ctx -> "client-shared";
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimiterRegistry, rateLimitProperties, keyResolver);

        // 第 1 次访问 /orders → 拿到 token 放行
        WebRequestContext order1 = ctx("GET", "/api/v1/orders/list");
        interceptor.intercept(order1, new DefaultWebInterceptorChain(List.of()));
        assertThat(order1.isShortCircuited()).isFalse();

        // 第 2 次访问 /orders → 桶耗尽 → deny（使用 route 的 denyCode）
        WebRequestContext order2 = ctx("GET", "/api/v1/orders/create");
        interceptor.intercept(order2, new DefaultWebInterceptorChain(List.of()));
        assertThat(order2.isShortCircuited()).isTrue();
        assertThat(order2.responseStatus()).isEqualTo(429);
        assertThat(order2.shortCircuitBody()).contains("ORDER_LIMITED");

        // 同一 client 访问 /users 走全局 2 permitsPerSecond，桶独立
        WebRequestContext user1 = ctx("GET", "/api/v1/users");
        interceptor.intercept(user1, new DefaultWebInterceptorChain(List.of()));
        assertThat(user1.isShortCircuited()).isFalse();
    }

    @Test
    void rateLimit_06_routeAntPattern_matchesAnySubpath() throws Exception {
        RateLimitProperties.RouteConfig seckillRoute = new RateLimitProperties.RouteConfig();
        seckillRoute.setPattern("/api/v1/seckill/**");
        seckillRoute.setPermitsPerSecond(1);
        rateLimitProperties.getRoutes().add(seckillRoute);

        KeyResolver keyResolver = ctx -> "seckill-user";
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimiterRegistry, rateLimitProperties, keyResolver);

        // /api/v1/seckill/foo/bar 也命中 /api/v1/seckill/**
        WebRequestContext ctx1 = ctx("POST", "/api/v1/seckill/foo/bar");
        interceptor.intercept(ctx1, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx1.isShortCircuited()).isFalse();

        WebRequestContext ctx2 = ctx("POST", "/api/v1/seckill/baz");
        interceptor.intercept(ctx2, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx2.isShortCircuited()).isTrue();
    }

    @Test
    void rateLimit_07_routeFallbackToGlobalWhenNoMatch() throws Exception {
        RateLimitProperties.RouteConfig ordersRoute = new RateLimitProperties.RouteConfig();
        ordersRoute.setPattern("/api/v1/orders/**");
        ordersRoute.setPermitsPerSecond(1);
        rateLimitProperties.getRoutes().add(ordersRoute);

        KeyResolver keyResolver = ctx -> "fallback-user";
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                rateLimiterRegistry, rateLimitProperties, keyResolver);

        // /api/v1/users 不命中 /api/v1/orders/** → 走全局（per-clientKey）
        // 预热全局桶到耗尽
        for (int i = 0; i < 3; i++) {
            rateLimiterRegistry.getOrCreate("fallback-user",
                    k -> RateLimiter.ofTokensPerSecond(rateLimitProperties.getPermitsPerSecond())).tryAcquire();
        }

        WebRequestContext ctx = ctx("GET", "/api/v1/users");
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.shortCircuitBody()).contains("RATE_LIMITED");  // 全局 denyCode
    }

    // ───────────────────────── CircuitBreaker 场景 ─────────────────────────

    @Test
    void circuitBreaker_05_closedState_proceeds() throws Exception {
        AtomicBoolean downstreamInvoked = new AtomicBoolean(false);
        KeyResolver keyResolver = ctx -> "client-cb";
        CircuitBreakerInterceptor interceptor = new CircuitBreakerInterceptor(
                circuitBreakerRegistry, circuitBreakerProperties, keyResolver);

        WebRequestContext ctx = ctx("GET", "/api/v1/users");
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of((c, ch) -> {
            downstreamInvoked.set(true);
        }));

        interceptor.intercept(ctx, chain);

        assertThat(downstreamInvoked).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
        Object cbKey = ctx.attribute(CircuitBreakerInterceptor.ATTR_KEY);
        assertThat(cbKey).isEqualTo("client-cb");
        assertThat(circuitBreakerRegistry.find("client-cb")).isPresent();
    }

    @Test
    void circuitBreaker_06_openState_shortCircuits503() throws Exception {
        AtomicBoolean downstreamInvoked = new AtomicBoolean(false);
        KeyResolver keyResolver = ctx -> "client-cb-open";
        CircuitBreakerInterceptor interceptor = new CircuitBreakerInterceptor(
                circuitBreakerRegistry, circuitBreakerProperties, keyResolver);

        // 预创建并强制 open
        CircuitBreaker cb = circuitBreakerRegistry.getOrCreate("client-cb-open",
                k -> CircuitBreaker.ofDefaults());
        cb.forceOpen();
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.OPEN);

        WebRequestContext ctx = ctx("GET", "/api/v1/users");
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of((c, ch) -> {
            downstreamInvoked.set(true);
        }));

        interceptor.intercept(ctx, chain);

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(503);
        assertThat(ctx.shortCircuitBody()).contains("client-cb-open");
        assertThat(downstreamInvoked).isFalse();
        Object decisionObj = ctx.attribute(CircuitBreakerInterceptor.ATTR_DECISION);
        assertThat(decisionObj).isNotNull();
        WebFilterDecision decision = (WebFilterDecision) decisionObj;
        assertThat(decision.reason()).isEqualTo("circuit_breaker.open");
    }

    @Test
    void circuitBreaker_07_differentKeysAreIndependent() throws Exception {
        KeyResolver keyResolver = ctx -> "default";
        CircuitBreakerInterceptor interceptor = new CircuitBreakerInterceptor(
                circuitBreakerRegistry, circuitBreakerProperties, keyResolver);

        // 让 key-A 触发 open
        CircuitBreaker cbA = circuitBreakerRegistry.getOrCreate("key-A",
                k -> CircuitBreaker.ofDefaults());
        cbA.forceOpen();
        // key-B 仍然 closed
        CircuitBreaker cbB = circuitBreakerRegistry.getOrCreate("key-B",
                k -> CircuitBreaker.ofDefaults());

        assertThat(cbA.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cbB.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreakerRegistry.size()).isEqualTo(2);
    }

    @Test
    void circuitBreaker_08_unidentifiedClient_shortCircuits401() throws Exception {
        AtomicBoolean downstreamInvoked = new AtomicBoolean(false);
        KeyResolver keyResolver = ctx -> null;
        CircuitBreakerInterceptor interceptor = new CircuitBreakerInterceptor(
                circuitBreakerRegistry, circuitBreakerProperties, keyResolver);

        WebRequestContext ctx = ctx("GET", "/api/v1/users");
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of((c, ch) -> {
            downstreamInvoked.set(true);
        }));

        interceptor.intercept(ctx, chain);

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(401);
        assertThat(downstreamInvoked).isFalse();
    }

    @Test
    void circuitBreaker_09_routeMatch_usesRouteFailureRate() throws Exception {
        // 配置 /api/v1/orders/** 用更严格的阈值
        CircuitBreakerProperties.RouteConfig ordersRoute = new CircuitBreakerProperties.RouteConfig();
        ordersRoute.setPattern("/api/v1/orders/**");
        ordersRoute.setFailureRateThreshold(10);
        ordersRoute.setDenyCode("ORDER_CIRCUIT_OPEN");
        circuitBreakerProperties.getRoutes().add(ordersRoute);

        KeyResolver keyResolver = ctx -> "client-cb-route";
        CircuitBreakerInterceptor interceptor = new CircuitBreakerInterceptor(
                circuitBreakerRegistry, circuitBreakerProperties, keyResolver);

        WebRequestContext ctx = ctx("GET", "/api/v1/orders/list");
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of((c, ch) -> {}));
        interceptor.intercept(ctx, chain);

        assertThat(ctx.isShortCircuited()).isFalse();
        // CB key = matchedPattern（不是 clientKey）
        assertThat(circuitBreakerRegistry.find("/api/v1/orders/**")).isPresent();
        assertThat(circuitBreakerRegistry.find("client-cb-route")).isEmpty();
    }

    @Test
    void circuitBreaker_10_routeMatch_openDenyUsesRouteDenyCode() throws Exception {
        CircuitBreakerProperties.RouteConfig ordersRoute = new CircuitBreakerProperties.RouteConfig();
        ordersRoute.setPattern("/api/v1/orders/**");
        ordersRoute.setFailureRateThreshold(10);
        ordersRoute.setDenyCode("ORDER_CIRCUIT_OPEN");
        circuitBreakerProperties.getRoutes().add(ordersRoute);

        KeyResolver keyResolver = ctx -> "client-cb-deny";
        CircuitBreakerInterceptor interceptor = new CircuitBreakerInterceptor(
                circuitBreakerRegistry, circuitBreakerProperties, keyResolver);

        // 预创建该 pattern 的 CB 并强制 open
        CircuitBreaker cb = circuitBreakerRegistry.getOrCreate("/api/v1/orders/**",
                k -> CircuitBreaker.ofDefaults());
        cb.forceOpen();

        WebRequestContext ctx = ctx("GET", "/api/v1/orders/list");
        WebInterceptorChain chain = new DefaultWebInterceptorChain(List.of((c, ch) -> {}));
        interceptor.intercept(ctx, chain);

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(503);
        assertThat(ctx.shortCircuitBody()).contains("ORDER_CIRCUIT_OPEN");
        assertThat(ctx.shortCircuitBody()).contains("client-cb-deny");
    }

    @Test
    void circuitBreaker_11_routeAndGlobalAreIndependent() throws Exception {
        CircuitBreakerProperties.RouteConfig ordersRoute = new CircuitBreakerProperties.RouteConfig();
        ordersRoute.setPattern("/api/v1/orders/**");
        ordersRoute.setFailureRateThreshold(10);
        circuitBreakerProperties.getRoutes().add(ordersRoute);

        KeyResolver keyResolver = ctx -> "shared-client";
        CircuitBreakerInterceptor interceptor = new CircuitBreakerInterceptor(
                circuitBreakerRegistry, circuitBreakerProperties, keyResolver);

        WebRequestContext orderCtx = ctx("GET", "/api/v1/orders/list");
        interceptor.intercept(orderCtx, new DefaultWebInterceptorChain(List.of()));
        assertThat(circuitBreakerRegistry.find("/api/v1/orders/**")).isPresent();

        WebRequestContext userCtx = ctx("GET", "/api/v1/users");
        interceptor.intercept(userCtx, new DefaultWebInterceptorChain(List.of()));
        // 未命中 routes → cb key = clientKey（向后兼容）
        assertThat(circuitBreakerRegistry.find("shared-client")).isPresent();
    }

    // ───────────────────────── helper ─────────────────────────

    private static WebRequestContext ctx(String method, String path) {
        return MutableWebRequestContext.builder()
                .method(method)
                .path(path)
                .build();
    }
}