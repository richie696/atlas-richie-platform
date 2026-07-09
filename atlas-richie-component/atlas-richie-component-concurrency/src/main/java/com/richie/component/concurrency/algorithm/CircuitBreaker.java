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
package com.richie.component.concurrency.algorithm;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器 —— 保护下游服务在故障期间不被持续请求压垮，业界经典三态(Circuit Breaker)模式。
 *
 * <p>熔断器是构建高可用系统的标配组件，其核心思想借鉴于电路保险丝：</p>
 *
 * <ul>
 *   <li><b>CLOSED (闭合)</b>：正常状态，请求正常通过；同时记录最近调用的失败情况</li>
 *   <li><b>OPEN (断开)</b>：故障状态，请求立即失败（不再调用下游），不再压垮已故障的系统</li>
 *   <li><b>HALF_OPEN (半开)</b>：探测状态，等待一段时间后放一个请求"试探"，成功则闭合，否则再次断开</li>
 * </ul>
 *
 * <h2>状态机</h2>
 * <pre>{@code
 *      ┌─────────────┐
 *      │   CLOSED    │ ←─── 成功 ───┐
 *      └──┬──────────┘              │
 *   失败率 │≥ 阈值                    │
 *         ▼                         │
 *   ┌─────────────┐  超时 │          │
 *   │    OPEN     │──────→┌───────────────┐
 *   └─────────────┘       │  HALF_OPEN    │
 *                         └──┬─────────┬──┘
 *                      成功 │         │ 失败
 *                            ▼         ▼
 *                          CLOSED    OPEN
 * }</pre>
 *
 * <h3>基本使用</h3>
 * <pre>{@code
 * // 默认配置：50% 失败率触发熔断，OPEN 状态持续 10 秒
 * CircuitBreaker breaker = CircuitBreaker.ofDefaults();
 *
 * // 严格按失败次数：10 次失败立即熔断
 * CircuitBreaker breaker = CircuitBreaker.ofCount(10, Duration.ofSeconds(30));
 *
 * // 自定义：60% 失败率，监控最近 100 次调用，OPEN 持续 30 秒
 * CircuitBreaker breaker = CircuitBreaker.builder()
 *     .failurePercent(60)
 *     .windowSize(100)
 *     .openDuration(Duration.ofSeconds(30))
 *     .build();
 *
 * // 包裹调用：失败时返回兜底值
 * String result = breaker.execute(
 *     () -> callRemoteService(),
 *     "default-value"
 * );
 *
 * // 显式抛出熔断异常
 * String result = breaker.executeOrThrow(() -> callRemoteService());
 * }</pre>
 *
 * <h3>滑动窗口策略</h3>
 * <p>本工具提供两种失败率统计窗口：</p>
 * <ul>
 *   <li><b>计数窗口 (COUNT_BASED)</b>：仅保留最近 N 次调用的成功/失败状态，先进先出</li>
 *   <li><b>时间窗口 (TIME_BASED)</b>：保留最近 {@code windowDuration} 内的所有调用，更精确</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>所有 {@code execute*()} 方法均为线程安全，可在多线程并发调用同一熔断器实例。
 * 状态切换由 {@link AtomicReference} 保护，状态转移的关键段使用 {@code synchronized}。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class CircuitBreaker {

    /**
     * 熔断器状态。
     */
    public enum State {
        /** 正常：请求通过，记录成功/失败统计 */
        CLOSED,
        /** 断开：请求被立即拒绝，不再调用下游 */
        OPEN,
        /** 半开：允许单个请求探测，验证下游是否恢复 */
        HALF_OPEN
    }

    /**
     * 滑动窗口策略。
     */
    public enum SlidingWindowType {
        /** 计数窗口：保留最近 {@code windowSize} 次调用 */
        COUNT_BASED,
        /** 时间窗口：保留最近 {@code windowDuration} 内的所有调用 */
        TIME_BASED
    }

    /**
     * 默认配置：50% 失败率触发熔断、监控最近 100 次调用、OPEN 持续 10 秒。
     */
    private static final int DEFAULT_FAILURE_PERCENT = 50;
    private static final int DEFAULT_WINDOW_SIZE = 100;
    private static final Duration DEFAULT_OPEN_DURATION = Duration.ofSeconds(10);
    private static final Duration DEFAULT_WINDOW_DURATION = Duration.ofSeconds(10);
    private static final int MIN_SAMPLE_SIZE = 10;

    private final int failureThreshold;       // 失败率阈值或绝对失败次数
    private final boolean useFailureRate;     // true=按百分比，false=按绝对次数
    private final int windowSize;
    private final Duration windowDuration;
    private final Duration openDuration;
    private final SlidingWindowType windowType;
    private final Clock clock;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicLong openedAtNanos = new AtomicLong(0L);

    // COUNT_BASED:环形缓冲存储每次调用的成功(true)/失败(false)
    private final boolean[] countOutcomes;
    private final AtomicLong countIndex = new AtomicLong(0L);

    // TIME_BASED:分别记录每次成功/失败的时间戳(纳秒)
    private final long[] timeSuccessNanos;
    private final long[] timeFailureNanos;
    private final AtomicLong timeSuccessIndex = new AtomicLong(0L);
    private final AtomicLong timeFailureIndex = new AtomicLong(0L);

    /**
     * HALF_OPEN 状态下的单次探测门控。
     *
     * <p>通过 {@code compareAndSet(false, true)} 保证同一时刻只有一个线程
     * 能在 HALF_OPEN 状态下执行下游调用（"单次探测"语义），其余并发线程
     * 将被视为 OPEN 状态直接拒绝。</p>
     *
     * <p>在状态转移 (HALF_OPEN → CLOSED / HALF_OPEN → OPEN) 或 {@link #reset()}
     * 时于 {@code synchronized} 块内重置为 {@code false}。</p>
     */
    private final AtomicBoolean halfOpenGate = new AtomicBoolean(false);

    private CircuitBreaker(Builder b, Clock clock) {
        this.failureThreshold = b.failureThreshold;
        this.useFailureRate = b.useFailureRate;
        this.windowSize = b.windowSize;
        this.windowDuration = b.windowDuration;
        this.openDuration = b.openDuration;
        this.windowType = b.windowType;
        this.clock = clock;

        if (windowType == SlidingWindowType.COUNT_BASED) {
            this.countOutcomes = new boolean[windowSize];
            this.timeSuccessNanos = null;
            this.timeFailureNanos = null;
        } else {
            this.countOutcomes = null;
            this.timeSuccessNanos = new long[windowSize];
            this.timeFailureNanos = new long[windowSize];
        }
    }

    // ========== 工厂方法 ==========

    /**
     * 默认配置熔断器：50% 失败率触发熔断、监控最近 100 次调用、OPEN 持续 10 秒。
     */
    public static CircuitBreaker ofDefaults() {
        return builder().build();
    }

    /**
     * 按"失败率"触发的熔断器，使用默认 100 次调用的计数窗口。
     *
     * @param failurePercent 失败率阈值（1-100），达到即触发熔断
     * @param openDuration   OPEN 状态持续时间
     */
    public static CircuitBreaker of(int failurePercent, Duration openDuration) {
        return builder()
                .failurePercent(failurePercent)
                .openDuration(openDuration)
                .build();
    }

    /**
     * 按"绝对失败次数"触发的熔断器。
     *
     * <p>一旦窗口内累计达到 {@code failureCount} 次失败即触发熔断，无需计算失败率。
     * 适合"任何失败都不可接受"的强一致性场景。</p>
     *
     * @param failureCount 失败次数阈值（必须 ≥ 1）
     * @param openDuration OPEN 状态持续时间
     */
    public static CircuitBreaker ofCount(int failureCount, Duration openDuration) {
        if (failureCount < 1) {
            throw new IllegalArgumentException("failureCount must be >= 1, got: " + failureCount);
        }
        Objects.requireNonNull(openDuration, "openDuration must not be null");
        if (openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("openDuration must be positive, got: " + openDuration);
        }
        return builder()
                .failureCount(failureCount)
                .openDuration(openDuration)
                .build();
    }

    /**
     * 按"指定时间窗口内的失败率"触发的熔断器。
     *
     * @param failurePercent 失败率阈值（1-100）
     * @param window         时间窗口长度（必须正）
     * @param openDuration   OPEN 状态持续时间
     */
    public static CircuitBreaker ofRate(int failurePercent, Duration window, Duration openDuration) {
        return builder()
                .failurePercent(failurePercent)
                .slidingWindowType(SlidingWindowType.TIME_BASED)
                .windowDuration(window)
                .openDuration(openDuration)
                .build();
    }

    /**
     * 创建一个新的 {@link Builder}。
     */
    public static Builder builder() {
        return new Builder();
    }

    // ========== 执行 ==========

    /**
     * 执行指定任务。
     *
     * <p>详细语义：</p>
     * <ul>
     *   <li><b>CLOSED 状态：</b>正常执行任务；任务抛出的异常会被计数 + 抛出</li>
     *   <li><b>HALF_OPEN 状态：</b>放行首个"试探"调用；后续并发调用仍按 OPEN 处理</li>
     *   <li><b>OPEN 状态：</b>立即抛出 {@link CircuitBreakerOpenException}</li>
     * </ul>
     *
     * @param task 要执行的任务
     */
    public <T> T execute(Callable<T> task) throws Exception {
        Objects.requireNonNull(task, "task must not be null");
        return doExecute(task);
    }

    /**
     * 执行任务并在熔断或异常时返回 fallback，不抛出异常。
     *
     * <p>{@link CircuitBreakerOpenException} 与任务异常统一捕获，返回 {@code fallback}。
     * 适用于"任何失败都不能让调用方崩溃"的兜底场景。</p>
     */
    public <T> T execute(Callable<T> task, T fallback) {
        Objects.requireNonNull(task, "task must not be null");
        try {
            return doExecute(task);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * 执行任务并显式抛出 {@link CircuitBreakerOpenException} 当熔断器处于 OPEN 状态。
     */
    public <T> T executeOrThrow(Callable<T> task) throws Exception {
        Objects.requireNonNull(task, "task must not be null");
        return doExecute(task);
    }

    private <T> T doExecute(Callable<T> task) throws Exception {
        advanceStateIfNeeded();

        State currentState = state.get();
        if (currentState == State.OPEN) {
            throw new CircuitBreakerOpenException(
                    "CircuitBreaker is OPEN; rejecting call without invoking downstream");
        }

        // HALF_OPEN: 通过 CAS 保证单次探测语义 —— 仅一个线程放行，其余并发调用按 OPEN 拒绝
        if (currentState == State.HALF_OPEN && !halfOpenGate.compareAndSet(false, true)) {
            throw new CircuitBreakerOpenException(
                    "CircuitBreaker is HALF_OPEN; another probe is already in progress, rejecting concurrent call");
        }

        try {
            T result = task.call();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e;
        }
    }

    /**
     * 返回当前熔断器状态。
     */
    public State state() {
        return state.get();
    }

    /**
     * 强制将熔断器重置为 CLOSED，清空所有统计计数。
     */
    public void reset() {
        synchronized (this) {
            state.set(State.CLOSED);
            openedAtNanos.set(0L);
            halfOpenGate.set(false);
            countIndex.set(0L);
            timeSuccessIndex.set(0L);
            timeFailureIndex.set(0L);
            if (countOutcomes != null) {
                Arrays.fill(countOutcomes, false);
            }
            if (timeSuccessNanos != null) {
                Arrays.fill(timeSuccessNanos, 0L);
            }
            if (timeFailureNanos != null) {
                Arrays.fill(timeFailureNanos, 0L);
            }
        }
    }

    /**
     * 强制将熔断器设为 OPEN（用于测试）。
     */
    public void forceOpen() {
        synchronized (this) {
            state.set(State.OPEN);
            openedAtNanos.set(clock.nanos());
        }
    }

    // ========== 内部：状态推进 ==========

    private void advanceStateIfNeeded() {
        if (state.get() != State.OPEN) {
            return;
        }
        long openedAt = openedAtNanos.get();
        long elapsed = clock.nanos() - openedAt;
        if (elapsed < openDuration.toNanos()) {
            return;
        }
        synchronized (this) {
            if (state.get() == State.OPEN) {
                long stillElapsed = clock.nanos() - openedAtNanos.get();
                if (stillElapsed >= openDuration.toNanos()) {
                    state.set(State.HALF_OPEN);
                    halfOpenGate.set(false);
                }
            }
        }
    }

    private void onSuccess() {
        recordOutcome(true);
        if (state.get() == State.HALF_OPEN) {
            synchronized (this) {
                if (state.get() == State.HALF_OPEN) {
                    state.set(State.CLOSED);
                    openedAtNanos.set(0L);
                    halfOpenGate.set(false);
                    clearCounters();
                }
            }
        }
    }

    private void onFailure() {
        recordOutcome(false);
        if (state.get() == State.HALF_OPEN) {
            synchronized (this) {
                if (state.get() == State.HALF_OPEN) {
                    state.set(State.OPEN);
                    openedAtNanos.set(clock.nanos());
                    halfOpenGate.set(false);
                }
            }
            return;
        }

        if (state.get() == State.CLOSED && shouldTrip()) {
            synchronized (this) {
                if (state.get() == State.CLOSED && shouldTrip()) {
                    state.set(State.OPEN);
                    openedAtNanos.set(clock.nanos());
                }
            }
        }
    }

    private boolean shouldTrip() {
        long[] stats = snapshot();
        long total = stats[0] + stats[1];
        if (total == 0) {
            return false;
        }
        if (useFailureRate) {
            // 至少累积 MIN_SAMPLE_SIZE 次调用再判定，避免冷启动误熔断
            if (total < Math.min(windowSize, MIN_SAMPLE_SIZE)) {
                return false;
            }
            int percent = (int) ((stats[1] * 100L) / total);
            return percent >= failureThreshold;
        }
        return stats[1] >= failureThreshold;
    }

    private void recordOutcome(boolean success) {
        if (windowType == SlidingWindowType.COUNT_BASED) {
            long idx = countIndex.getAndIncrement();
            // 位掩码防止 AtomicLong 溢出为负数时 (int)(idx % windowSize) 产生负数下标
            int slot = (int) ((idx & Long.MAX_VALUE) % windowSize);
            Objects.requireNonNull(countOutcomes, "countOutcomes must not be null in COUNT_BASED mode")
                    [slot] = success;
        } else {
            long now = clock.nanos();
            if (success) {
                long idx = timeSuccessIndex.getAndIncrement();
                int slot = (int) ((idx & Long.MAX_VALUE) % windowSize);
                Objects.requireNonNull(timeSuccessNanos, "timeSuccessNanos must not be null in TIME_BASED mode")
                        [slot] = now;
            } else {
                long idx = timeFailureIndex.getAndIncrement();
                int slot = (int) ((idx & Long.MAX_VALUE) % windowSize);
                Objects.requireNonNull(timeFailureNanos, "timeFailureNanos must not be null in TIME_BASED mode")
                        [slot] = now;
            }
        }
    }

    /**
     * 统计当前窗口内的 [successCount, failureCount]。
     */
    private long[] snapshot() {
        if (countOutcomes != null) {
            return snapshotCountBased();
        }
        return snapshotTimeBased();
    }

    private long[] snapshotCountBased() {
        long success = 0L;
        long failure = 0L;
        boolean[] outcomes = this.countOutcomes;
        if (outcomes == null) {
            return new long[]{0L, 0L};
        }
        long recorded = Math.min(countIndex.get(), windowSize);
        for (int i = 0; i < recorded; i++) {
            if (outcomes[i]) {
                success++;
            } else {
                failure++;
            }
        }
        return new long[]{success, failure};
    }

    private long[] snapshotTimeBased() {
        long now = clock.nanos();
        long windowNanos = windowDuration.toNanos();
        long success = 0L;
        long failure = 0L;
        long[] successArr = this.timeSuccessNanos;
        long[] failureArr = this.timeFailureNanos;
        if (successArr == null || failureArr == null) {
            return new long[]{0L, 0L};
        }
        long totalSuccess = timeSuccessIndex.get();
        long totalFailure = timeFailureIndex.get();
        for (int i = 0; i < windowSize && i < totalSuccess; i++) {
            long ts = successArr[i];
            if (now - ts <= windowNanos) {
                success++;
            }
        }
        for (int i = 0; i < windowSize && i < totalFailure; i++) {
            long ts = failureArr[i];
            if (now - ts <= windowNanos) {
                failure++;
            }
        }
        return new long[]{success, failure};
    }

    private void clearCounters() {
        halfOpenGate.set(false);
        countIndex.set(0L);
        timeSuccessIndex.set(0L);
        timeFailureIndex.set(0L);
        if (countOutcomes != null) {
            Arrays.fill(countOutcomes, false);
        }
        if (timeSuccessNanos != null) {
            Arrays.fill(timeSuccessNanos, 0L);
        }
        if (timeFailureNanos != null) {
            Arrays.fill(timeFailureNanos, 0L);
        }
    }

    /**
     * 抽象时钟，便于测试时注入假时钟。
     */
    @FunctionalInterface
    interface Clock {
        long nanos();

        static Clock system() {
            return System::nanoTime;
        }
    }

    // ========== Builder ==========

    /**
     * {@link CircuitBreaker} 构建器，支持细粒度配置。
     */
    public static final class Builder {
        private int failureThreshold = DEFAULT_FAILURE_PERCENT;
        private boolean useFailureRate = true;
        private int windowSize = DEFAULT_WINDOW_SIZE;
        private Duration windowDuration = DEFAULT_WINDOW_DURATION;
        private Duration openDuration = DEFAULT_OPEN_DURATION;
        private SlidingWindowType windowType = SlidingWindowType.COUNT_BASED;

        private Builder() {
        }

        /**
         * 设置失败率阈值（默认 50，范围 1-100）。
         */
        public Builder failurePercent(int percent) {
            if (percent < 1 || percent > 100) {
                throw new IllegalArgumentException("failurePercent must be in [1,100], got: " + percent);
            }
            this.failureThreshold = percent;
            this.useFailureRate = true;
            return this;
        }

        /**
         * 设置绝对失败次数阈值（必须 ≥ 1）。一旦窗口内累计达到此次数即熔断。
         */
        public Builder failureCount(int count) {
            if (count < 1) {
                throw new IllegalArgumentException("failureCount must be >= 1, got: " + count);
            }
            this.failureThreshold = count;
            this.useFailureRate = false;
            return this;
        }

        /**
         * 设置滑动窗口大小（计数模式，默认 100，最小 10）。
         */
        public Builder windowSize(int size) {
            if (size < 10) {
                throw new IllegalArgumentException("windowSize must be >= 10, got: " + size);
            }
            this.windowSize = size;
            return this;
        }

        /**
         * 设置 OPEN 状态持续时间（默认 10 秒）。
         */
        public Builder openDuration(Duration duration) {
            Objects.requireNonNull(duration, "openDuration must not be null");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("openDuration must be positive, got: " + duration);
            }
            this.openDuration = duration;
            return this;
        }

        /**
         * 设置滑动窗口类型（默认 {@link SlidingWindowType#COUNT_BASED}）。
         */
        public Builder slidingWindowType(SlidingWindowType type) {
            Objects.requireNonNull(type, "slidingWindowType must not be null");
            this.windowType = type;
            return this;
        }

        /**
         * 设置时间窗口长度（仅 TIME_BASED 模式生效，默认 10 秒）。
         */
        public Builder windowDuration(Duration duration) {
            Objects.requireNonNull(duration, "windowDuration must not be null");
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("windowDuration must be positive");
            }
            this.windowDuration = duration;
            return this;
        }

        /**
         * 构建 {@link CircuitBreaker} 实例（默认系统纳秒时钟）。
         */
        public CircuitBreaker build() {
            return new CircuitBreaker(this, Clock.system());
        }

        /**
         * 使用自定义纳秒时钟构建 {@link CircuitBreaker}（主要用于测试时间相关行为）。
         */
        public CircuitBreaker build(java.util.function.LongSupplier nanosSupplier) {
            Objects.requireNonNull(nanosSupplier, "nanosSupplier must not be null");
            Clock testClock = nanosSupplier::getAsLong;
            return new CircuitBreaker(this, testClock);
        }
    }
}
