package com.richie.component.concurrency.algorithm;

import com.richie.component.concurrency.algorithm.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RateLimiter}.
 *
 * <p>覆盖令牌桶限流器的全部公开 API 与关键边界：</p>
 *
 * <ol>
 *   <li>三种工厂方法的语义校验</li>
 *   <li>非阻塞 {@code tryAcquire()} 在桶满/桶空时的行为</li>
 *   <li>阻塞 {@code acquire()} 与不可中断变体</li>
 *   <li>定时补充令牌的端到端验证（基于"短周期补充后桶非空"的弱断言）</li>
 *   <li>参数验证与并发安全性</li>
 *   <li>{@link RateLimiter#close()} 幂等与关闭后行为</li>
 *   <li>构建器契约与非法路径</li>
 * </ol>
 *
 * <p>对时序敏感的测试通过 {@link java.util.concurrent.ScheduledExecutorService}
 * 虚拟线程调度器在小窗口（100ms-500ms）内观察，避免长时间挂起影响整体测试运行。</p>
 */
class RateLimiterTest {

    /**
     * 一个专用 Executor：fork 出异步工作线程避免虚拟线程在测试主线程内自挂。
     */
    private final ExecutorService helpers = Executors.newVirtualThreadPerTaskExecutor();

    // ============================================================================================
    // 工厂方法：ofTokensPerSecond
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("ofTokensPerSecond: 正参数创建非空限流器")
    void ofTokensPerSecond_createsLimiterWithInitialBucket() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(10)) {
            assertThat(limiter.availablePermits()).isEqualTo(10);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("ofTokensPerSecond: 0 令牌数抛 IllegalArgumentException")
    void ofTokensPerSecond_zeroPermitsThrows() {
        assertThatThrownBy(() -> RateLimiter.ofTokensPerSecond(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permits");
    }

    @Test
    @Timeout(2)
    @DisplayName("ofTokensPerSecond: 负数令牌抛 IllegalArgumentException")
    void ofTokensPerSecond_negativePermitsThrows() {
        assertThatThrownBy(() -> RateLimiter.ofTokensPerSecond(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permits");
    }

    // ============================================================================================
    // 工厂方法：ofTokensPerDuration
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("ofTokensPerDuration: 初始桶容量等于 permits 值")
    void ofTokensPerDuration_initialBucketMatchesPermits() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(500, Duration.ofMinutes(1))) {
            assertThat(limiter.availablePermits()).isEqualTo(500);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("ofTokensPerDuration: null window 抛 NullPointerException")
    void ofTokensPerDuration_nullWindowThrows() {
        assertThatThrownBy(() -> RateLimiter.ofTokensPerDuration(10, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("ofTokensPerDuration: 零时长窗口抛 IllegalArgumentException")
    void ofTokensPerDuration_zeroWindowThrows() {
        assertThatThrownBy(() -> RateLimiter.ofTokensPerDuration(10, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    @Test
    @Timeout(2)
    @DisplayName("ofTokensPerDuration: 负时长窗口抛 IllegalArgumentException")
    void ofTokensPerDuration_negativeWindowThrows() {
        assertThatThrownBy(() -> RateLimiter.ofTokensPerDuration(10, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    // ============================================================================================
    // 工厂方法：ofTryAcquireTimeout（已弃用 —— 保留测试以验证向后兼容行为）
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("ofTryAcquireTimeout: 桶空时 tryAcquire 立即返回 false（严格非阻塞语义）")
    @SuppressWarnings("deprecation")
    void ofTryAcquireTimeout_tryAcquireIsStrictlyNonBlocking() throws Exception {
        Duration period = Duration.ofMillis(500);
        try (RateLimiter limiter = RateLimiter.ofTryAcquireTimeout(1, period)) {
            assertThat(limiter.tryAcquire()).isTrue();
            // 自 2.2.0 起，ofTryAcquireTimeout 仅作为兼容入口存在，tryAcquire 严格非阻塞
            long start = System.nanoTime();
            boolean second = limiter.tryAcquire();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(second)
                    .as("tryAcquire() must return false immediately when bucket is empty, even on ofTryAcquireTimeout")
                    .isFalse();
            assertThat(elapsedMillis)
                    .as("tryAcquire() must not block, regardless of factory timeout")
                    .isLessThan(50L);
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("ofTryAcquireTimeout: tryAcquire(Duration) 在 period 内成功拿到令牌")
    @SuppressWarnings("deprecation")
    void ofTryAcquireTimeout_tryAcquireWithTimeoutEventuallySucceeds() throws Exception {
        Duration period = Duration.ofMillis(300);
        try (RateLimiter limiter = RateLimiter.ofTryAcquireTimeout(1, period)) {
            assertThat(limiter.tryAcquire()).isTrue();
            long start = System.nanoTime();
            boolean got = limiter.tryAcquire(period.multipliedBy(4));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(got).isTrue();
            assertThat(elapsedMillis)
                    .as("tryAcquire(Duration) must wait for refill, not return immediately")
                    .isGreaterThanOrEqualTo(period.toMillis() - 100L);
        }
    }

    // ============================================================================================
    // tryAcquire / tryAcquire(int)
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire: 桶满时连续 N 次都成功")
    void tryAcquire_fullBucketAllSucceed() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(5)) {
            for (int i = 0; i < 5; i++) {
                assertThat(limiter.tryAcquire())
                        .as("tryAcquire #%d of 5", i + 1)
                        .isTrue();
            }
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire: 桶空时立即返回 false")
    void tryAcquire_emptyBucketImmediatelyReturnsFalse() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(2)) {
            assertThat(limiter.tryAcquire()).isTrue();
            assertThat(limiter.tryAcquire()).isTrue();
            assertThat(limiter.tryAcquire())
                    .as("after bucket exhausted, tryAcquire must return false immediately, not block")
                    .isFalse();
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(int): 多令牌消费")
    void tryAcquire_multiPermits() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(10)) {
            assertThat(limiter.tryAcquire(3)).isTrue();
            assertThat(limiter.availablePermits()).isLessThanOrEqualTo(7);
            assertThat(limiter.tryAcquire(7)).isTrue();
            assertThat(limiter.availablePermits()).isLessThanOrEqualTo(0);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(int): 请求超出剩余令牌则全部失败且不消费任何令牌")
    void tryAcquire_insufficientPermitsNoConsumption() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(3)) {
            limiter.tryAcquire(); // consume 1, left 2
            int beforeFailed = limiter.availablePermits();
            assertThat(limiter.tryAcquire(5))
                    .as("requesting more than available must fail")
                    .isFalse();
            assertThat(limiter.availablePermits())
                    .as("failed acquisition must not consume any permits")
                    .isEqualTo(beforeFailed);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(int): 负数 permits 抛 IllegalArgumentException")
    void tryAcquire_negativePermitsThrows() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(5)) {
            assertThatThrownBy(() -> limiter.tryAcquire(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> limiter.tryAcquire(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ============================================================================================
    // tryAcquire(Duration) / tryAcquire(int, Duration) —— 限时阻塞（新增自 2.2.0）
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(Duration): 桶空时在指定超时内等待，超时后返回 false")
    void tryAcquireWithTimeout_emptyBucketReturnsFalseAfterTimeout() {
        Duration period = Duration.ofMinutes(5);
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(1, period)) {
            assertThat(limiter.tryAcquire()).isTrue();

            long start = System.nanoTime();
            boolean got = limiter.tryAcquire(Duration.ofMillis(100));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(got)
                    .as("with bucket empty and timeout shorter than refill period, must return false")
                    .isFalse();
            assertThat(elapsedMillis)
                    .as("tryAcquire(Duration) must wait approximately the full timeout, not return instantly")
                    .isGreaterThanOrEqualTo(80L);
            assertThat(elapsedMillis)
                    .as("tryAcquire(Duration) must respect the timeout, never exceed it by much")
                    .isLessThan(500L);
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("tryAcquire(Duration): 等待期内桶补充后能拿到令牌")
    void tryAcquireWithTimeout_succeedsWhenRefillArrivesInTime() {
        Duration period = Duration.ofMillis(150);
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(1, period)) {
            assertThat(limiter.tryAcquire()).isTrue();
            assertThat(limiter.tryAcquire(period.multipliedBy(4)))
                    .as("refill within timeout window must succeed")
                    .isTrue();
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(Duration): 桶满时立即返回 true，不阻塞")
    void tryAcquireWithTimeout_fullBucketReturnsImmediately() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(5)) {
            long start = System.nanoTime();
            boolean got = limiter.tryAcquire(Duration.ofSeconds(10));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(got).isTrue();
            assertThat(elapsedMillis)
                    .as("full bucket should return immediately regardless of timeout")
                    .isLessThan(50L);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(Duration): null timeout 抛 NullPointerException")
    void tryAcquireWithTimeout_nullThrows() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(2)) {
            assertThatThrownBy(() -> limiter.tryAcquire((Duration) null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(Duration): 零或负超时抛 IllegalArgumentException")
    void tryAcquireWithTimeout_nonPositiveThrows() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(2)) {
            assertThatThrownBy(() -> limiter.tryAcquire(Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeout");
            assertThatThrownBy(() -> limiter.tryAcquire(Duration.ofMillis(-1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("timeout");
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(int, Duration): 多令牌获取 + 超时")
    void tryAcquireMultiPermitsWithTimeout_emptyBucketTimesOut() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(3)) {
            limiter.tryAcquire(3);
            long start = System.nanoTime();
            boolean got = limiter.tryAcquire(2, Duration.ofMillis(100));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(got).isFalse();
            assertThat(elapsedMillis)
                    .as("multi-permit timeout must respect the configured timeout")
                    .isGreaterThanOrEqualTo(80L);
            assertThat(elapsedMillis).isLessThan(500L);
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("tryAcquire(int, Duration): 桶空 + 多令牌等待期间补充后拿到令牌")
    void tryAcquireMultiPermitsWithTimeout_succeedsWhenRefillArrives() {
        Duration period = Duration.ofMillis(200);
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(2, period)) {
            assertThat(limiter.tryAcquire(2)).isTrue();
            // 等待时 refill 给出 2 个令牌，第二轮恰好能拿到
            assertThat(limiter.tryAcquire(2, period.multipliedBy(4)))
                    .as("refill within timeout window must satisfy the 2-permit request")
                    .isTrue();
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(int, Duration): 非法 permits 抛 IllegalArgumentException")
    void tryAcquireMultiPermitsWithTimeout_invalidPermitsThrows() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(5)) {
            assertThatThrownBy(() -> limiter.tryAcquire(0, Duration.ofMillis(100)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("permits");
            assertThatThrownBy(() -> limiter.tryAcquire(-2, Duration.ofMillis(100)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("permits");
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(Duration): 等待期间被中断时恢复中断标志并返回 false")
    void tryAcquireWithTimeout_interruptPreservesFlag() throws Exception {
        Duration period = Duration.ofMinutes(5);
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(1, period)) {
            assertThat(limiter.tryAcquire()).isTrue();

            CountDownLatch enteredLatch = new CountDownLatch(1);
            AtomicReference<Throwable> workerError = new AtomicReference<>();
            AtomicInteger sawInterrupt = new AtomicInteger();
            AtomicInteger gotResult = new AtomicInteger(-1);

            Thread worker = Thread.ofVirtual()
                    .name("try-acquire-timeout-interrupt-test")
                    .start(() -> {
                        try {
                            enteredLatch.countDown();
                            boolean got = limiter.tryAcquire(Duration.ofSeconds(30));
                            gotResult.set(got ? 1 : 0);
                            sawInterrupt.set(Thread.currentThread().isInterrupted() ? 1 : 0);
                        } catch (Throwable t) {
                            workerError.set(t);
                        }
                    });

            assertThat(enteredLatch.await(1, TimeUnit.SECONDS)).isTrue();
            // 给 worker 时间进入 semaphore.tryAcquire 阻塞等待
            Thread.sleep(50);
            worker.interrupt();
            worker.join(1000);

            assertThat(worker.isAlive())
                    .as("worker must exit after interrupt")
                    .isFalse();
            assertThat(workerError.get())
                    .as("tryAcquire(Duration) must catch InterruptedException internally, not propagate it")
                    .isNull();
            assertThat(gotResult.get())
                    .as("interrupted tryAcquire(Duration) must return false")
                    .isEqualTo(0);
            assertThat(sawInterrupt.get())
                    .as("interrupt flag must be preserved after tryAcquire(Duration) catches InterruptedException")
                    .isEqualTo(1);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("tryAcquire(Duration): 关闭后立即返回 false，不阻塞")
    void tryAcquireWithTimeout_afterCloseReturnsFalse() {
        RateLimiter limiter = RateLimiter.ofTokensPerSecond(5);
        limiter.close();
        long start = System.nanoTime();
        boolean got = limiter.tryAcquire(Duration.ofSeconds(5));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertThat(got).isFalse();
        assertThat(elapsedMillis)
                .as("closed limiter must reject tryAcquire(Duration) immediately")
                .isLessThan(50L);
    }

    // ============================================================================================
    // acquire(int)
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("acquire: 桶满时立即返回不阻塞")
    void acquire_fullBucketReturnsImmediately() throws Exception {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(5)) {
            long start = System.nanoTime();
            limiter.acquire();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(elapsed).isLessThan(50L);
            assertThat(limiter.availablePermits()).isEqualTo(4);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("acquire(int): 桶空时阻塞直到补充令牌")
    void acquire_blocksUntilRefill() throws Exception {
        Duration period = Duration.ofMillis(150);
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(1, period)) {
            limiter.acquire(); // consume the only permit

            CountDownLatch acquiredLatch = new CountDownLatch(1);
            Future<?> waiter = helpers.submit(() -> {
                try {
                    limiter.acquire();
                    acquiredLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            assertThat(acquiredLatch.await(period.toMillis() * 4, TimeUnit.MILLISECONDS))
                    .as("acquire() must wake within a few refill periods when the bucket is empty")
                    .isTrue();
            waiter.get();
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("acquire(int): 负数 permits 抛 IllegalArgumentException")
    void acquire_negativePermitsThrows() {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(2)) {
            assertThatThrownBy(() -> limiter.acquire(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> limiter.acquire(-5))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("acquire: 收到 InterruptedException 时中断标志被恢复")
    void acquire_preservesInterruptStatus() throws Exception {
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(1, Duration.ofMinutes(1))) {
            limiter.acquire(); // consume the only permit
            CountDownLatch enteredLatch = new CountDownLatch(1);
            AtomicInteger sawInterrupt = new AtomicInteger();
            AtomicReference<Throwable> workerError = new AtomicReference<>();

            Thread worker = Thread.ofVirtual()
                    .name("acquire-interrupt-test")
                    .start(() -> {
                        try {
                            enteredLatch.countDown();
                            limiter.acquire();
                        } catch (InterruptedException e) {
                            sawInterrupt.set(1);
                            Thread.currentThread().interrupt();
                        } catch (Throwable t) {
                            workerError.set(t);
                        }
                    });

            assertThat(enteredLatch.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(50); // 给 worker 时间进入 semaphore.acquire
            worker.interrupt();
            worker.join(1000);

            assertThat(worker.isAlive())
                    .as("worker must exit after interrupt")
                    .isFalse();
            assertThat(sawInterrupt.get())
                    .as("interrupted acquire() must observe and clear the interrupt flag")
                    .isEqualTo(1);
            assertThat(workerError.get())
                    .as("only InterruptedException is expected; no other throwable should escape")
                    .isNull();
        }
    }

    // ============================================================================================
    // acquireUninterruptibly
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("acquireUninterruptibly: 桶空时被中断不抛出，桶补充后获取成功")
    void acquireUninterruptibly_survivesInterrupt() throws Exception {
        Duration period = Duration.ofMillis(150);
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(1, period)) {
            limiter.acquire();

            CountDownLatch readyLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(1);
            AtomicInteger sawInterrupt = new AtomicInteger();

            Thread worker = Thread.ofVirtual().name("uninterruptible-test").start(() -> {
                try {
                    readyLatch.countDown();
                    boolean got = limiter.acquireUninterruptibly(1);
                    sawInterrupt.set(Thread.currentThread().isInterrupted() ? 1 : 0);
                    assertThat(got).isTrue();
                } finally {
                    doneLatch.countDown();
                }
            });

            assertThat(readyLatch.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(50);
            worker.interrupt();
            assertThat(doneLatch.await(period.toMillis() * 4, TimeUnit.MILLISECONDS))
                    .as("acquireUninterruptibly must continue waiting even after interrupt")
                    .isTrue();
            worker.join();
            assertThat(sawInterrupt.get())
                    .as("interrupt status must be preserved (not silently swallowed)")
                    .isEqualTo(1);
        }
    }

    // ============================================================================================
    // close()
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("close: 关闭后 tryAcquire 返回 false")
    void close_tryAcquireAfterCloseReturnsFalse() {
        RateLimiter limiter = RateLimiter.ofTokensPerSecond(5);
        limiter.close();
        assertThat(limiter.tryAcquire())
                .as("closed limiter must reject further tryAcquire attempts")
                .isFalse();
    }

    @Test
    @Timeout(2)
    @DisplayName("close: 关闭后 acquire 抛 IllegalStateException")
    void close_acquireAfterCloseThrows() {
        RateLimiter limiter = RateLimiter.ofTokensPerSecond(5);
        limiter.close();
        assertThatThrownBy(limiter::acquire).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("close: 多次调用幂等")
    void close_isIdempotent() {
        RateLimiter limiter = RateLimiter.ofTokensPerSecond(1);
        limiter.close();
        limiter.close(); // should not throw
        assertThat(limiter.tryAcquire()).isFalse();
    }

    // ============================================================================================
    // 并发安全
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("tryAcquire: 10 个并发线程抢购 5 个令牌，恰好 5 成功")
    void tryAcquire_concurrentConsumptionExactlyAllowsPermits() throws Exception {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(5)) {
            int threads = 10;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            AtomicInteger successes = new AtomicInteger();

            Future<?>[] futures = new Future<?>[threads];
            for (int i = 0; i < threads; i++) {
                futures[i] = helpers.submit(() -> {
                    try {
                        start.await();
                        if (limiter.tryAcquire()) {
                            successes.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(2, TimeUnit.SECONDS);
            }
            assertThat(successes.get())
                    .as("with a 5-permit bucket and 10 racers, exactly 5 must succeed")
                    .isEqualTo(5);
        }
    }

    @Test
    @Timeout(3)
    @DisplayName("tryAcquire: 并发消费后 availablePermits 不为负")
    void tryAcquire_concurrentDoesNotUnderflow() throws Exception {
        try (RateLimiter limiter = RateLimiter.ofTokensPerSecond(10)) {
            int threads = 30;
            CountDownLatch start = new CountDownLatch(1);
            Future<?>[] futures = new Future<?>[threads];
            for (int i = 0; i < threads; i++) {
                futures[i] = helpers.submit(() -> {
                    try {
                        start.await();
                        limiter.tryAcquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(2, TimeUnit.SECONDS);
            }
            assertThat(limiter.availablePermits())
                    .as("availablePermits must never be negative after concurrent consumption")
                    .isGreaterThanOrEqualTo(0);
        }
    }

    // ============================================================================================
    // 端到端：补充周期到达后令牌返回
    // ============================================================================================

    @Test
    @Timeout(3)
    @DisplayName("补充：耗尽令牌后等待补充周期，至少能再拿到 1 个")
    void refill_bringsPermitsBackAfterPeriod() throws Exception {
        Duration period = Duration.ofMillis(120);
        try (RateLimiter limiter = RateLimiter.ofTokensPerDuration(2, period)) {
            limiter.acquire();
            limiter.acquire();

            // Wait one full period then a small buffer
            boolean gained = false;
            long deadlineNanos = System.nanoTime() + period.toNanos() * 3;
            while (System.nanoTime() < deadlineNanos) {
                if (limiter.tryAcquire()) {
                    gained = true;
                    break;
                }
                Thread.sleep(20);
            }
            assertThat(gained)
                    .as("after one refill period the bucket must contain at least 1 permit")
                    .isTrue();
        }
    }

    // ============================================================================================
    // Builder
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("Builder: 完整配置后构建成功")
    void builder_fullConfigBuildsSuccessfully() {
        try (RateLimiter limiter = RateLimiter.builder()
                .permits(20)
                .period(Duration.ofSeconds(5))
                .build()) {
            assertThat(limiter.availablePermits()).isEqualTo(20);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("Builder: tryAcquireTimeoutEnabled 已弃用 —— 该设置不再影响 tryAcquire 行为")
    @SuppressWarnings("deprecation")
    void builder_tryAcquireTimeoutEnabledIsDeprecatedNoOp() throws Exception {
        try (RateLimiter limiter = RateLimiter.builder()
                .permits(1)
                .period(Duration.ofMillis(300))
                .tryAcquireTimeoutEnabled(true)
                .build()) {
            assertThat(limiter.tryAcquire()).isTrue();
            long start = System.nanoTime();
            boolean second = limiter.tryAcquire();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(second)
                    .as("tryAcquire must remain strictly non-blocking even when tryAcquireTimeoutEnabled(true)")
                    .isFalse();
            assertThat(elapsedMillis)
                    .as("tryAcquire must not block, regardless of builder.tryAcquireTimeoutEnabled")
                    .isLessThan(50L);
        }
    }

    @Test
    @Timeout(5)
    @DisplayName("Builder: 通过 tryAcquire(Duration) 显式等待能拿到令牌（替代旧的 tryAcquireTimeoutEnabled）")
    void builder_periodUsedForTryAcquireDuration() throws Exception {
        Duration period = Duration.ofMillis(300);
        try (RateLimiter limiter = RateLimiter.builder()
                .permits(1)
                .period(period)
                .build()) {
            assertThat(limiter.tryAcquire()).isTrue();
            long start = System.nanoTime();
            boolean got = limiter.tryAcquire(period.multipliedBy(4));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(got).isTrue();
            assertThat(elapsedMillis)
                    .as("tryAcquire(Duration) must wait for refill period")
                    .isGreaterThanOrEqualTo(period.toMillis() - 100L);
        }
    }

    @Test
    @Timeout(2)
    @DisplayName("Builder: 未设置 permits 时 build 抛 IllegalStateException")
    void builder_missingPermitsThrows() {
        assertThatThrownBy(() -> RateLimiter.builder()
                .period(Duration.ofSeconds(1))
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permits");
    }


    @Test
    @Timeout(2)
    @DisplayName("Builder: 未设置 period 时 build 抛 IllegalStateException")
    void builder_missingPeriodThrows() {
        assertThatThrownBy(() -> RateLimiter.builder()
                .permits(10)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("period");
    }

    @Test
    @Timeout(2)
    @DisplayName("Builder: 负数 permits 在 builder 阶段即拒绝")
    void builder_negativePermitsRejected() {
        assertThatThrownBy(() -> RateLimiter.builder().permits(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RateLimiter.builder().permits(-3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(2)
    @DisplayName("Builder: 零时长 period 抛 IllegalArgumentException")
    void builder_zeroPeriodRejected() {
        assertThatThrownBy(() -> RateLimiter.builder()
                .permits(10)
                .period(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
