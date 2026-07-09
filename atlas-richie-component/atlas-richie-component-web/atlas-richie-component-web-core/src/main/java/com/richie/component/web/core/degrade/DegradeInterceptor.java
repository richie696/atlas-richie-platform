/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.web.core.degrade;

import com.richie.component.web.core.config.degrade.DegradeProperties;
import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.Ordered;
import org.springframework.util.AntPathMatcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 降级拦截器（README.md §4.7）。
 * <p>
 * 拦截器链上的最后一个"异常观察者"：先让下游拦截器 / 业务执行，再根据结果决定是否降级。
 * <p>
 * <strong>职责边界</strong>：
 * <ul>
 *   <li>本拦截器<strong>不捕获异常</strong>——让异常继续传播到
 *       {@link com.richie.component.web.core.exception.GlobalExceptionControllerAdvice}
 *       或 servlet 容器的错误链。本拦截器在调用链<strong>之后</strong>用 attribute 读取已记录的异常状态。</li>
 *   <li>如需在拦截器<strong>内部</strong>捕获并降级，业务方可注册自定义 {@link WebInterceptor} 排在
 *       本拦截器之前，且自行 try/catch + 写入降级结果 attribute</li>
 * </ul>
 *
 * <h2>判定流程</h2>
 * <ol>
 *   <li>读 attribute {@code degrade.skip}：若为 true，跳过（业务方显式豁免）</li>
 *   <li>读 attribute {@code degrade.exception}：若非空，{@link Trigger#EXCEPTION}</li>
 *   <li>读 attribute {@code degrade.manual}：若为 true，{@link Trigger#CUSTOM}</li>
 *   <li>读 attribute {@code degrade.latencyMs}：若超阈值，{@link Trigger#HIGH_LATENCY}</li>
 *   <li>查 {@link DegradeStrategyRegistry#select(Trigger)}；命中即写短路响应</li>
 *   <li>未命中 → 按 path 匹配 {@link DegradeProperties#getRoutes()}（精确 → Ant 通配）；
 *       命中即用该 {@link DegradeProperties.RouteFallback} 生成响应</li>
 *   <li>仍未命中 → 兜底响应（全局 fallback）</li>
 * </ol>
 *
 * <h2>顺序</h2>
 * <p>{@link #getOrder()} 返回 {@code 350}（位于 RateLimit / CircuitBreaker 之后，
 * HangDetection 之前），README.md §3.4 拦截器链实际顺序。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class DegradeInterceptor implements WebInterceptor, Ordered {

    /**
     * 拦截器顺序：350（RateLimit=300, CircuitBreaker=400, Degrade=350, Hang=200）。
     */
    public static final int ORDER = 350;

    /**
     * 业务写入：跳过本次降级（{@code "true"}）。
     */
    public static final String ATTR_SKIP = "degrade.skip";

    /**
     * 业务写入：触发 {@link Trigger#EXCEPTION}（Throwable）。
     */
    public static final String ATTR_EXCEPTION = "degrade.exception";

    /**
     * 业务写入：触发 {@link Trigger#CUSTOM}（{@code "true"}）。
     */
    public static final String ATTR_MANUAL = "degrade.manual";

    /**
     * 业务写入：触发 {@link Trigger#HIGH_LATENCY}（Long 毫秒）；超阈值时降级。
     */
    public static final String ATTR_LATENCY_MS = "degrade.latencyMs";

    /**
     * 高延迟阈值（毫秒），超此值视为 {@link Trigger#HIGH_LATENCY}。默认 3000。
     */
    public static final long DEFAULT_LATENCY_THRESHOLD_MS = 3000L;

    /**
     * 命中策略 attribute key。
     */
    public static final String ATTR_HIT_STRATEGY = "degrade.hit_strategy";

    /** 全局 fallback 命中时的策略名标记（{@code ATTR_HIT_STRATEGY} 值）。 */
    public static final String STRATEGY_NAME_FALLBACK = "<fallback>";

    /** path 路由命中但无 Bean 引用时的策略名标记。 */
    public static final String STRATEGY_NAME_ROUTE = "<route>";

    /** path 路由命中且带 Bean 引用时的策略名标记前缀。 */
    public static final String STRATEGY_NAME_ROUTE_BEAN_PREFIX = "<route-bean:";

    private static final AntPathMatcher ANT_MATCHER = new AntPathMatcher();

    private final DegradeStrategyRegistry registry;
    private final DegradeProperties properties;
    private final long latencyThresholdMs;
    /** 可空：仅在配置了 routes.{path}.fallbackBean 时需要；为 null 表示无 Bean 降级能力。注入 {@link BeanFactory}
     *  而非 {@code ApplicationContext}，降低测试桩构造代价。 */
    private final BeanFactory beanFactory;
    /** Bean 反射方法缓存：key = beanName + "#" + methodName → Method。 */
    private final Map<String, Method> beanMethodCache = new ConcurrentHashMap<>();

    public DegradeInterceptor(DegradeStrategyRegistry registry,
                              DegradeProperties properties) {
        this(registry, properties, DEFAULT_LATENCY_THRESHOLD_MS, null);
    }

    public DegradeInterceptor(DegradeStrategyRegistry registry,
                              DegradeProperties properties,
                              long latencyThresholdMs) {
        this(registry, properties, latencyThresholdMs, null);
    }

    public DegradeInterceptor(DegradeStrategyRegistry registry,
                              DegradeProperties properties,
                              long latencyThresholdMs,
                              BeanFactory beanFactory) {
        this.registry = registry;
        this.properties = properties;
        this.latencyThresholdMs = latencyThresholdMs;
        this.beanFactory = beanFactory;
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        Long startNanos = System.nanoTime();
        try {
            chain.proceed(ctx);
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
            // 如果下游未写入 latency 属性（业务未自测），回填以支持 HIGH_LATENCY 判定
            if (ctx.attribute(ATTR_LATENCY_MS) == null) {
                ctx.setAttribute(ATTR_LATENCY_MS, latencyMs);
            }
            evaluate(ctx);
        }
    }

    /**
     * 执行降级判定与短路（包公开仅供测试）。
     */
    void evaluate(WebRequestContext ctx) {
        if (Boolean.TRUE.equals(ctx.attribute(ATTR_SKIP))) {
            return;
        }

        Trigger trigger = detectTrigger(ctx);
        if (trigger == null) {
            return;
        }

        java.util.Optional<DegradeStrategy> hit = registry.select(trigger);
        DegradeResult result;
        if (hit.isPresent()) {
            result = hit.get().build(trigger, ctxSnapshot(ctx, trigger));
            ctx.setAttribute(ATTR_HIT_STRATEGY, hit.get().name());
        } else {
            result = routeOrFallbackResult(ctx, trigger);
        }
        apply(ctx, result);
        log.debug("DegradeInterceptor triggered: trigger={} strategy={} status={}",
                trigger, ctx.attribute(ATTR_HIT_STRATEGY), result.status());
    }

    private Trigger detectTrigger(WebRequestContext ctx) {
        Object manual = ctx.attribute(ATTR_MANUAL);
        if (Boolean.TRUE.equals(manual)) {
            return Trigger.CUSTOM;
        }
        Object ex = ctx.attribute(ATTR_EXCEPTION);
        if (ex instanceof Throwable) {
            return Trigger.EXCEPTION;
        }
        Object lat = ctx.attribute(ATTR_LATENCY_MS);
        if (lat instanceof Number) {
            long ms = ((Number) lat).longValue();
            if (ms > latencyThresholdMs) {
                return Trigger.HIGH_LATENCY;
            }
        }
        return null;
    }

    private Map<String, Object> ctxSnapshot(WebRequestContext ctx, Trigger trigger) {
        Map<String, Object> snap = new HashMap<>();
        snap.put("path", ctx.path());
        Object ex = ctx.attribute(ATTR_EXCEPTION);
        if (ex instanceof Throwable) {
            snap.put("exception", ex);
            snap.put("exceptionType", ex.getClass().getName());
            if (((Throwable) ex).getMessage() != null) {
                snap.put("exceptionMessage", ((Throwable) ex).getMessage());
            }
        }
        Object lat = ctx.attribute(ATTR_LATENCY_MS);
        if (lat instanceof Number) {
            snap.put("latencyMs", ((Number) lat).longValue());
        }
        snap.put("trigger", trigger);
        return snap;
    }

    /**
     * 按 path 匹配 routes，未命中走全局 fallback。
     * <p>命中顺序：精确 → Ant 通配；同一优先级按 {@link #properties#getRoutes()} 迭代顺序。
     */
    private DegradeResult routeOrFallbackResult(WebRequestContext ctx, Trigger trigger) {
        DegradeProperties.RouteFallback route = matchRoute(ctx.path());
        if (route != null) {
            // 优先 Bean 动态降级
            if (route.getFallbackBean() != null && !route.getFallbackBean().isBlank()) {
                DegradeResult beanResult = invokeBeanFallback(ctx, trigger, route);
                if (beanResult != null) {
                    ctx.setAttribute(ATTR_HIT_STRATEGY,
                            STRATEGY_NAME_ROUTE_BEAN_PREFIX + route.getFallbackBean() + ">");
                    return beanResult;
                }
                // Bean 调用失败 → 回退到 RouteFallback 的静态配置
                log.warn("DegradeInterceptor: bean fallback failed, fallback to static route config: bean={} method={}",
                        route.getFallbackBean(), route.getFallbackMethod());
            }
            ctx.setAttribute(ATTR_HIT_STRATEGY, STRATEGY_NAME_ROUTE);
            return buildResult(route.getStatus(), route.getCode(), route.getMsg(),
                    trigger, route.getHeaders(), STRATEGY_NAME_ROUTE);
        }
        // 全局 fallback
        ctx.setAttribute(ATTR_HIT_STRATEGY, STRATEGY_NAME_FALLBACK);
        DegradeProperties.Fallback fb = properties.getFallback();
        return buildResult(fb.getStatus(), fb.getCode(), fb.getMsg(),
                trigger, fb.getHeaders(), STRATEGY_NAME_FALLBACK);
    }

    /**
     * 匹配 path 对应的 {@link DegradeProperties.RouteFallback}。
     * <p>优先级：精确匹配（map.get）→ Ant 通配（按迭代顺序首命中）。
     */
    DegradeProperties.RouteFallback matchRoute(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Map<String, DegradeProperties.RouteFallback> routes = properties.getRoutes();
        if (routes == null || routes.isEmpty()) {
            return null;
        }
        // 精确匹配优先
        DegradeProperties.RouteFallback exact = routes.get(path);
        if (exact != null) {
            return exact;
        }
        // Ant 通配：按 map 迭代顺序首命中
        for (Map.Entry<String, DegradeProperties.RouteFallback> e : routes.entrySet()) {
            String pattern = e.getKey();
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            if (pattern.contains("*") || pattern.contains("?") || pattern.contains("{")) {
                if (ANT_MATCHER.match(pattern, path)) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 反射调用 Bean 动态降级方法。返回 null 表示调用失败（由调用方决定是否回退静态配置）。
     */
    private DegradeResult invokeBeanFallback(WebRequestContext ctx,
                                             Trigger trigger,
                                             DegradeProperties.RouteFallback route) {
        if (beanFactory == null) {
            log.warn("DegradeInterceptor: bean fallback configured but BeanFactory is null: bean={}",
                    route.getFallbackBean());
            return null;
        }
        String beanName = route.getFallbackBean();
        String methodName = route.getFallbackMethod();
        if (methodName == null || methodName.isBlank()) {
            log.warn("DegradeInterceptor: bean fallback missing method: bean={}", beanName);
            return null;
        }
        Object bean;
        try {
            bean = beanFactory.getBean(beanName);
        } catch (Exception ex) {
            log.warn("DegradeInterceptor: bean not found: bean={} cause={}", beanName, ex.toString());
            return null;
        }
        Method method = beanMethodCache.computeIfAbsent(beanName + "#" + methodName,
                key -> resolveFallbackMethod(bean.getClass(), methodName));
        if (method == null) {
            log.warn("DegradeInterceptor: no usable fallback method: bean={} method={}", beanName, methodName);
            return null;
        }
        try {
            Object returnValue = (method.getParameterCount() == 1)
                    ? method.invoke(bean, ctx)
                    : method.invoke(bean);
            return toDegradeResult(ctx, trigger, route, beanName, returnValue);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            log.warn("DegradeInterceptor: bean fallback invocation failed: bean={} method={} cause={}",
                    beanName, methodName, ex.toString());
            return null;
        }
    }

    /**
     * 解析 Bean 上的降级方法。规则：
     * <ol>
     *   <li>首选 {@code public Object methodName(WebRequestContext)}（单参）</li>
     *   <li>次选 {@code public Object methodName()}（无参）</li>
     *   <li>若同 bean 多次配置不同方法名，独立缓存</li>
     * </ol>
     */
    private static Method resolveFallbackMethod(Class<?> beanClass, String methodName) {
        Method singleArg = null;
        Method noArg = null;
        for (Method m : beanClass.getMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            if (m.getParameterCount() == 1
                    && WebRequestContext.class.isAssignableFrom(m.getParameterTypes()[0])) {
                singleArg = m;
                break;
            }
            if (m.getParameterCount() == 0 && noArg == null) {
                noArg = m;
            }
        }
        return singleArg != null ? singleArg : noArg;
    }

    /**
     * Bean 方法返回值转 {@link DegradeResult}：
     * <ul>
     *   <li>{@link ApiResult}：序列化为 body，状态码取 {@link DegradeProperties.RouteFallback#getStatus()}</li>
     *   <li>{@link String}：作为 body，状态码取 yml 配置</li>
     *   <li>其它：返回 null（调用方决定回退策略）</li>
     * </ul>
     */
    private static DegradeResult toDegradeResult(WebRequestContext ctx,
                                                  Trigger trigger,
                                                  DegradeProperties.RouteFallback route,
                                                  String beanName,
                                                  Object returnValue) {
        String body;
        if (returnValue instanceof ApiResult<?> api) {
            body = api.toJson();
        } else if (returnValue instanceof String s) {
            body = s;
        } else {
            log.warn("DegradeInterceptor: bean fallback return type unsupported: bean={} type={}",
                    beanName, returnValue == null ? "null" : returnValue.getClass().getName());
            return null;
        }
        return DegradeResult.of(route.getStatus(), body,
                route.getHeaders() == null ? new HashMap<>() : route.getHeaders(),
                STRATEGY_NAME_ROUTE_BEAN_PREFIX + beanName + ">");
    }

    /**
     * 用占位符替换 + ApiResult.error 构造降级结果（静态配置路径）。
     */
    private static DegradeResult buildResult(int status,
                                             String code,
                                             String msgTemplate,
                                             Trigger trigger,
                                             Map<String, String> headers,
                                             String strategyName) {
        String reason = trigger.name().toLowerCase().replace('_', '-');
        String msg = msgTemplate.replace("{reason}", reason);
        ApiResult<Void> api = ApiResult.error(code, msg);
        return DegradeResult.of(status, api.toJson(), headers, strategyName);
    }

    private void apply(WebRequestContext ctx, DegradeResult result) {
        result.headers().forEach(ctx::addResponseHeader);
        ctx.markShortCircuit(result.status(), result.body());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}