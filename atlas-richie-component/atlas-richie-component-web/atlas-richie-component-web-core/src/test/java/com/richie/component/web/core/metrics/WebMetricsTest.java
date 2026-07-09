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

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link WebMetrics} A 方案埋点测试：
 * <ul>
 *   <li>{@code cb.state} gauge 自动注册（A 方案核心）</li>
 *   <li>cbCall / cbNotPermitted counter 业务方手动调用路径</li>
 *   <li>noop() 空操作语义</li>
 *   <li>同一 cbKey 多次注册幂等</li>
 * </ul>
 */
class WebMetricsTest {

    private static final ToDoubleFunction<Object> CB_STATE_VALUE_FN =
            c -> (double) ((CircuitBreaker) c).state().ordinal();

    @Test
    void noop_doesNotRegisterAnything() {
        WebMetrics metrics = WebMetrics.noop();
        metrics.rateLimitAllow();
        metrics.cbCall("success", "/api/v1/orders");
        metrics.cbNotPermitted("__global__");
        metrics.registerGauge(WebMetrics.CB_STATE,
                CircuitBreaker.ofRate(50, Duration.ofSeconds(10), Duration.ofSeconds(30)),
                CB_STATE_VALUE_FN, "key", "k1");
        metrics.hangDetected("warn");
        // 不抛异常即通过（registry=null 时空操作）
        assertThat(metrics).isNotNull();
    }

    @Test
    void registerGauge_publishesGaugeWithKeyTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebMetrics metrics = new WebMetrics(registry);
        CircuitBreaker cb = CircuitBreaker.ofRate(50, Duration.ofSeconds(10), Duration.ofSeconds(30));

        metrics.registerGauge(WebMetrics.CB_STATE, cb, CB_STATE_VALUE_FN, "key", "user-1");

        Gauge gauge = registry.find(WebMetrics.CB_STATE)
                .tag("key", "user-1")
                .gauge();
        assertThat(gauge).isNotNull();
        // 默认 CLOSED → ordinal=0 → gauge value = 0
        assertThat(gauge.value()).isEqualTo(WebMetrics.CB_STATE_CLOSED);
    }

    @Test
    void registerGauge_reflectsLiveStateChange() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebMetrics metrics = new WebMetrics(registry);
        CircuitBreaker cb = CircuitBreaker.ofRate(50, Duration.ofSeconds(10), Duration.ofSeconds(30));

        metrics.registerGauge(WebMetrics.CB_STATE, cb, CB_STATE_VALUE_FN, "key", "k1");

        // 初始 CLOSED
        Gauge gauge = registry.find(WebMetrics.CB_STATE).tag("key", "k1").gauge();
        assertThat(gauge.value()).isEqualTo(WebMetrics.CB_STATE_CLOSED);

        // 验证 ordinal 映射：cb.state() 返回 enum，每个 enum 在 enum class 中顺序固定 CLOSED=0, HALF_OPEN=1, OPEN=2
        assertThat(cb.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.state().ordinal()).isEqualTo(0);
    }

    @Test
    void registerGauge_isIdempotentForSameKey() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebMetrics metrics = new WebMetrics(registry);
        CircuitBreaker cb1 = CircuitBreaker.ofRate(50, Duration.ofSeconds(10), Duration.ofSeconds(30));
        CircuitBreaker cb2 = CircuitBreaker.ofRate(60, Duration.ofSeconds(20), Duration.ofSeconds(40));

        metrics.registerGauge(WebMetrics.CB_STATE, cb1, CB_STATE_VALUE_FN, "key", "dup-key");
        metrics.registerGauge(WebMetrics.CB_STATE, cb2, CB_STATE_VALUE_FN, "key", "dup-key"); // 同 key 第二次应幂等（不抛、不覆盖）

        // Micrometer 同名 gauge 幂等：find() 只返回一个
        long gaugeCount = registry.find(WebMetrics.CB_STATE).tag("key", "dup-key").gauges().size();
        assertThat(gaugeCount).isEqualTo(1);
        // 第一次注册的 cb1 仍生效（Micrometer 引用第一个 source）
        Gauge gauge = registry.find(WebMetrics.CB_STATE).tag("key", "dup-key").gauge();
        assertThat(gauge.value()).isEqualTo(WebMetrics.CB_STATE_CLOSED);
    }

    @Test
    void cbCall_and_cbNotPermitted_incrementCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebMetrics metrics = new WebMetrics(registry);

        metrics.cbCall("success", "/api/v1/payments/**");
        metrics.cbCall("success", "/api/v1/payments/**");
        metrics.cbCall("failure", "/api/v1/payments/**");
        metrics.cbNotPermitted("/api/v1/payments/**");

        assertThat(registry.counter(WebMetrics.CB_CALLS, "result", "success", "pattern", "/api/v1/payments/**").count())
                .isEqualTo(2d);
        assertThat(registry.counter(WebMetrics.CB_CALLS, "result", "failure", "pattern", "/api/v1/payments/**").count())
                .isEqualTo(1d);
        assertThat(registry.counter(WebMetrics.CB_NOT_PERMITTED, "pattern", "/api/v1/payments/**").count())
                .isEqualTo(1d);
    }

    @Test
    void hangDetected_incrementsWithLevelTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebMetrics metrics = new WebMetrics(registry);

        metrics.hangDetected("warn");
        metrics.hangDetected("dump");
        metrics.hangDetected("kill_switch");

        assertThat(registry.counter(WebMetrics.HANG_DETECTIONS, "level", "warn").count()).isEqualTo(1d);
        assertThat(registry.counter(WebMetrics.HANG_DETECTIONS, "level", "dump").count()).isEqualTo(1d);
        assertThat(registry.counter(WebMetrics.HANG_DETECTIONS, "level", "kill_switch").count()).isEqualTo(1d);
    }

    @Test
    void registerGauge_withNullSource_isNoop() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        WebMetrics metrics = new WebMetrics(registry);

        metrics.registerGauge(WebMetrics.CB_STATE, null, CB_STATE_VALUE_FN, "key", "k-null");

        Gauge gauge = registry.find(WebMetrics.CB_STATE).tag("key", "k-null").gauge();
        assertThat(gauge).isNull();
    }
}