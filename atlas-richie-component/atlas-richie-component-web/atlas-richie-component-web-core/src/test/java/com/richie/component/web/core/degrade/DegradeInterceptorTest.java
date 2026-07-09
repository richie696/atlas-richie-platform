/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.degrade;

import com.richie.component.web.core.config.degrade.DegradeProperties;

import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import com.richie.contract.model.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DegradeInterceptor} 行为测试（4 个触发场景 + 兜底 + 跳过）。
 */
class DegradeInterceptorTest {

    private DefaultDegradeStrategyRegistry registry;
    private DegradeProperties properties;
    private DegradeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        registry = new DefaultDegradeStrategyRegistry();
        properties = new DegradeProperties();
        interceptor = new DegradeInterceptor(registry, properties, 100L);
    }

    @Test
    void exceptionTrigger_appliesStrategy() throws Exception {
        registry.register("err-stub", strategy("err-stub", 0, Trigger.EXCEPTION,
                DegradeResult.of(503, "{\"error\":\"err\"}", "err-stub")));

        WebRequestContext ctx = newCtx();
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("boom"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(Integer.valueOf(ctx.responseStatus())).isEqualTo(503);
        assertThat(ctx.shortCircuitBody()).contains("err");
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY)).isEqualTo("err-stub");
    }

    @Test
    void customTrigger_manualFlag() throws Exception {
        registry.register("manual-stub", strategy("manual-stub", 0, Trigger.CUSTOM,
                DegradeResult.of(200, "{\"stub\":true}", "manual-stub")));

        WebRequestContext ctx = newCtx();
        ctx.setAttribute(DegradeInterceptor.ATTR_MANUAL, Boolean.TRUE);

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(Integer.valueOf(ctx.responseStatus())).isEqualTo(200);
        assertThat(ctx.shortCircuitBody()).contains("stub");
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY)).isEqualTo("manual-stub");
    }

    @Test
    void highLatencyTrigger_overThreshold() throws Exception {
        registry.register("slow-stub", strategy("slow-stub", 0, Trigger.HIGH_LATENCY,
                DegradeResult.of(504, "{\"error\":\"slow\"}", "slow-stub")));

        WebRequestContext ctx = newCtx();
        ctx.setAttribute(DegradeInterceptor.ATTR_LATENCY_MS, 5000L);

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(Integer.valueOf(ctx.responseStatus())).isEqualTo(504);
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY)).isEqualTo("slow-stub");
    }

    @Test
    void noTrigger_noShortCircuit() throws Exception {
        registry.register("err-stub", strategy("err-stub", 0, Trigger.EXCEPTION,
                DegradeResult.of(503, "x", "err-stub")));

        WebRequestContext ctx = newCtx();
        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void skipFlag_bypassesDegrade() throws Exception {
        registry.register("err-stub", strategy("err-stub", 0, Trigger.EXCEPTION,
                DegradeResult.of(503, "x", "err-stub")));

        WebRequestContext ctx = newCtx();
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("boom"));
        ctx.setAttribute(DegradeInterceptor.ATTR_SKIP, Boolean.TRUE);

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void noMatch_fallbackApplied() throws Exception {
        // 注册只响应 HIGH_LATENCY 的策略；但 ctx 只标 EXCEPTION → 走兜底
        registry.register("latency-only", strategy("latency-only", 0, Trigger.HIGH_LATENCY,
                DegradeResult.of(504, "x", "latency-only")));

        WebRequestContext ctx = newCtx();
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("boom"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(Integer.valueOf(ctx.responseStatus())).isEqualTo(properties.getFallback().getStatus());
        assertThat(ctx.shortCircuitBody()).contains("exception"); // reason=exception
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY)).isEqualTo("<fallback>");
    }

    @Test
    void orderPrecedence_firstMatchWins() throws Exception {
        registry.register("first", strategy("first", -10, Trigger.EXCEPTION,
                DegradeResult.of(501, "first", "first")));
        registry.register("second", strategy("second", 0, Trigger.EXCEPTION,
                DegradeResult.of(502, "second", "second")));

        WebRequestContext ctx = newCtx();
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("boom"));

        interceptor.intercept(ctx, noopChain());

        assertThat(Integer.valueOf(ctx.responseStatus())).isEqualTo(501);
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY)).isEqualTo("first");
    }

    @Test
    void evaluateStrategyReceivesContext() throws Exception {
        registry.register("ctx-aware", new DegradeStrategy() {
            @Override public String name() { return "ctx-aware"; }
            @Override public Set<Trigger> triggers() { return Set.of(Trigger.EXCEPTION); }
            @Override public int order() { return 0; }
            @Override public boolean matches(Trigger t) { return t == Trigger.EXCEPTION; }
            @Override public DegradeResult build(Trigger t, Map<String, Object> c) {
                Throwable ex = (Throwable) c.get("exception");
                return DegradeResult.of(503,
                        "{\"path\":\"" + c.get("path") + "\",\"msg\":\"" + ex.getMessage() + "\"}",
                        "ctx-aware");
            }
        });

        WebRequestContext ctx = newCtx();
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("xyz"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.shortCircuitBody()).contains("xyz").contains("/api/test");
    }

    @Test
    void latencyAttribute_autoBackfilled() throws Exception {
        registry.register("latency-only", strategy("latency-only", 0, Trigger.HIGH_LATENCY,
                DegradeResult.of(504, "x", "latency-only")));

        WebRequestContext ctx = newCtx();
        // 无 ATTR_LATENCY_MS，应自动回填（通过 finally 块）
        // 由于此测试执行很快（<100ms），不应触发 HIGH_LATENCY
        interceptor.intercept(ctx, noopChain());
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_LATENCY_MS)).isNotNull();
    }

    @Test
    void getOrder_returns350() {
        assertThat(interceptor.getOrder()).isEqualTo(350);
        assertThat(DegradeInterceptor.ORDER).isEqualTo(350);
    }

    // ─────────────────────── helpers ───────────────────────

    private static WebRequestContext newCtx() {
        Map<String, java.util.List<String>> headers = new HashMap<>();
        headers.put("X-Client-Id", java.util.List.of("client-1"));
        return new MutableWebRequestContext("GET", "/api/test", headers, Map.of());
    }

    private static WebInterceptorChain noopChain() {
        return new WebInterceptorChain() {
            @Override public void proceed(WebRequestContext ctx) { /* no-op */ }
            @Override public java.util.List<com.richie.component.web.core.spi.WebInterceptor> interceptors() {
                return java.util.List.of();
            }
        };
    }

    private static DegradeStrategy strategy(String name, int order, Trigger trigger, DegradeResult result) {
        return new DegradeStrategy() {
            @Override public String name() { return name; }
            @Override public Set<Trigger> triggers() { return Set.of(trigger); }
            @Override public int order() { return order; }
            @Override public boolean matches(Trigger t) { return t == trigger; }
            @Override public DegradeResult build(Trigger t, Map<String, Object> ctx) { return result; }
        };
    }

    // ─────────────────────── path 路由（routes）相关 ───────────────────────

    @Test
    void routeExactMatch_overridesGlobalFallback() throws Exception {
        properties.getRoutes().put("/api/v1/orders", route("ORDER_DEGRADED", "订单服务暂不可用"));

        WebRequestContext ctx = newCtx("/api/v1/orders");
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("boom"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.shortCircuitBody()).contains("ORDER_DEGRADED");
        assertThat(ctx.shortCircuitBody()).contains("订单服务暂不可用");
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY))
                .isEqualTo(DegradeInterceptor.STRATEGY_NAME_ROUTE);
    }

    @Test
    void routeAntPattern_matchAnySubpath() throws Exception {
        properties.getRoutes().put("/api/v1/orders/**", route("ORDER_DEGRADED", "订单服务暂不可用"));

        WebRequestContext ctx = newCtx("/api/v1/orders/123/items");
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("boom"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.shortCircuitBody()).contains("ORDER_DEGRADED");
    }

    @Test
    void routeAntPattern_doesNotMatchOtherPaths() throws Exception {
        properties.getRoutes().put("/api/v1/orders/**", route("ORDER_DEGRADED", "订单服务暂不可用"));

        WebRequestContext ctx = newCtx("/api/v1/users");
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("boom"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.shortCircuitBody()).contains(properties.getFallback().getCode());
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY))
                .isEqualTo(DegradeInterceptor.STRATEGY_NAME_FALLBACK);
    }

    @Test
    void exactMatch_winsOverAntPattern() throws Exception {
        DegradeProperties.RouteFallback exact = route("EXACT", "精确命中");
        DegradeProperties.RouteFallback wildcard = route("WILD", "通配命中");
        properties.getRoutes().put("/api/v1/orders/**", wildcard);
        properties.getRoutes().put("/api/v1/orders/list", exact);

        WebRequestContext ctx = newCtx("/api/v1/orders/list");
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("boom"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.shortCircuitBody()).contains("EXACT");
        assertThat(ctx.shortCircuitBody()).doesNotContain("WILD");
    }

    @Test
    void routeBeanFallback_invokesBeanMethodReturningApiResult() throws Exception {
        DegradeProperties.RouteFallback route = new DegradeProperties.RouteFallback();
        route.setStatus(200);
        route.setFallbackBean("paymentFallback");
        route.setFallbackMethod("degraded");
        properties.getRoutes().put("/api/v1/payments", route);

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("paymentFallback", new PaymentFallbackBean());
        interceptor = new DegradeInterceptor(registry, properties, 100L, bf);

        WebRequestContext ctx = newCtx("/api/v1/payments");
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("db down"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(Integer.valueOf(ctx.responseStatus())).isEqualTo(200);
        assertThat(ctx.shortCircuitBody()).contains("\"code\":\"PAYMENT_DEGRADED\"");
        assertThat(ctx.shortCircuitBody()).contains("/api/v1/payments");
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY))
                .isEqualTo("<route-bean:paymentFallback>");
    }

    @Test
    void routeBeanFallback_invokesBeanMethodReturningString() throws Exception {
        DegradeProperties.RouteFallback route = new DegradeProperties.RouteFallback();
        route.setStatus(503);
        route.setFallbackBean("rawFallback");
        route.setFallbackMethod("rawJson");
        properties.getRoutes().put("/api/v1/raw", route);

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        bf.addBean("rawFallback", new RawFallbackBean());
        interceptor = new DegradeInterceptor(registry, properties, 100L, bf);

        WebRequestContext ctx = newCtx("/api/v1/raw");
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("x"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.shortCircuitBody()).contains("\"raw\":true");
    }

    @Test
    void routeBeanFallback_beanMissing_fallsBackToStaticRoute() throws Exception {
        DegradeProperties.RouteFallback route = new DegradeProperties.RouteFallback();
        route.setStatus(503);
        route.setCode("STATIC_FALLBACK");
        route.setMsg("静态降级");
        route.setFallbackBean("nonexistent");
        route.setFallbackMethod("anything");
        properties.getRoutes().put("/api/v1/x", route);

        StaticListableBeanFactory bf = new StaticListableBeanFactory();
        interceptor = new DegradeInterceptor(registry, properties, 100L, bf);

        WebRequestContext ctx = newCtx("/api/v1/x");
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("x"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.shortCircuitBody()).contains("STATIC_FALLBACK");
        assertThat(ctx.shortCircuitBody()).contains("静态降级");
        assertThat((Object) ctx.attribute(DegradeInterceptor.ATTR_HIT_STRATEGY))
                .isEqualTo(DegradeInterceptor.STRATEGY_NAME_ROUTE);
    }

    @Test
    void routeBeanFallback_noBeanFactoryInjected_skipsSilently() throws Exception {
        DegradeProperties.RouteFallback route = new DegradeProperties.RouteFallback();
        route.setStatus(503);
        route.setCode("STATIC_FALLBACK");
        route.setMsg("静态降级");
        route.setFallbackBean("anything");
        route.setFallbackMethod("anything");
        properties.getRoutes().put("/api/v1/y", route);

        interceptor = new DegradeInterceptor(registry, properties, 100L);

        WebRequestContext ctx = newCtx("/api/v1/y");
        ctx.setAttribute(DegradeInterceptor.ATTR_EXCEPTION, new RuntimeException("x"));

        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.shortCircuitBody()).contains("STATIC_FALLBACK");
    }

    @Test
    void routeDoesNotApply_whenNoTriggerFires() throws Exception {
        properties.getRoutes().put("/api/v1/orders", route("ORDER_DEGRADED", "订单服务暂不可用"));

        WebRequestContext ctx = newCtx("/api/v1/orders");
        interceptor.intercept(ctx, noopChain());

        assertThat(ctx.isShortCircuited()).isFalse();
    }

    // ─────────────────────── helpers (routes) ───────────────────────

    private static WebRequestContext newCtx(String path) {
        Map<String, java.util.List<String>> headers = new HashMap<>();
        headers.put("X-Client-Id", java.util.List.of("client-1"));
        return new MutableWebRequestContext("GET", path, headers, Map.of());
    }

    private static DegradeProperties.RouteFallback route(String code, String msg) {
        DegradeProperties.RouteFallback r = new DegradeProperties.RouteFallback();
        r.setCode(code);
        r.setMsg(msg);
        return r;
    }

    public static class PaymentFallbackBean {
        public ApiResult<?> degraded(WebRequestContext ctx) {
            return ApiResult.error("PAYMENT_DEGRADED", "支付服务降级 path=" + ctx.path());
        }
    }

    public static class RawFallbackBean {
        public String rawJson() {
            return "{\"raw\":true,\"code\":\"RAW\"}";
        }
    }
}