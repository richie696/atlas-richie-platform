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
package com.richie.component.web.core.metrics;

import com.richie.component.web.core.sse.SseManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;

/**
 * Web 端统一指标埋点门面（README.md §4.1 / §4.2 / §4.4 / §4.8）。
 * <p>
 * 业务方启用 {@code spring-boot-starter-actuator} 后，Spring 自动装配 {@link MeterRegistry}，
 * 本类所有 record* 方法自动埋点；未启用时 {@code registry=null}，所有方法空操作（<strong>零侵入</strong>）。
 *
 * <h2>命名约定</h2>
 * <ul>
 *   <li>{@code web.rate_limit.allow} / {@code web.rate_limit.reject} —— §4.1</li>
 *   <li>{@code web.cb.calls} / {@code web.cb.not_permitted} / {@code web.cb.state} —— §4.2</li>
 *   <li>{@code web.hang.detections} —— §4.4</li>
 *   <li>{@code web.sse.connections} / {@code web.sse.send} / {@code web.sse.disconnect} —— §4.4 SSE 长连接</li>
 * </ul>
 *
 * <h2>CB 指标分工（A 方案 — 零侵入 + 业务可介入）</h2>
 * <ul>
 *   <li>{@link #cbNotPermitted(String)} —— 由 {@code CircuitBreakerInterceptor} OPEN 短路时自动埋</li>
 *   <li>{@link #registerGauge(String, Object, ToDoubleFunction, String...)} —— 由 {@code CircuitBreakerInterceptor}
 *       首次创建 cb 时自动注册 gauge（值由 {@code CircuitBreaker#state()} ordinal 实时计算，
 *       调用方 lambda 内做类型 cast；本类签名不依赖 {@code CircuitBreaker} 类）</li>
 *   <li>{@link #cbCall(String, String)} —— 业务方手动调：业务代码用 {@code cb.execute(callable)}
 *       包 controller 逻辑后，在 finally 块调 {@code metrics.cbCall("success"/"failure", pattern)}。
 *       这是 Java 生态共识（Resilience4j / Sentinel / Hystrix 都要求业务显式包一层；拦截器模式
 *       {@code chain.proceed(ctx)} 返回 void，物理上包不住 controller）。</li>
 * </ul>
 *
 * <h2>类路径解耦（关键设计）</h2>
 * <p>本类方法签名<strong>不</strong>引用任何外部可选依赖（如 {@code CircuitBreaker}），
 * 即便调用方所在的拦截器/组件因 {@code @ConditionalOnClass} 跳过装配，本类也可独立加载——
 * 配合 {@code atlas-richie-component-concurrency} 的 {@code <optional>true</optional>} 声明，
 * 实现"未引入 concurrency 时 web-core 仍可启动 + RateLimit/CB 拦截器自动跳过"。
 *
 * <h2>线程安全</h2>
 * <p>所有方法线程安全：{@link Counter#increment()} 内部使用 {@code LongAdder}，
 * {@link Gauge} 每次 scrape 时实时读 source 对象的 state；本类内 {@link ConcurrentHashMap} 去重。
 *
 * @author richie696
 * @since 2026-07
 */
public final class WebMetrics {

    public static final String RATE_LIMIT_ALLOW = "web.rate_limit.allow";
    public static final String RATE_LIMIT_REJECT = "web.rate_limit.reject";
    public static final String CB_CALLS = "web.cb.calls";
    public static final String CB_NOT_PERMITTED = "web.cb.not_permitted";
    public static final String CB_STATE = "web.cb.state";
    public static final String HANG_DETECTIONS = "web.hang.detections";
    public static final String SSE_CONNECTED = "web.sse.connections";
    public static final String SSE_SEND = "web.sse.send";
    public static final String SSE_DISCONNECTED = "web.sse.disconnect";

    /** CB state gauge 数值映射：CLOSED=0 / HALF_OPEN=1 / OPEN=2。 */
    public static final double CB_STATE_CLOSED = 0d;
    public static final double CB_STATE_HALF_OPEN = 1d;
    public static final double CB_STATE_OPEN = 2d;

    @Nullable
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Boolean> gaugeRegistered = new ConcurrentHashMap<>();

    public WebMetrics(@Nullable MeterRegistry registry) {
        this.registry = registry;
    }

    /** 空操作实例：registry=null，所有方法直接 return。 */
    public static WebMetrics noop() {
        return new WebMetrics(null);
    }

    // ─────────────────────── §4.1 RateLimit ───────────────────────

    public void rateLimitAllow() {
        if (registry == null) return;
        registry.counter(RATE_LIMIT_ALLOW).increment();
    }

    /**
     * @param reason  {@code client_unidentified} / {@code rate_limited}
     * @param pattern 命中的 route pattern（未命中时为 {@code __global__}）
     */
    public void rateLimitReject(String reason, String pattern) {
        if (registry == null) return;
        registry.counter(RATE_LIMIT_REJECT, "reason", reason, "pattern", pattern).increment();
    }

