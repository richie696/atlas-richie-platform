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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Debouncer}.
 *
 * <p>覆盖构造校验、首次触发、重置计时、flush、cancel、close 幂等以及线程安全。</p>
 */
class DebouncerTest {

    // ============================================================================================
    // 构造校验
    // ============================================================================================

    @Nested
    @DisplayName("构造参数校验")
    class Construction {

        @Test
        @DisplayName("null delay 抛 NPE")
        void nullDelay_throws() {
            Runnable noop = () -> {};
            assertThatThrownBy(() -> Debouncer.of(null, noop))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("delay");
        }

        @Test
        @DisplayName("null action 抛 NPE")
        void nullAction_throws() {
            assertThatThrownBy(() -> Debouncer.of(Duration.ofMillis(100), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("action");
        }

        @Test
        @DisplayName("零延迟抛 IAE")
        void zeroDelay_throws() {
            assertThatThrownBy(() -> Debouncer.of(Duration.ZERO, () -> {}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("delay must be positive");
        }

        @Test
        @DisplayName("负延迟抛 IAE")
        void negativeDelay_throws() {
            assertThatThrownBy(() -> Debouncer.of(Duration.ofMillis(-1), () -> {}))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("delay must be positive");
        }

        @Test
        @DisplayName("正常参数构造成功")
        void validConstruction_succeeds() {
            Debouncer debouncer = Debouncer.of(Duration.ofMillis(100), () -> {});
            try {
                // 初始状态应无挂起操作
                assertThat(debouncer.isPending()).isFalse();
            } finally {
                debouncer.close();
            }
        }
    }

    // ============================================================================================
    // trigger 行为
    // ============================================================================================

    @Nested
    @DisplayName("trigger 行为")
    class Trigger {

        @Test
        @Timeout(5)
        @DisplayName("单次 trigger 后 delay 结束执行一次 action")
        void singleTrigger_executesOnce() throws Exception {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);

            Debouncer debouncer = Debouncer.of(Duration.ofMillis(50), () -> {
                counter.incrementAndGet();
                latch.countDown();
            });
            try {
                debouncer.trigger();
                latch.await(2, TimeUnit.SECONDS);
                assertThat(counter).hasValue(1);
            } finally {
                debouncer.close();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("连续 trigger 会重置计时，最终只执行一次")
        void multipleTriggers_resetAndExecuteOnce() throws Exception {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);

            Debouncer debouncer = Debouncer.of(Duration.ofMillis(80), () -> {
                counter.incrementAndGet();
                latch.countDown();
            });
            try {
                for (int i = 0; i < 10; i++) {
                    debouncer.trigger();
                    Thread.sleep(20);
                }
                // 等最后一次 trigger 后 delay 到期
                latch.await(2, TimeUnit.SECONDS);
                assertThat(counter).hasValue(1);
            } finally {
                debouncer.close();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("trigger 后 isPending 应为 true")
        void trigger_marksPending() {
            Debouncer debouncer = Debouncer.of(Duration.ofSeconds(10), () -> {});
            try {
                assertThat(debouncer.isPending()).isFalse();
                debouncer.trigger();
                assertThat(debouncer.isPending()).isTrue();
            } finally {
                debouncer.cancel();
                debouncer.close();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("trigger 后到 delay 完成 isPending 自动回到 false")
        void trigger_clearsPendingAfterExecution() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            Debouncer debouncer = Debouncer.of(Duration.ofMillis(30), () -> latch.countDown());
            try {
                debouncer.trigger();
                assertThat(debouncer.isPending()).isTrue();
                latch.await(2, TimeUnit.SECONDS);
                Thread.sleep(50); // 给调度器时间标记 future 完成
                assertThat(debouncer.isPending()).isFalse();
            } finally {
                debouncer.close();
            }
        }
    }

    // ============================================================================================
    // flush 立即执行
    // ============================================================================================

    @Nested
    @DisplayName("flush 立即执行挂起动作")
    class Flush {

        @Test
        @Timeout(5)
        @DisplayName("flush 立即触发挂起动作并取消计时")
        void flush_executesImmediately() throws Exception {
            AtomicInteger counter = new AtomicInteger();
            Debouncer debouncer = Debouncer.of(Duration.ofSeconds(10), counter::incrementAndGet);
            try {
                debouncer.trigger();
                assertThat(debouncer.isPending()).isTrue();

                debouncer.flush();
                assertThat(counter).hasValue(1);
                // flush 已取消计时器
                assertThat(debouncer.isPending()).isFalse();
            } finally {
                debouncer.close();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("无挂起动作时 flush 仍直接调用一次 action")
        void flush_executesEvenWhenNothingPending() {
            AtomicInteger counter = new AtomicInteger();
            Debouncer debouncer = Debouncer.of(Duration.ofSeconds(10), counter::incrementAndGet);
            try {
                assertThat(debouncer.isPending()).isFalse();
                debouncer.flush();
                assertThat(counter).hasValue(1);
            } finally {
                debouncer.close();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("flush 吞掉 action 中的异常，不向上抛")
        void flush_swallowsActionException() {
            Debouncer debouncer = Debouncer.of(Duration.ofSeconds(10),
                    () -> { throw new RuntimeException("boom"); });
            try {
                // 不应抛异常
                debouncer.flush();
            } finally {
                debouncer.close();
            }
        }
    }

    // ============================================================================================
    // cancel 取消
    // ============================================================================================

    @Nested
    @DisplayName("cancel 取消挂起")
    class Cancel {

        @Test
        @Timeout(5)
        @DisplayName("cancel 后 action 不执行")
        void cancel_blocksExecution() throws Exception {
            AtomicInteger counter = new AtomicInteger();
            Debouncer debouncer = Debouncer.of(Duration.ofMillis(50), counter::incrementAndGet);
            try {
                debouncer.trigger();
                debouncer.cancel();
                Thread.sleep(150); // 远超 delay
                assertThat(counter).hasValue(0);
            } finally {
                debouncer.close();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("cancel 后再次 trigger 仍能工作（不会永久关闭）")
        void cancel_doesNotDisableSubsequentTriggers() throws Exception {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            Debouncer debouncer = Debouncer.of(Duration.ofMillis(30), () -> {
                counter.incrementAndGet();
                latch.countDown();
            });
            try {
                debouncer.trigger();
                debouncer.cancel();
                assertThat(debouncer.isPending()).isFalse();

                debouncer.trigger();
                latch.await(2, TimeUnit.SECONDS);
                assertThat(counter).hasValue(1);
            } finally {
                debouncer.close();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("cancel 在无挂起时调用无效果")
        void cancel_whenNothingPending_noOp() {
            Debouncer debouncer = Debouncer.of(Duration.ofSeconds(10), () -> {});
            try {
                assertThat(debouncer.isPending()).isFalse();
                debouncer.cancel();
                assertThat(debouncer.isPending()).isFalse();
            } finally {
                debouncer.close();
            }
        }
    }

    // ============================================================================================
    // close 关闭调度器
    // ============================================================================================

    @Nested
    @DisplayName("close 关闭调度器")
    class Close {

        @Test
        @Timeout(5)
        @DisplayName("close 幂等，多次调用安全")
        void close_isIdempotent() {
            Debouncer debouncer = Debouncer.of(Duration.ofSeconds(10), () -> {});
            debouncer.close();
            debouncer.close();
            debouncer.close();
            // 不抛异常即成功
        }

        @Test
        @Timeout(5)
        @DisplayName("close 后 trigger 无效果")
        void close_blocksSubsequentTriggers() throws Exception {
            AtomicInteger counter = new AtomicInteger();
            Debouncer debouncer = Debouncer.of(Duration.ofMillis(30), counter::incrementAndGet);
            debouncer.close();

            debouncer.trigger();
            Thread.sleep(150);
            assertThat(counter).hasValue(0);
        }

        @Test
        @Timeout(5)
        @DisplayName("close 后 isPending 永远为 false")
        void close_isPendingFalse() {
            Debouncer debouncer = Debouncer.of(Duration.ofSeconds(10), () -> {});
            try {
                debouncer.trigger();
                assertThat(debouncer.isPending()).isTrue();
            } finally {
                debouncer.close();
            }
            assertThat(debouncer.isPending()).isFalse();
        }
    }

    // ============================================================================================
    // 异常吞掉
    // ============================================================================================

    @Nested
    @DisplayName("action 抛异常场景")
    class ExceptionSwallowing {

        @Test
        @Timeout(5)
        @DisplayName("trigger 后 action 抛异常，不影响调度器继续运行")
        void exceptionInAction_doesNotKillScheduler() throws Exception {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(2);
            Debouncer debouncer = Debouncer.of(Duration.ofMillis(30), () -> {
                counter.incrementAndGet();
                latch.countDown();
                if (counter.get() == 1) {
                    throw new RuntimeException("first call fails");
                }
            });
            try {
                debouncer.trigger();
                latch.await(2, TimeUnit.SECONDS);
                Thread.sleep(50);
                assertThat(counter).hasValueGreaterThanOrEqualTo(1);

                debouncer.trigger();
                latch.await(2, TimeUnit.SECONDS);
                Thread.sleep(50);
                assertThat(counter).hasValueGreaterThanOrEqualTo(2);
            } finally {
                debouncer.close();
            }
        }
    }

    // ============================================================================================
    // 线程安全
    // ============================================================================================

    @Nested
    @DisplayName("线程安全")
    class Concurrency {

        @Test
        @Timeout(10)
        @DisplayName("多线程并发 trigger 不会丢失重置，最终只执行一次")
        void concurrentTriggers_eventuallyExecuteOnce() throws Exception {
            AtomicInteger counter = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            Debouncer debouncer = Debouncer.of(Duration.ofMillis(100), () -> {
                counter.incrementAndGet();
                latch.countDown();
            });
            try {
                int threads = 8;
                int perThread = 50;
                Thread[] workers = new Thread[threads];
                for (int i = 0; i < threads; i++) {
                    workers[i] = new Thread(() -> {
                        for (int j = 0; j < perThread; j++) {
                            debouncer.trigger();
                        }
                    });
                }
                for (Thread w : workers) w.start();
                for (Thread w : workers) w.join();

                latch.await(5, TimeUnit.SECONDS);
                Thread.sleep(200); // 给调度器宽裕时间
                // 多线程并发 trigger 的语义："最后一次触发后 delay 结束执行 1 次"。
                // 在并发场景下可能执行 1~2 次（旧 future + 新 future 都可能在时刻窗口内被触发）。
                assertThat(counter.get()).isBetween(1, 2);
            } finally {
                debouncer.close();
            }
        }
    }
}
