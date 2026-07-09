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
package com.richie.component.concurrency.algorithm;

import com.richie.component.concurrency.algorithm.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CircuitBreaker}.
 *
 * <p>覆盖熔断器的全部公开 API 与状态机关键路径：</p>
 *
 * <ol>
 *   <li>工厂方法的语义校验</li>
 *   <li>CLOSED 状态下的成功累计与失败累计</li>
 *   <li>CLOSED → OPEN 转换触发阈值</li>
 *   <li>OPEN → HALF_OPEN 自动探测</li>
 *   <li>HALF_OPEN → CLOSED（试探成功）与 HALF_OPEN → OPEN（试探失败）</li>
 *   <li>失败率模式与绝对次数模式</li>
 *   <li>时间窗口模式</li>
 *   <li>execute(task, fallback) 容错路径</li>
 *   <li>forceOpen / reset 手动控制</li>
 *   <li>10 线程并发访问下的线程安全性</li>
 *   <li>使用 {@link CircuitBreaker.Builder#build(java.util.function.LongSupplier)} 注入假时钟验证时间相关状态转换</li>
 * </ol>
 */
class CircuitBreakerTest {

    // ============================================================================================
    // 工厂方法 & 默认配置
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("ofDefaults: 初始状态 CLOSED，状态机入口正确")
    void ofDefaults_startsAsClosed() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @Timeout(2)
    @DisplayName("of: 失败率模式构造的初始状态为 CLOSED")
    void of_startsAsClosed() {
        CircuitBreaker breaker = CircuitBreaker.of(50, Duration.ofSeconds(5));
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @Timeout(2)
    @DisplayName("of: null openDuration 抛 NullPointerException")
    void of_nullOpenDurationThrows() {
        assertThatThrownBy(() -> CircuitBreaker.of(50, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("ofCount: 失败次数阈值熔断器")
    void ofCount_startsAsClosed() {
        CircuitBreaker breaker = CircuitBreaker.ofCount(5, Duration.ofSeconds(1));
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @Timeout(2)
    @DisplayName("ofCount: 0 失败次数抛 IllegalArgumentException")
    void ofCount_zeroCountThrows() {
        assertThatThrownBy(() -> CircuitBreaker.ofCount(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("ofCount: 负数失败次数抛 IllegalArgumentException")
    void ofCount_negativeCountThrows() {
        assertThatThrownBy(() -> CircuitBreaker.ofCount(-1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ============================================================================================
    // 计数窗口：CLOSED → OPEN 触发
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("execute: 全部成功时不触发熔断，状态保持 CLOSED")
    void execute_allSucceed_remainsClosed() throws Exception {
        CircuitBreaker breaker = CircuitBreaker.ofCount(3, Duration.ofSeconds(1));
        for (int i = 0; i < 10; i++) {
            String result = breaker.execute(() -> "ok-" + System.nanoTime());
            assertThat(result).startsWith("ok-");
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @Timeout(2)
    @DisplayName("execute: 失败次数达到阈值时触发熔断，状态变为 OPEN")
    void execute_failuresReachThreshold_tripsToOpen() {
        CircuitBreaker breaker = CircuitBreaker.ofCount(3, Duration.ofSeconds(1));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new RuntimeException("boom-" + idx);
            })).isInstanceOf(RuntimeException.class).hasMessageContaining("boom-");
        }

        assertThat(breaker.state())
                .as("after 3 failures (threshold), circuit must trip to OPEN")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @Timeout(2)
    @DisplayName("execute: OPEN 状态下再次调用立即拒绝，抛出 CircuitBreakerOpenException")
    void execute_openStateRejectsCalls() {
        CircuitBreaker breaker = CircuitBreaker.ofCount(2, Duration.ofSeconds(1));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new RuntimeException("trigger-open");
            })).isInstanceOf(RuntimeException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> breaker.execute(() -> "downstream-payload"))
                .as("OPEN state must reject with CircuitBreakerOpenException, NOT invoke downstream")
                .isInstanceOf(CircuitBreakerOpenException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("execute(task, fallback): OPEN 状态下返回 fallback 而非抛异常")
    void execute_withFallback_returnsFallbackWhenOpen() {
        CircuitBreaker breaker = CircuitBreaker.ofCount(1, Duration.ofSeconds(1));

        // 触发熔断
        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new RuntimeException("trigger");
        })).isInstanceOf(RuntimeException.class);

        // fallback 应被吞掉异常并返回
        String result = breaker.execute(() -> "should-not-be-called", "fallback-value");
        assertThat(result).isEqualTo("fallback-value");
    }

    @Test
    @Timeout(2)
    @DisplayName("execute(task, fallback): 任务本身抛异常时也返回 fallback")
    void execute_withFallback_returnsFallbackOnTaskException() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        // 不会触发熔断（仅 1 次失败），但任务异常被吞掉
        String result = breaker.execute(
                () -> {
                    throw new RuntimeException("inner-boom");
                },
                "safe-default");
        assertThat(result).isEqualTo("safe-default");
    }

    // ============================================================================================
    // 失败率模式
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("失败率模式：累计 10 次调用中 50% 失败即熔断")
    void failureRateMode_tripsAtThreshold() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failurePercent(50)
                .windowSize(10)
                .openDuration(Duration.ofSeconds(1))
                .build();

        // 5 次成功 + 5 次失败（50% 触发熔断）
        for (int i = 0; i < 5; i++) {
            try {
                breaker.execute(() -> "ok");
            } catch (Exception ignored) {
            }
        }
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class);
        }

        assertThat(breaker.state())
                .as("at 50% failure rate with 10-call window, breaker must trip to OPEN")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @Timeout(2)
    @DisplayName("失败率模式：累积样本不足时不熔断（冷启动保护）")
    void failureRateMode_respectsMinimumSampleSize() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failurePercent(50)
                .windowSize(100)
                .openDuration(Duration.ofSeconds(1))
                .build();

        // 5 次失败，未达最小样本阈值 10
        for (int i = 0; i < 5; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("boom");
                });
            } catch (Exception ignored) {
            }
        }

        assertThat(breaker.state())
                .as("with only 5 calls, MIN_SAMPLE_SIZE prevents premature tripping")
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @Timeout(2)
    @DisplayName("失败率模式：失败率不足阈值时不熔断")
    void failureRateMode_belowThreshold_remainsClosed() {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failurePercent(80)
                .windowSize(10)
                .openDuration(Duration.ofSeconds(1))
                .build();

        // 7 次成功 + 3 次失败（30% 失败，未达 80%）
        for (int i = 0; i < 7; i++) {
            try {
                breaker.execute(() -> "ok");
            } catch (Exception ignored) {
            }
        }
        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("boom");
                });
            } catch (Exception ignored) {
            }
        }

        assertThat(breaker.state())
                .as("at 30% failure rate with 80% threshold, breaker stays CLOSED")
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    // ============================================================================================
    // 时间窗口
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("时间窗口：窗口长度内的失败率触发熔断，窗口外的老数据被忽略")
    void timeBasedWindow_countsOnlyRecentFailures() throws Exception {
        AtomicLong fakeNanos = new AtomicLong(0L);
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failurePercent(50)
                .slidingWindowType(CircuitBreaker.SlidingWindowType.TIME_BASED)
                .windowDuration(Duration.ofSeconds(1))
                .windowSize(100)
                .openDuration(Duration.ofMillis(50))
                .build(fakeNanos::get);

        // T=0: 5 次失败 —— T=0 时刻
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class);
        }

        // 推进到 T=2s（窗口 1s 已过去，老数据已忽略）
        fakeNanos.set(TimeUnit.SECONDS.toNanos(2));

        // 新增 10 次成功
        for (int i = 0; i < 10; i++) {
            breaker.execute(() -> "ok");
        }

        assertThat(breaker.state())
                .as("recent failures are outside the window; recent successes dominate; stays CLOSED")
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @Timeout(5)
    @DisplayName("时间窗口：窗口长度内的失败率达到阈值触发熔断")
    void timeBasedWindow_tripsWhenFailuresInsideWindow() {
        AtomicLong fakeNanos = new AtomicLong(0L);
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failurePercent(50)
                .slidingWindowType(CircuitBreaker.SlidingWindowType.TIME_BASED)
                .windowDuration(Duration.ofSeconds(2))
                .windowSize(100)
                .openDuration(Duration.ofMillis(50))
                .build(fakeNanos::get);

        // T=0: 5 成功 + 6 失败（窗口内）
        for (int i = 0; i < 5; i++) {
            try {
                breaker.execute(() -> "ok");
            } catch (Exception ignored) {
            }
        }
        for (int i = 0; i < 6; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("boom");
                });
            } catch (Exception ignored) {
            }
        }

        // 状态推进
        fakeNanos.set(TimeUnit.MILLISECONDS.toNanos(10));

        assertThat(breaker.state())
                .as("failure rate within time window exceeds threshold; must trip to OPEN")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ============================================================================================
    // OPEN → HALF_OPEN → CLOSED 自动恢复
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("OPEN 状态持续时间到达后自动进入 HALF_OPEN，成功恢复为 CLOSED")
    void openToHalfOpenToClosed_automaticRecovery() throws Exception {
        AtomicLong fakeNanos = new AtomicLong(0L);
        AtomicInteger callCount = new AtomicInteger(0);

        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureCount(2)
                .openDuration(Duration.ofSeconds(1))
                .build(fakeNanos::get);

        // 触发熔断（2 次失败）
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new RuntimeException("trigger");
            })).isInstanceOf(RuntimeException.class);
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        // 推进到 T=1.5s（OPEN 持续时间已过，HALF_OPEN）
        fakeNanos.set(TimeUnit.MILLISECONDS.toNanos(1500));

        // 通过 doExecute 进入 HALF_OPEN 并试探成功
        String result = breaker.execute(() -> {
            callCount.incrementAndGet();
            return "downstream-ok";
        });

        assertThat(result).isEqualTo("downstream-ok");
        assertThat(breaker.state())
                .as("after successful probe, state returns to CLOSED")
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @Timeout(5)
    @DisplayName("OPEN 状态持续时间到达后自动进入 HALF_OPEN，失败再次进入 OPEN")
    void openToHalfOpen_failureGoesBackToOpen() {
        AtomicLong fakeNanos = new AtomicLong(0L);
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureCount(2)
                .openDuration(Duration.ofSeconds(1))
                .build(fakeNanos::get);

        // 触发熔断
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> breaker.execute(() -> {
                throw new RuntimeException("trigger");
            })).isInstanceOf(RuntimeException.class);
        }

        // 推进到 OPEN 已持续 1.5s
        fakeNanos.set(TimeUnit.MILLISECONDS.toNanos(1500));

        // 试探失败：再次进入 OPEN
        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new RuntimeException("still-down");
        })).isInstanceOf(RuntimeException.class);

        assertThat(breaker.state())
                .as("failed probe leaves state at OPEN (not CLOSED)")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @Timeout(5)
    @DisplayName("OPEN 状态持续时间未到时不会提前进入 HALF_OPEN")
    void openState_doesNotAdvancePrematurely() {
        AtomicLong fakeNanos = new AtomicLong(0L);
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failureCount(1)
                .openDuration(Duration.ofSeconds(2))
                .build(fakeNanos::get);

        // 触发熔断
        assertThatThrownBy(() -> breaker.execute(() -> {
            throw new RuntimeException("trigger");
        })).isInstanceOf(RuntimeException.class);

        // 推进到 OPEN 仅持续 500ms（远小于 2s）
        fakeNanos.set(TimeUnit.MILLISECONDS.toNanos(500));

        // 调用仍被拒绝
        assertThatThrownBy(() -> breaker.execute(() -> "downstream"))
                .isInstanceOf(CircuitBreakerOpenException.class);
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ============================================================================================
    // 手动控制
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("forceOpen: 手动触发 OPEN，立即拒绝后续调用")
    void forceOpen_rejectsSubsequentCalls() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        breaker.forceOpen();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> breaker.execute(() -> "downstream"))
                .isInstanceOf(CircuitBreakerOpenException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("reset: OPEN 状态强制重置为 CLOSED，清空统计")
    void reset_restoresClosedAndClearsCounters() {
        CircuitBreaker breaker = CircuitBreaker.ofCount(2, Duration.ofSeconds(1));
        for (int i = 0; i < 2; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("trigger");
                });
            } catch (Exception ignored) {
            }
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

        breaker.reset();

        assertThat(breaker.state())
                .as("reset must return to CLOSED")
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // 重置后再次失败 2 次又触发熔断，证明窗口已清空
        for (int i = 0; i < 2; i++) {
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("trigger-again");
                });
            } catch (Exception ignored) {
            }
        }
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ============================================================================================
    // Builder 校验
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("Builder: failurePercent 越界抛 IllegalArgumentException")
    void builder_failurePercentOutOfRangeThrows() {
        assertThatThrownBy(() -> CircuitBreaker.builder().failurePercent(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CircuitBreaker.builder().failurePercent(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("Builder: windowSize 过小抛 IllegalArgumentException")
    void builder_windowSizeTooSmallThrows() {
        assertThatThrownBy(() -> CircuitBreaker.builder().windowSize(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("Builder: 零或负的 openDuration 抛 IllegalArgumentException")
    void builder_openDurationInvalidThrows() {
        assertThatThrownBy(() -> CircuitBreaker.builder().openDuration(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CircuitBreaker.builder().openDuration(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("Builder: 链式调用可以构造复杂的失败率+自定义窗口配置")
    void builder_chainedConfigBuildsValidBreaker() throws Exception {
        CircuitBreaker breaker = CircuitBreaker.builder()
                .failurePercent(70)
                .windowSize(20)
                .openDuration(Duration.ofMillis(500))
                .build();
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        String result = breaker.execute(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    // ============================================================================================
    // 并发安全
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("并发：10 个线程同时调用，状态机在并发下正确触发并拒绝")
    void concurrentHammer_doesNotCorruptState() throws Exception {
        CircuitBreaker breaker = CircuitBreaker.ofCount(10, Duration.ofSeconds(1));
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CyclicBarrier barrier = new CyclicBarrier(10);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final boolean shouldFail = (i % 3 == 0);
                futures.add(pool.submit(() -> {
                    barrier.await();
                    try {
                        breaker.execute(() -> {
                            if (shouldFail) {
                                throw new RuntimeException("boom");
                            }
                            return "ok";
                        });
                        successes.incrementAndGet();
                    } catch (CircuitBreakerOpenException e) {
                        rejected.incrementAndGet();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                    return null;
                }));
            }
            for (var f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // 100 次中应有一部分被拒绝（触发 OPEN 后）
        assertThat(rejected.get())
                .as("after threshold failures, OPEN state must reject calls without invoking downstream")
                .isGreaterThan(0);
        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(successes.get() + rejected.get() + failures.get())
                .as("all 100 calls must be accounted for")
                .isEqualTo(100);
    }

    @Test
    @Timeout(5)
    @DisplayName("并发：OPEN 后并发调用 fallback 不应触发下游服务")
    void concurrent_openStateFallbacks_invokeOnce() throws Exception {
        CircuitBreaker breaker = CircuitBreaker.ofCount(1, Duration.ofSeconds(1));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger downstreamInvocations = new AtomicInteger();
        AtomicInteger fallbackHits = new AtomicInteger();
        try {
            // 第一次：触发熔断
            try {
                breaker.execute(() -> {
                    throw new RuntimeException("trigger");
                });
            } catch (Exception ignored) {
            }
            assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);

            CountDownLatch start = new CountDownLatch(1);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await();
                        String result = breaker.execute(() -> {
                            downstreamInvocations.incrementAndGet();
                            return "downstream";
                        }, "fallback");
                        if ("fallback".equals(result)) {
                            fallbackHits.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var f : futures) {
                f.get(2, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(downstreamInvocations.get())
                .as("OPEN state must never invoke downstream regardless of concurrent callers")
                .isZero();
        assertThat(fallbackHits.get())
                .as("all OPEN-state calls must hit the fallback")
                .isEqualTo(20);
    }

    // ============================================================================================
    // execute/状态机边界
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("executeOrThrow: 行为与 execute 相同（OPEN 状态抛出异常）")
    void executeOrThrow_openStateThrows() {
        CircuitBreaker breaker = CircuitBreaker.ofCount(1, Duration.ofSeconds(1));
        try {
            breaker.executeOrThrow(() -> {
                throw new RuntimeException("trigger");
            });
        } catch (Exception ignored) {
        }
        assertThatThrownBy(() -> breaker.executeOrThrow(() -> "downstream"))
                .isInstanceOf(CircuitBreakerOpenException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("execute: 任务正常返回时下游被调用一次")
    void execute_invokesDownstreamExactlyOnce() throws Exception {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        AtomicInteger callCount = new AtomicInteger();
        String result = breaker.execute(() -> {
            callCount.incrementAndGet();
            return "downstream-result";
        });
        assertThat(result).isEqualTo("downstream-result");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @Timeout(2)
    @DisplayName("execute: null 任务抛 NullPointerException")
    void execute_nullTaskThrows() {
        CircuitBreaker breaker = CircuitBreaker.ofDefaults();
        assertThatThrownBy(() -> breaker.execute((Callable<String>) null))
                .isInstanceOf(NullPointerException.class);
    }
}
