package com.richie.component.concurrency.algorithm;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 令牌桶限流器 —— 控制单位时间内的最大并发操作数。
 *
 * <p>在高并发场景中，保护下游服务（数据库、外部 API、消息队列等）不被瞬时洪流击垮是
 * 最常见的需求之一。本工具基于经典<b>令牌桶 (Token Bucket)</b> 算法实现，相比简单的
 * 计数器限流具有以下优势：</p>
 *
 * <ul>
 *   <li><b>允许突发：</b>桶内可累积令牌，应对短时突发流量更友好</li>
 *   <li><b>平滑限流：</b>以固定速率补充令牌，避免毛刺</li>
 *   <li><b>三档等待语义，按调用方需求自由选择：</b>
 *     <ul>
 *       <li>{@link #tryAcquire()} / {@link #tryAcquire(int)} —— <b>非阻塞</b>，立即返回</li>
 *       <li>{@link #tryAcquire(Duration)} / {@link #tryAcquire(int, Duration)} —— <b>限时阻塞</b>，超时返回</li>
 *       <li>{@link #acquire()} / {@link #acquire(int)} —— <b>无限阻塞</b>，挂起至拿到令牌</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>基本使用</h3>
 * <pre>{@code
 * // 1) 每秒补充 100 个令牌（突发最大 100 个/秒）
 * RateLimiter limiter = RateLimiter.ofTokensPerSecond(100);
 * if (limiter.tryAcquire()) {
 *     callExternalService();
 * }
 *
 * // 2) 60 秒内最多 1000 次（容量较大的令牌桶）
 * RateLimiter limiter = RateLimiter.ofTokensPerDuration(1000, Duration.ofMinutes(1));
 * limiter.acquire();
 *
 * // 3) 阻塞等待直到拿到令牌
 * try {
 *     limiter.acquire();
 *     handleRequest();
 * } catch (InterruptedException e) {
 *     Thread.currentThread().interrupt();
 * }
 *
 * // 4) 一次消费多个令牌（限流粒度可调）
 * limiter.tryAcquire(5);  // 一次拿 5 个令牌
 *
 * // 5) 限时等待：500ms 内拿不到令牌即放弃（适合 SLA 严苛场景）
 * if (limiter.tryAcquire(Duration.ofMillis(500))) {
 *     callExternalService();
 * } else {
 *     returnRateLimitedResponse();
 * }
 * }</pre>
 *
 * <h3>实现原理</h3>
 * <p>
 * 内部以 {@link Semaphore} 持有当前可用令牌，{@link ScheduledExecutorService}
 * 以固定速率定期释放令牌。当 {@link #close()} 被调用或 JVM 退出时调度器自动关闭。
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>所有方法均为线程安全，可在多线程环境中并发调用 {@link #tryAcquire()} /
 * {@link #acquire()}。{@link #availablePermits()} 返回近似值，不保证原子一致。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class RateLimiter implements AutoCloseable {

    private final Semaphore semaphore;
    private final int permitsPerRefill;
    private final ScheduledExecutorService scheduler;
    private final ScheduledFuture<?> refillerFuture;
    private final long tryAcquireTimeoutNanos;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private RateLimiter(int permits, Duration window, long tryAcquireTimeoutNanos) {
        this.permitsPerRefill = permits;
        this.semaphore = new Semaphore(permits);
        this.tryAcquireTimeoutNanos = tryAcquireTimeoutNanos;
        // 使用虚拟线程调度器；单线程定期补充令牌，避免并发释放。
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("rate-limiter-", 0).factory());
        this.refillerFuture = scheduler.scheduleAtFixedRate(
                this::refillSafely,
                window.toNanos(),
                window.toNanos(),
                TimeUnit.NANOSECONDS);
    }

    /**
     * 创建每秒补充 {@code permitsPerSecond} 个令牌的限流器。
     *
     * <p>桶容量等于 {@code permitsPerSecond}，每 1 秒补充一次。</p>
     *
     * @param permitsPerSecond 每秒允许通过的令牌数（必须 ≥ 1）
     * @return 限流器实例
     * @throws IllegalArgumentException 如果 {@code permitsPerSecond} < 1
     */
    public static RateLimiter ofTokensPerSecond(int permitsPerSecond) {
        validatePermits(permitsPerSecond);
        return new RateLimiter(permitsPerSecond, Duration.ofSeconds(1), 0L);
    }

    /**
     * 创建在 {@code window} 时间窗口内补充 {@code permits} 个令牌的限流器。
     *
     * <p>典型的"配额式"用法，例如"60 秒内最多调用 1000 次"。桶容量等于 {@code permits}，
     * 每个 {@code window} 补充一次。</p>
     *
     * @param permits 在指定时间窗口内允许通过的令牌数（必须 ≥ 1）
     * @param window  时间窗口（必须正）
     * @return 限流器实例
     * @throws NullPointerException     如果 {@code window} 为 null
     * @throws IllegalArgumentException 如果参数非法
     */
    public static RateLimiter ofTokensPerDuration(int permits, Duration window) {
        validatePermits(permits);
        Objects.requireNonNull(window, "window must not be null");
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive, got: " + window);
        }
        return new RateLimiter(permits, window, 0L);
    }

    /**
     * 创建非阻塞变体：{@link #tryAcquire()} 在等待 {@code timeout} 时间内仍无令牌则返回 {@code false}。
     *
     * <p>桶容量等于 {@code permits}，每个 {@code timeout} 时间窗口补充一次。
     * 该变体适用于 SLA 严苛场景 —— 当令牌耗尽时调用方立即放弃，而非阻塞挂起。</p>
     *
     * @param permits  每个补充周期内允许通过的令牌数（必须 ≥ 1）
     * @param timeout  既作为令牌补充周期，也作为 {@link #tryAcquire()} 的最大等待时长
     * @return 非阻塞限流器实例
     * @throws NullPointerException     如果 {@code timeout} 为 null
     * @throws IllegalArgumentException 如果参数非法
     * @deprecated 自 1.0.0 起，{@code try*} 前缀约定为<b>严格非阻塞</b>，本工厂隐式让
     *             {@link #tryAcquire()} 在工厂参数 {@code timeout} 内等待，违反该约定。
     *             请改用 {@link #ofTokensPerDuration(int, Duration)}（或
     *             {@link #builder()}）创建限流器，再通过新的
     *             {@link #tryAcquire(Duration) tryAcquire(Duration)} /
     *             {@link #tryAcquire(int, Duration)} 显式表达阻塞等待语义。本方法保留仅为
     *             向后兼容。
     */
    @Deprecated
    public static RateLimiter ofTryAcquireTimeout(int permits, Duration timeout) {
        validatePermits(permits);
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive, got: " + timeout);
        }
        return new RateLimiter(permits, timeout, timeout.toNanos());
    }

    /**
     * 创建自定义构建器。
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 尝试立即获取 1 个令牌（不阻塞）。
     *
     * @return 获取成功返回 {@code true}；桶空时立即返回 {@code false}
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试立即获取 {@code permits} 个令牌（不阻塞）。
     *
     * <p>本方法严格遵循 {@code try*} 命名约定 —— <b>无论通过何种工厂创建，桶空时立即返回
     * {@code false}，不会阻塞等待补充</b>。需要阻塞等待的语义请改用 {@link #tryAcquire(Duration)}
     * 或 {@link #tryAcquire(int, Duration)}；需要无限等待请改用 {@link #acquire(int)}。</p>
     *
     * @param permits 要获取的令牌数（必须 ≥ 1）
     * @return 获取成功返回 {@code true}；桶空时立即返回 {@code false}
     * @throws IllegalArgumentException 如果 {@code permits} < 1
     * @since 1.0.0
     */
    public boolean tryAcquire(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, got: " + permits);
        }
        if (closed.get()) {
            return false;
        }
        return semaphore.tryAcquire(permits);
    }

    /**
     * 如果令牌不足，在指定超时内等待 1 个令牌，超时后仍无令牌则返回 {@code false}。
     *
     * <p>与 {@link #tryAcquire()} 的关键区别：本方法会<b>阻塞等待</b>，而非立即返回。
     * 这是显式的<b>限时阻塞</b>语义，与 {@link #tryAcquire()} 的<b>非阻塞</b>语义彻底分离。
     * 需要无限等待的语义请改用 {@link #acquire()}。</p>
     *
     * <p>等待期间线程被中断时恢复中断标志并返回 {@code false}。</p>
     *
     * @param timeout 最大等待时长（必须正）
     * @return 获取成功返回 {@code true}；超时未获取返回 {@code false}
     * @throws NullPointerException     如果 {@code timeout} 为 null
     * @throws IllegalArgumentException 如果 {@code timeout} 为零或负
     * @since 1.0.0
     */
    public boolean tryAcquire(Duration timeout) {
        return tryAcquire(1, timeout);
    }

    /**
     * 如果令牌不足，在指定超时内等待 {@code permits} 个令牌，超时后仍无令牌则返回 {@code false}。
     *
     * <p>这是显式的<b>限时阻塞</b>语义，与 {@link #tryAcquire(int)} 的<b>非阻塞</b>语义
     * 彻底分离 —— 两者共用 {@code try*} 前缀但语义不同，调用方应根据需要选择合适的方法：
     * <ul>
     *   <li>立即判断 → {@link #tryAcquire(int)}</li>
     *   <li>限时等待 → {@link #tryAcquire(int, Duration)}</li>
     *   <li>无限等待 → {@link #acquire(int)}</li>
     * </ul></p>
     *
     * <p>等待期间线程被中断时恢复中断标志并返回 {@code false}。</p>
     *
     * @param permits 需要的令牌数（必须 ≥ 1）
     * @param timeout 最大等待时长（必须正）
     * @return 获取成功返回 {@code true}；超时未获取或已关闭返回 {@code false}
     * @throws NullPointerException     如果 {@code timeout} 为 null
     * @throws IllegalArgumentException 如果参数非法
     * @since 1.0.0
     */
    public boolean tryAcquire(int permits, Duration timeout) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, got: " + permits);
        }
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive, got: " + timeout);
        }
        if (closed.get()) {
            return false;
        }
        try {
            return semaphore.tryAcquire(permits, timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 阻塞获取 1 个令牌，直到桶内有可用令牌。
     *
     * @throws InterruptedException 如果等待期间线程被中断
     */
    public void acquire() throws InterruptedException {
        acquire(1);
    }

    /**
     * 阻塞获取 {@code permits} 个令牌，直到桶内有足够令牌。
     *
     * <p>一旦获取成功立即返回；如果令牌数不足，线程将被挂起直至补充到所需数量。
     * 唤醒被中断时立即抛出 {@link InterruptedException}，并恢复线程中断标志。</p>
     *
     * @param permits 要获取的令牌数（必须 ≥ 1）
     * @throws InterruptedException       如果等待期间线程被中断
     * @throws IllegalArgumentException   如果 {@code permits} < 1
     * @throws IllegalStateException      如果限流器已关闭
     */
    public void acquire(int permits) throws InterruptedException {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, got: " + permits);
        }
        if (closed.get()) {
            throw new IllegalStateException("RateLimiter is closed");
        }
        semaphore.acquire(permits);
    }

    /**
     * 阻塞获取 {@code permits} 个令牌，期间不响应线程中断。
     *
     * <p>与 {@link #acquire(int)} 的区别：本方法不会抛出 {@link InterruptedException}，
     * 等待期间被中断会继续等待，获取成功后才返回。但线程中断标志仍会被保留，
     * 调用方应自行处理（通常调用 {@link Thread#interrupted()} 检查）。</p>
     *
     * @param permits 要获取的令牌数（必须 ≥ 1）
     * @return 是否成功获取令牌（{@code false} 表示因并发被关闭导致 {@link Semaphore} 中断，
     *         实际场景几乎不会发生，仅作为非阻塞契约返回）
     * @throws IllegalArgumentException 如果 {@code permits} < 1
     */
    public boolean acquireUninterruptibly(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, got: " + permits);
        }
        if (closed.get()) {
            return false;
        }
        semaphore.acquireUninterruptibly(permits);
        return true;
    }

    /**
     * 返回当前桶内可用令牌数（近似值）。
     *
     * <p>该方法仅返回瞬时快照值，多线程环境下可能在调用返回前已被其他线程消费。
     * 不可用于精确决策，仅适合监控与可观测性指标。</p>
     *
     * @return 当前可用令牌数
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * 关闭限流器：取消后台补充任务并释放调度器线程。
     *
     * <p>关闭后再次调用 {@link #tryAcquire()} 返回 {@code false}，
     * {@link #acquire(int)} 抛出 {@link IllegalStateException}。
     * 本方法幂等，多次调用安全。</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            refillerFuture.cancel(false);
            scheduler.shutdown();
        }
    }

    /**
     * 由调度器定期调用的补充任务，吞掉所有异常以避免终止后续调度。
     *
     * <p>每次补充将桶顶到容量上限 {@link #permitsPerRefill}。如果当前可用令牌已超过
     * 容量（仅可能发生在 {@link Semaphore#release(int)} 被外部用户直接调用时），
     * 则不再释放，防止令牌数无限累积。</p>
     */
    private void refillSafely() {
        try {
            int available = semaphore.availablePermits();
            int release = permitsPerRefill - available;
            if (release > 0) {
                semaphore.release(release);
            }
            // release <= 0 表示桶已满，跳过本次补充即可。
        } catch (Throwable ignored) {
            // 调度任务吞掉所有 Throwable，避免单次失败终止后续补充周期。
        }
    }

    /**
     * 验证 {@code permits} 参数合法性。
     */
    private static void validatePermits(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, got: " + permits);
        }
    }

    /**
     * {@link RateLimiter} 构建器，支持所有工厂方法的细粒度配置。
     */
    public static final class Builder {

        private int permits;
        private Duration period;
        private boolean tryAcquireTimeoutEnabled;

        private Builder() {
        }

        /**
         * 设置每个补充周期允许通过的令牌数。
         *
         * @param permits 令牌数（必须 ≥ 1）
         * @return 当前构建器
         */
        public Builder permits(int permits) {
            validatePermits(permits);
            this.permits = permits;
            return this;
        }

        /**
         * 设置令牌补充周期。
         *
         * @param period 补充周期（必须正）
         * @return 当前构建器
         */
        public Builder period(Duration period) {
            Objects.requireNonNull(period, "period must not be null");
            if (period.isZero() || period.isNegative()) {
                throw new IllegalArgumentException("period must be positive, got: " + period);
            }
            this.period = period;
            return this;
        }

        /**
         * 启用非阻塞模式：{@code period} 同时作为 {@link RateLimiter#tryAcquire()} 的最大等待时长。
         *
         * @param enabled 是否启用兼容行为
         * @return 当前构建器
         * @deprecated 自 1.0.0 起 {@link RateLimiter#tryAcquire() tryAcquire()} 已统一为严格非阻塞，
         *             该设置不再影响 {@code tryAcquire} 行为。请改用 {@link RateLimiter#tryAcquire(Duration)}
         *             显式表达阻塞等待语义。本方法保留仅为兼容旧版调用代码。
         */
        @Deprecated
        public Builder tryAcquireTimeoutEnabled(boolean enabled) {
            this.tryAcquireTimeoutEnabled = enabled;
            return this;
        }

        /**
         * 构建 {@link RateLimiter} 实例。
         *
         * @return 限流器
         * @throws IllegalStateException 如果 {@link #permits(int)} 或 {@link #period(Duration)} 未设置
         */
        public RateLimiter build() {
            if (permits <= 0) {
                throw new IllegalStateException("permits must be set via permits(int)");
            }
            if (period == null) {
                throw new IllegalStateException("period must be set via period(Duration)");
            }
            long timeoutNanos = tryAcquireTimeoutEnabled ? period.toNanos() : 0L;
            return new RateLimiter(permits, period, timeoutNanos);
        }
    }
}
