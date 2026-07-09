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
package com.richie.component.concurrency.config.properties;

import com.richie.component.concurrency.config.ConcurrencyAutoConfiguration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 熔断器子系统配置 —— 绑定 {@code platform.concurrency.circuit-breaker.*} 命名空间。
 *
 * <p>管理本组件提供的 {@link com.richie.component.concurrency.algorithm.CircuitBreaker} 三态熔断器
 * 的注册开关与行为参数。配置示例：</p>
 *
 * <pre>{@code
 * platform:
 *   concurrency:
 *     circuit-breaker:
 *       enabled: true
 *       failure-rate-threshold: 0.5
 *       sliding-window-size: 100
 *       wait-duration: 30s
 *       half-open-max-successes: 3
 * }</pre>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@link #enabled} —— 是否在 Spring 容器中注册熔断器 Bean（默认：{@code false}）</li>
 *   <li>{@link #failureRateThreshold} —— 触发熔断的失败率阈值（{@code 0.0} ~ {@code 1.0}，默认：
 *       {@code 0.5}），达到即从 CLOSED 切换到 OPEN</li>
 *   <li>{@link #slidingWindowSize} —— 滑动窗口大小（计数模式，默认：{@code 10}），窗口内累计足够
 *       样本才会判定失败率，避免冷启动误熔断</li>
 *   <li>{@link #waitDuration} —— OPEN 状态持续时间（默认：{@code 30s}），超时后进入 HALF_OPEN 试探</li>
 *   <li>{@link #halfOpenMaxSuccesses} —— 半开探测阶段需要的连续成功次数（默认：{@code 3}），
 *       达到后回到 CLOSED；当前底层 {@code CircuitBreaker.Builder} 默认采用单次探测即切换状态，
 *       该字段留作未来扩展，当前版本不参与构建参数计算</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.concurrency.circuit-breaker")
public class CircuitBreakerProperties {

    /**
     * 是否在 Spring 容器中注册熔断器 Bean（默认：{@code false}）。
     *
     * <p>设为 {@code true} 后，{@link ConcurrencyAutoConfiguration}
     * 会创建一个 {@link com.richie.component.concurrency.algorithm.CircuitBreaker} Bean，
     * 供业务方按需注入。</p>
     */
    private boolean enabled = false;

    /**
     * 触发熔断的失败率阈值（{@code 0.0} ~ {@code 1.0}，默认：{@code 0.5}）。
     *
     * <p>在 CLOSED 状态下，当滑动窗口内失败率 ≥ 该阈值时切换到 OPEN。该值以
     * {@code double} 形式暴露是为了配置可读性（{@code 0.5} 比 {@code 50} 更直观），内部构建
     * {@link com.richie.component.concurrency.algorithm.CircuitBreaker.Builder} 时会乘以
     * {@code 100} 转为整数百分比。</p>
     */
    private double failureRateThreshold = 0.5;

    /**
     * 滑动窗口大小（计数模式，默认：{@code 10}）。
     *
     * <p>该值是底层熔断器维护的调用结果环形缓冲区大小；窗口越大失败率统计越平滑，
     * 但对突发故障的反应越迟钝。</p>
     */
    private int slidingWindowSize = 10;

    /**
     * OPEN 状态持续时间（默认：{@code 30s}）。
     *
     * <p>OPEN 状态下所有调用立即被拒绝；超过该时长后熔断器进入 HALF_OPEN 试探状态，
     * 下一次调用作为探测决定最终回到 CLOSED 或重新进入 OPEN。</p>
     */
    private Duration waitDuration = Duration.ofSeconds(30);

    /**
     * 半开探测阶段需要的连续成功次数（默认：{@code 3}）。
     *
     * <p>当前版本底层 {@link com.richie.component.concurrency.algorithm.CircuitBreaker}
     * 采用"单次探测成功即关闭"语义，该字段暂不直接参与构建参数；保留该字段是为了
     * 未来扩展（升级到多次探测模式时无需破坏配置契约）。</p>
     */
    private int halfOpenMaxSuccesses = 3;
}