    // ─────────────────────── §4.2 CircuitBreaker ───────────────────────

    /**
     * 业务方手动调：在 {@code cb.execute(callable)} 的 try/finally 块中，根据执行结果调
     * {@code cbCall("success"/"failure", pattern)}。
     * <p>拦截器层无法自动包 controller 方法（{@link com.richie.component.web.core.spi.WebInterceptorChain#proceed}
     * 返回 void），故业务方需显式接入——这是 Java 生态共识（Resilience4j / Sentinel / Hystrix 同理）。
     *
     * @param result  {@code success} / {@code failure}
     * @param pattern 命中的 route pattern（未命中时为 {@code __global__}）
     */
    public void cbCall(String result, String pattern) {
        if (registry == null) return;
        registry.counter(CB_CALLS, "result", result, "pattern", pattern).increment();
    }

    /**
     * 由 {@code CircuitBreakerInterceptor} 在 OPEN 短路时自动调（tag = pattern）。
     */
    public void cbNotPermitted(String pattern) {
        if (registry == null) return;
        registry.counter(CB_NOT_PERMITTED, "pattern", pattern).increment();
    }

    /**
     * 通用 gauge 注册（CB state / 业务自定义 gauge 都走这里）。
     * <p>典型用法——CB 状态 gauge：
     * <pre>{@code
     * metrics.registerGauge(WebMetrics.CB_STATE, cb,
     *         c -> (double) ((CircuitBreaker) c).state().ordinal(),
     *         "key", cbKey);
     * }</pre>
     *
     * <p><strong>幂等性</strong>：以 {@code name + "|" + joined tags} 作为 key，
     * 同 key 多次注册仅生效一次（{@link #gaugeRegistered} 去重 + Micrometer 内部同名 gauge 去重）。
     *
     * <p><strong>类路径解耦</strong>：{@code source} 为 {@link Object}、{@code valueFn} 为
     * {@link ToDoubleFunction}{@code <Object>}——本类签名不强制依赖 {@code CircuitBreaker} 等外部类，
     * 调用方 lambda 内自行 cast 到具体类型。
     *
     * @param name    Micrometer meter 名（建议引用 {@link #CB_STATE} 等常量）
     * @param source  gauge 读取 state 的源对象（被 Micrometer 持有引用，不可被 GC）
     * @param valueFn scrape 时实时计算 gauge value
     * @param tags    {@code k1, v1, k2, v2, ...} 形式的 key/value 交替数组
     */
    public void registerGauge(String name, Object source, ToDoubleFunction<Object> valueFn, String... tags) {
        if (registry == null) return;
        if (source == null) return;
        String key = gaugeKey(name, tags);
        gaugeRegistered.computeIfAbsent(key, _ -> {
            Gauge.builder(name, source, valueFn).tags(tags).register(registry);
            return Boolean.TRUE;
        });
    }

    private static String gaugeKey(String name, String[] tags) {
        if (tags == null || tags.length == 0) {
            return name;
        }
        return name + "|" + StringUtils.arrayToDelimitedString(tags, "|");
    }

    // ─────────────────────── §4.4 HangDetection ───────────────────────

    /**
     * @param level {@code warn} / {@code dump} / {@code kill_switch}
     */
    public void hangDetected(String level) {
        if (registry == null) return;
        registry.counter(HANG_DETECTIONS, "level", level).increment();
    }

    // ─────────────────────── §4.4 SSE 长连接 ───────────────────────

    /**
     * 累计 SSE 连接建立次数。由 {@code SseManager.connect(clientId)} 自动埋。
     */
    public void sseConnected() {
        if (registry == null) return;
        registry.counter(SSE_CONNECTED).increment();
    }

    /**
     * @param result {@code success} / {@code failure} / {@code miss}
     */
    public void sseSend(String result) {
        if (registry == null) return;
        registry.counter(SSE_SEND, "result", result).increment();
    }

    /**
     * @param reason {@code completion} / {@code timeout} / {@code error} / {@code manual} / {@code shutdown}
     */
    public void sseDisconnected(String reason) {
        if (registry == null) return;
        registry.counter(SSE_DISCONNECTED, "reason", reason).increment();
    }

    /**
     * 注册 SSE 当前连接数 gauge。由 {@code SseManager} 在 {@code start()} 时调用——gauge value
     * 通过 {@link java.util.function.ToDoubleFunction} 实时读 {@code SseManager.activeCount()}。
     * <p>多次注册幂等：Micrometer 内部对同名 gauge 去重。
     *
     * @param manager {@code SseManager} 实例（activeCount() 必须可访问）
     */
    public void registerSseConnectionsGauge(SseManager manager) {
        if (registry == null) return;
        if (manager == null) return;
        Gauge.builder(SSE_CONNECTED, manager, m -> (double) m.activeCount())
                .register(registry);
    }
}