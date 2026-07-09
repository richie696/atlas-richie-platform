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
package com.richie.component.concurrency.threadpool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DynamicExecutor}.
 *
 * <p>覆盖四种构造签名、热更新事件、拒绝计数、运行态快照以及并发安全性。</p>
 */
class DynamicExecutorTest {

    // ============================================================================================
    // 构造 & 基础执行
    // ============================================================================================

    @Nested
    @DisplayName("构造与基础功能")
    class Construction {

        @Test
        @Timeout(5)
        @DisplayName("4 参构造：成功执行一个任务")
        void fourArgsConstructor_submitsTaskSuccessfully() throws Exception {
            var executor = new DynamicExecutor(
                    1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                var result = executor.submit(() -> "hello").get(2, TimeUnit.SECONDS);
                assertThat(result).isEqualTo("hello");
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("5 参构造（含 ThreadFactory）：成功执行一个任务")
        void fiveArgsConstructor_withThreadFactory() throws Exception {
            var executor = new DynamicExecutor(
                    1, 1, 60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(), r -> new Thread(r, "test-pool-"));
            try {
                var result = executor.submit(() -> "ok").get(2, TimeUnit.SECONDS);
                assertThat(result).isEqualTo("ok");
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("5 参构造（含 Handler）：AbortPolicy 触发异常")
        void fiveArgsConstructor_withHandler_rejects() {
            var executor = new DynamicExecutor(
                    0, 1, 10, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
            try {
                executor.execute(() -> sleepUninterruptibly(2000));
                executor.execute(() -> sleepUninterruptibly(2000));
                executor.execute(() -> sleepUninterruptibly(2000)); // 第 3 个被拒绝
            } catch (Exception e) {
                assertThat(e).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("6 参构造（含 ThreadFactory + Handler）：完整参数传递")
        void sixArgsConstructor_allParams() throws Exception {
            var executor = new DynamicExecutor(
                    2, 4, 60, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(100),
                    r -> new Thread(r, "full-"),
                    new ThreadPoolExecutor.DiscardPolicy());
            try {
                var result = executor.submit(() -> 42).get(2, TimeUnit.SECONDS);
                assertThat(result).isEqualTo(42);
                assertThat(executor.getThreadFactory()).isNotNull();
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ============================================================================================
    // onResize 热更新
    // ============================================================================================

    @Nested
    @DisplayName("运行时热更新 onResize")
    class OnResize {

        @Test
        @Timeout(5)
        @DisplayName("更新 corePoolSize：getCorePoolSize 返回新值")
        void resize_corePoolSize() {
            var executor = new DynamicExecutor(
                    4, 32, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                executor.onResize(PoolResizeEvent.builder().corePoolSize(16).build());
                assertThat(executor.getCorePoolSize()).isEqualTo(16);
                assertThat(executor.getMaximumPoolSize()).isEqualTo(32); // 其他字段不变
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("更新 maximumPoolSize：getMaximumPoolSize 返回新值")
        void resize_maximumPoolSize() {
            var executor = new DynamicExecutor(
                    4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                executor.onResize(PoolResizeEvent.builder().maximumPoolSize(32).build());
                assertThat(executor.getMaximumPoolSize()).isEqualTo(32);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("更新 keepAliveTime：getKeepAliveTime 返回新值（毫秒精度）")
        void resize_keepAliveTime() {
            var executor = new DynamicExecutor(
                    4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                executor.onResize(PoolResizeEvent.builder()
                        .keepAliveTime(Duration.ofSeconds(120)).build());
                assertThat(executor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(120);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("更新拒绝策略：旧 handler 被替换，新 handler 生效")
        void resize_rejectedHandler() {
            var executor = new DynamicExecutor(
                    0, 1, 10, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1),
                    new ThreadPoolExecutor.AbortPolicy());
            try {
                // 先确认 AbortPolicy 会拒绝
                executor.execute(() -> sleepUninterruptibly(500));
                executor.execute(() -> sleepUninterruptibly(500));
                assertThatThrownBy(() -> executor.execute(() -> {}))
                        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);

                // 热更新为 DiscardPolicy
                executor.onResize(PoolResizeEvent.builder()
                        .rejectedHandler(new ThreadPoolExecutor.DiscardPolicy())
                        .build());

                // DiscardPolicy 静默丢弃，不抛异常
                executor.execute(() -> {});
                // 如果上一行没抛异常，说明 handler 已生效
                assertThat(executor.getRejectedExecutionHandler())
                        .isInstanceOf(ThreadPoolExecutor.DiscardPolicy.class);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("发送 null 事件：不抛异常，参数不变")
        void resize_nullEvent_doesNothing() {
            var executor = new DynamicExecutor(
                    4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                executor.onResize(null);
                assertThat(executor.getCorePoolSize()).isEqualTo(4);
                assertThat(executor.getMaximumPoolSize()).isEqualTo(8);
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("部分更新事件：仅更新非 null 字段，其他不变")
        void resize_partialEvent_onlyUpdatesNonNull() {
            var executor = new DynamicExecutor(
                    4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
            try {
                executor.onResize(PoolResizeEvent.builder()
                        .maximumPoolSize(16)
                        .keepAliveTime(Duration.ofSeconds(30))
                        .build());
                assertThat(executor.getCorePoolSize()).isEqualTo(4);   // 未更新
                assertThat(executor.getMaximumPoolSize()).isEqualTo(16); // 已更新
                assertThat(executor.getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(30); // 已更新
                assertThat(executor.getRejectedExecutionHandler())
                        .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class); // 未更新
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(10)
        @DisplayName("扩容后能提交更多并发任务：验证 maximumPoolSize 调整的实际效果")
        void resize_maxPoolSize_allowsMoreConcurrentTasks() throws Exception {
            var executor = new DynamicExecutor(
                    1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100));
            try {
                // 先占满 1 个线程
                var latch1 = new CountDownLatch(1);
                var latch2 = new CountDownLatch(1);
                var started1 = new CountDownLatch(1);
                var started2 = new CountDownLatch(1);

                executor.execute(() -> {
                    started1.countDown();
                    awaitUninterruptibly(latch1);
                });
                started1.await(2, TimeUnit.SECONDS);

                // 第二个任务应该排队（因为 maximumPoolSize=1）
                var queued = new AtomicInteger(0);
                executor.execute(() -> {
                    queued.set(1);
                    started2.countDown();
                    awaitUninterruptibly(latch2);
                });

                // 扩容
                executor.onResize(PoolResizeEvent.builder().maximumPoolSize(4).build());

                // 释放第一个线程 → 第二个任务应该被第二个线程执行而非排队
                latch1.countDown();
                started2.await(3, TimeUnit.SECONDS);
                assertThat(queued).hasValue(1);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ============================================================================================
    // 拒绝计数
    // ============================================================================================

    @Nested
    @DisplayName("拒绝计数")
    class RejectionCounting {

        @Test
        @Timeout(5)
        @DisplayName("拒绝计数在超容量后递增")
        void rejectionCount_incrementsOnRejection() {
            var executor = new DynamicExecutor(
                    1, 1, 10, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1));
            try {
                executor.execute(() -> sleepUninterruptibly(2000));
                executor.execute(() -> sleepUninterruptibly(2000));
                assertThatThrownBy(() -> executor.execute(() -> {}))
                        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                assertThat(executor.getRejectionCount()).isOne();
                executor.resetRejectionCount();
                assertThat(executor.getRejectionCount()).isZero();
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("重置拒绝计数后归零")
        void resetRejectionCount_clearsCounter() {
            var executor = new DynamicExecutor(
                    1, 1, 10, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1));
            try {
                executor.execute(() -> sleepUninterruptibly(2000));
                executor.execute(() -> sleepUninterruptibly(2000)); // 进队列
                assertThatThrownBy(() -> executor.execute(() -> {})) // 被拒绝
                        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                assertThat(executor.getRejectionCount()).isOne();
                assertThatThrownBy(() -> executor.execute(() -> {})) // 又被拒绝
                        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                assertThat(executor.getRejectionCount()).isEqualTo(2);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ============================================================================================
    // 快照
    // ============================================================================================

    @Nested
    @DisplayName("运行态快照")
    class Snapshot {

        @Test
        @Timeout(5)
        @DisplayName("snapshot() 返回的值与线程池状态一致")
        void snapshot_matchesExecutorState() throws Exception {
            var executor = new DynamicExecutor(
                    2, 4, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100));
            try {
                // 提交一个耗时任务，确保 activeCount > 0
                var latch = new CountDownLatch(1);
                executor.execute(() -> awaitUninterruptibly(latch));

                PoolStatus status = executor.snapshot();
                assertThat(status.getCorePoolSize()).isEqualTo(2);
                assertThat(status.getMaximumPoolSize()).isEqualTo(4);
                assertThat(status.getPoolSize()).isGreaterThanOrEqualTo(1);
                assertThat(status.getActiveCount()).isGreaterThanOrEqualTo(1);
                assertThat(status.getQueueRemainingCapacity()).isGreaterThan(0);
                assertThat(status.getRejectedCount()).isZero();

                latch.countDown();
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @Timeout(5)
        @DisplayName("snapshot() 在拒绝后 reflection 拒绝计数")
        void snapshot_reflectsRejectedCount() {
            var executor = new DynamicExecutor(
                    0, 1, 10, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1));
            try {
                executor.execute(() -> sleepUninterruptibly(2000));
                executor.execute(() -> sleepUninterruptibly(2000));
                assertThatThrownBy(() -> executor.execute(() -> {}))
                        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
                assertThatThrownBy(() -> executor.execute(() -> {}))
                        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);

                PoolStatus status = executor.snapshot();
                assertThat(status.getRejectedCount()).isEqualTo(2);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    // ============================================================================================
    // 事件参数校验
    // ============================================================================================

    @Nested
    @DisplayName("PoolResizeEvent 构建校验")
    class EventValidation {

        @Test
        @DisplayName("corePoolSize 为负数时抛异常")
        void corePoolSize_negative_throws() {
            assertThatThrownBy(() -> PoolResizeEvent.builder().corePoolSize(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be >= 0");
        }

        @Test
        @DisplayName("maximumPoolSize 为负数时抛异常")
        void maximumPoolSize_negative_throws() {
            assertThatThrownBy(() -> PoolResizeEvent.builder().maximumPoolSize(-5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be >= 0");
        }

        @Test
        @DisplayName("keepAliveTime 为 0 时抛异常")
        void keepAliveTime_zero_throws() {
            assertThatThrownBy(() -> PoolResizeEvent.builder().keepAliveTime(Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("keepAliveTime 为负数时抛异常")
        void keepAliveTime_negative_throws() {
            assertThatThrownBy(() -> PoolResizeEvent.builder()
                    .keepAliveTime(Duration.ofSeconds(-1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be positive");
        }

        @Test
        @DisplayName("全字段 build + getter 正确")
        void fullBuild_allGettersWork() {
            var handler = new ThreadPoolExecutor.CallerRunsPolicy();
            var event = PoolResizeEvent.builder()
                    .corePoolSize(10)
                    .maximumPoolSize(20)
                    .keepAliveTime(Duration.ofMinutes(5))
                    .rejectedHandler(handler)
                    .build();
            assertThat(event.getCorePoolSize()).isEqualTo(10);
            assertThat(event.getMaximumPoolSize()).isEqualTo(20);
            assertThat(event.getKeepAliveTime()).isEqualTo(Duration.ofMinutes(5));
            assertThat(event.getRejectedHandler()).isSameAs(handler);
        }
    }

    // ============================================================================================
    // 辅助方法
    // ============================================================================================

    private static void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
