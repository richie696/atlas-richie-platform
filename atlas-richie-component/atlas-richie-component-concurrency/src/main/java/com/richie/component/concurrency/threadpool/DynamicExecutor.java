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
package com.richie.component.concurrency.threadpool;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可运行时调整的线程池 —— 在不依赖任何配置中心/三方组件的前提下，通过事件驱动的方式
 * 动态调整核心线程数、最大线程数、空闲存活时间和拒绝策略。
 *
 * <h2>设计思想</h2>
 * <p>传统线程池的缺点之一是参数在构造时固定，生产环境遇到突发流量或慢调用时只能通过
 * 重启应用来调整。本类通过{@link #onResize(PoolResizeEvent)}方法开放运行时调整能力，
 * 外部事件源可以是配置中心、Admin API、定时任务或 JMX 等任意机制——组件内部不绑定任何
 * 外部依赖。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 创建线程池（与传统 TPE 完全一致的构造方式）
 * var executor = new DynamicExecutor(
 *     4, 8, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000));
 *
 * // 运行时热更新（事件可来自配置中心监听器）
 * executor.onResize(PoolResizeEvent.builder()
 *         .corePoolSize(16)
 *         .maximumPoolSize(32)
 *         .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
 *         .build());
 *
 * // 获取运行态快照（接入监控系统）
 * PoolStatus status = executor.snapshot();
 * }</pre>
 *
 * <h3>与 {@link ThreadPoolExecutor} 的关系</h3>
 * <ul>
 *   <li>继承自 {@link ThreadPoolExecutor}，完全兼容 TPE 的完整 API</li>
 *   <li>扩展能力集中在 {@link #onResize(PoolResizeEvent)} 和 {@link #snapshot()} 上</li>
 *   <li>拒绝计数通过内部 {@link CountingHandler} 包装用户传入的拒绝策略来实现</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public class DynamicExecutor extends ThreadPoolExecutor {

    private static final Logger log = LoggerFactory.getLogger(DynamicExecutor.class);

    // ========================================================================
    // 构造函数 — 委托给 7 参构造，确保 CountingHandler 包装
    // ========================================================================

    /**
     * 创建可运行时调整的线程池。
     *
     * @param corePoolSize    核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime   空闲线程存活时间
     * @param unit            时间单位
     * @param workQueue       任务队列
     */
    public DynamicExecutor(
            int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建可运行时调整的线程池。
     *
     * @param corePoolSize    核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime   空闲线程存活时间
     * @param unit            时间单位
     * @param workQueue       任务队列
     * @param threadFactory   线程工厂
     */
    public DynamicExecutor(
            int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 创建可运行时调整的线程池。
     *
     * @param corePoolSize    核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime   空闲线程存活时间
     * @param unit            时间单位
     * @param workQueue       任务队列
     * @param handler         拒绝策略处理器（会被自动包装以支持拒绝计数）
     */
    public DynamicExecutor(
            int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), handler);
    }

    /**
     * 创建可运行时调整的线程池（完整参数）。
     *
     * @param corePoolSize    核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime   空闲线程存活时间
     * @param unit            时间单位
     * @param workQueue       任务队列
     * @param threadFactory   线程工厂
     * @param handler         拒绝策略处理器（会被自动包装以支持拒绝计数）
     */
    public DynamicExecutor(
            int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit,
            BlockingQueue<Runnable> workQueue,
            ThreadFactory threadFactory,
            RejectedExecutionHandler handler) {
        // 将用户 handler 包装为 CountingHandler 再传给 super
        // TPE 内部 execute() 调用 this.handler.rejectedExecution(r, this)，
        // 实际走 CountingHandler → 计数 → 委托给用户的原始 handler
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory,
                new CountingHandler(handler));
    }

    // ========================================================================
    // 运行时调整 — 核心扩展能力
    // ========================================================================

    /**
     * 根据事件参数对线程池进行运行时调整。
     *
     * <p>仅更新事件中非 {@code null} 的字段，{@code null} 字段表示"不调整"。</p>
     *
     * <h4>可调整参数</h4>
     * <ul>
     *   <li>{@code corePoolSize} → {@link ThreadPoolExecutor#setCorePoolSize(int)}</li>
     *   <li>{@code maximumPoolSize} → {@link ThreadPoolExecutor#setMaximumPoolSize(int)}</li>
     *   <li>{@code keepAliveTime} → {@link ThreadPoolExecutor#setKeepAliveTime(long, TimeUnit)}</li>
     *   <li>{@code rejectedHandler} → {@link ThreadPoolExecutor#setRejectedExecutionHandler(RejectedExecutionHandler)}</li>
     * </ul>
     *
     * <h4>使用经验</h4>
     * <ul>
     *   <li>突发流量：适当增大 {@code maximumPoolSize} 让更多任务能被处理</li>
     *   <li>慢调用积压：增大 {@code corePoolSize} 提高并发处理能力</li>
     *   <li>持续高水位：将拒绝策略从 {@code AbortPolicy} 调整为 {@code CallerRunsPolicy}，
     *       利用调用方线程消化部分压力</li>
     *   <li>流量回落：将两个 size 和 keepAlive 调回基线值，防止空闲线程占用资源</li>
     * </ul>
     *
     * @param event 事件参数，包含需要调整的目标值；{@code null} 时忽略
     */
    public void onResize(PoolResizeEvent event) {
        if (event == null) {
            log.warn("DynamicExecutor.onResize: received null event, ignored");
            return;
        }

        String original = snapshotBrief();

        if (event.getMaximumPoolSize() != null) {
            int newSize = event.getMaximumPoolSize();
            log.debug("DynamicExecutor.onResize: maximumPoolSize {} → {}", getMaximumPoolSize(), newSize);
            setMaximumPoolSize(newSize);
        }

        if (event.getCorePoolSize() != null) {
            int newCore = event.getCorePoolSize();
            if (newCore > getMaximumPoolSize()) {
                setMaximumPoolSize(newCore);
            }
            log.debug("DynamicExecutor.onResize: corePoolSize {} → {}", getCorePoolSize(), newCore);
            setCorePoolSize(newCore);
        }

        if (event.getKeepAliveTime() != null) {
            Duration duration = event.getKeepAliveTime();
            log.debug("DynamicExecutor.onResize: keepAliveTime {} → {}",
                    getKeepAliveTime(TimeUnit.MILLISECONDS), duration.toMillis());
            setKeepAliveTime(duration.toMillis(), TimeUnit.MILLISECONDS);
        }

        if (event.getRejectedHandler() != null) {
            countingHandler().setDelegate(event.getRejectedHandler());
            log.debug("DynamicExecutor.onResize: rejectedHandler → {}",
                    event.getRejectedHandler().getClass().getSimpleName());
        }

        log.info("DynamicExecutor.onResize: {} → {}", original, snapshotBrief());
    }

    // ========================================================================
    // 运行态监控
    // ========================================================================

    /**
     * 获取当前线程池的不可变快照。
     *
     * <p>外部监控系统可通过此方法周期拉取状态数据，接入 Micrometer、Prometheus 等。</p>
     *
     * @return 包含线程池当前全部关键指标的 {@link PoolStatus} 实例
     */
    public PoolStatus snapshot() {
        return PoolStatus.builder()
                .corePoolSize(getCorePoolSize())
                .maximumPoolSize(getMaximumPoolSize())
                .poolSize(getPoolSize())
                .activeCount(getActiveCount())
                .queueSize(getQueue().size())
                .queueRemainingCapacity(getQueue().remainingCapacity())
                .completedTaskCount(getCompletedTaskCount())
                .totalTaskCount(getTaskCount())
                .rejectedCount(countingHandler().getCount())
                .build();
    }

    // ========================================================================
    // 拒绝策略相关 — 透明处理 CountingHandler 包装
    // ========================================================================

    /**
     * 设置拒绝策略处理器。
     *
     * <p>此覆写确保用户设置的 handler 被 CountingHandler 包装，拒绝计数持续生效。</p>
     *
     * @param handler 新的拒绝策略处理器
     */
    @Override
    public void setRejectedExecutionHandler(@Nonnull RejectedExecutionHandler handler) {
        countingHandler().setDelegate(handler);
    }

    /**
     * 获取当前生效的拒绝策略处理器（返回用户传入的原生 handler，非内部包装器）。
     *
     * @return 当前拒绝策略处理器
     */
    @Override
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return countingHandler().getDelegate();
    }

    /**
     * 获取累计被拒绝的任务数。
     *
     * @return 自线程池创建以来被拒绝的任务总数
     */
    public long getRejectionCount() {
        return countingHandler().getCount();
    }

    /**
     * 重置拒绝计数。
     */
    public void resetRejectionCount() {
        countingHandler().resetCount();
    }

    // ========================================================================
    // 内部辅助
    // ========================================================================

    /**
     * 获取内部的 CountingHandler 实例。
     *
     * <p>构造时传入的 CountingHandler 被 TPE 内部存储为 {@code this.handler}，
     * 通过 {@code super.getRejectedExecutionHandler()} 可以拿到原始引用。</p>
     */
    private CountingHandler countingHandler() {
        return (CountingHandler) super.getRejectedExecutionHandler();
    }

    /**
     * 生成一个简短的线程池参数摘要（用于日志）。
     */
    private String snapshotBrief() {
        return String.format("core=%d, max=%d, keepAlive=%dms, handler=%s",
                getCorePoolSize(), getMaximumPoolSize(),
                getKeepAliveTime(TimeUnit.MILLISECONDS),
                getRejectedExecutionHandler().getClass().getSimpleName());
    }

    // ========================================================================
    // 内部静态类 — 拒绝计数包装器
    // ========================================================================

    /**
     * 内部拒绝计数包装器 —— 对用户传入的任意 RejectedExecutionHandler 做透明计数。
     *
     * <p>TPE 的 {@link #rejectedExecution(Runnable, ThreadPoolExecutor)} 不是可覆写的 protected 方法，
     * 因此无法通过子类覆写来计数。本包装器在构造时替换掉用户 handler，TPE 内部
     * {@code execute()} 调用 {@code handler.rejectedExecution(r, this)} 时实际走的
     * 是 CountingHandler，先计数再委托给用户自己的 handler。</p>
     */
    private static class CountingHandler implements RejectedExecutionHandler {

        private final AtomicLong count = new AtomicLong();
        private volatile RejectedExecutionHandler delegate;

        CountingHandler(RejectedExecutionHandler delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate handler must not be null");
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            count.incrementAndGet();
            delegate.rejectedExecution(r, executor);
        }

        void setDelegate(RejectedExecutionHandler handler) {
            this.delegate = Objects.requireNonNull(handler, "handler must not be null");
        }

        RejectedExecutionHandler getDelegate() {
            return delegate;
        }

        long getCount() {
            return count.get();
        }

        void resetCount() {
            count.set(0);
        }
    }
}
