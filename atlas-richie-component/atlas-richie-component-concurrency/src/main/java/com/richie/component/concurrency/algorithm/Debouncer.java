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
package com.richie.component.concurrency.algorithm;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 防抖器 —— 用语义化 API 替代 {@code ScheduledExecutorService} 的手动调用，
 * 将“重复触发即重置计时”的防抖模式封装为两个方法：{@link #trigger()} 和 {@link #close()}。
 *
 * <h2>基本使用</h2>
 * <pre>{@code
 * // 300ms 内无新 trigger() 触发，则执行一次 save()
 * Debouncer debouncer = Debouncer.of(Duration.ofMillis(300), this::save);
 *
 * // 事件循环中反复调用，每次重置计时器
 * onChange(() -> debouncer.trigger());
 *
 * // 应用关闭时顺带释放调度器线程
 * debouncer.close();
 * }</pre>
 *
 * <h3>与 ScheduledExecutorService 的差异</h3>
 * <p>
 * 如果直接用 {@code scheduler.schedule()} 并手动 {@code cancel()} + 重新 {@code schedule()}，
 * 需要维护 {@code ScheduledFuture} 引用和多处 try/catch。本工具将这套样板封装为
 * {@code trigger()} 一个调用点，同时负责释放虚拟线程调度器（{@link #close()}）。
 * </p>
 *
 * <h3>线程安全</h3>
 * <p>所有公开方法均为线程安全，可在多线程环境中并发调用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class Debouncer implements AutoCloseable {

    private final Runnable action;
    private final Duration delay;
    private final ScheduledExecutorService scheduler;

    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private ScheduledFuture<?> future;

    /**
     * 创建一个指定延迟和操作的防抖器。
     *
     * <p>调度器使用虚拟线程（适合 IO 等待密集型操作），单个线程循环复用。</p>
     *
     * @param delay  防抖延迟（非 null，正时长）
     * @param action 防抖动作（非 null）
     * @return 防抖器实例
     */
    public static Debouncer of(Duration delay, Runnable action) {
        Objects.requireNonNull(delay, "delay must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (delay.isZero() || delay.isNegative()) {
            throw new IllegalArgumentException("delay must be positive, got: " + delay);
        }
        return new Debouncer(delay, action);
    }

    private Debouncer(Duration delay, Runnable action) {
        this.delay = delay;
        this.action = action;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().name("debouncer-", 0).factory());
    }

    /**
     * 触发防抖：如果当前没有排队的执行计划，则创建一个新计划；
     * 如果已有则取消旧计划并创建一个新计划（重置倒计时）。
     *
     * <p>调用本方法后，如果在接下来的 {@code delay} 时间内没有再次调用，
     * 则执行指定的操作。每次调用都重置倒计时。</p>
     *
     * <p>防抖器关闭后调用本方法无效果。</p>
     */
    public void trigger() {
        if (closed.get()) {
            return;
        }
        synchronized (lock) {
            if (closed.get()) {
                return;
            }
            if (future != null) {
                future.cancel(false);
            }
            future = scheduler.schedule(this::executeActionSafely, delay.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 立即执行挂起的防抖操作（如果有），然后取消计时器。
     *
     * <p>没有挂起的操作时调用无效果。本方法等价于取消计时器并手动调用 {@code action.run()}。</p>
     */
    public void flush() {
        ScheduledFuture<?> current;
        synchronized (lock) {
            current = future;
            future = null;
        }
        if (current != null) {
            current.cancel(false);
        }
        try {
            action.run();
        } catch (Throwable ignored) {
            // flush 吞掉异常，不打断调用方
        }
    }

    /**
     * 取消当前挂起的防抖操作（如果有）。
     *
     * <p>调用后之前的 {@link #trigger()} 不再触发操作。不影响后续 {@link #trigger()} 调用。</p>
     */
    public void cancel() {
        synchronized (lock) {
            if (future != null) {
                future.cancel(false);
                future = null;
            }
        }
    }

    /**
     * 是否有待执行的防抖操作。
     *
     * @return 有排队的操作返回 {@code true}；无或已关闭返回 {@code false}
     */
    public boolean isPending() {
        synchronized (lock) {
            return future != null && !future.isDone();
        }
    }

    /**
     * 关闭防抖器：取消当前计时器，释放调度器线程。
     *
     * <p>本方法幂等，多次调用安全。</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            synchronized (lock) {
                if (future != null) {
                    future.cancel(false);
                    future = null;
                }
            }
            scheduler.shutdown();
        }
    }

    /**
     * 由调度器在触发时调用的安全包装 —— 吞掉操作中的所有异常，
     * 避免调度器线程因未捕获异常而终止下一个调度周期。
     */
    private void executeActionSafely() {
        try {
            action.run();
        } catch (Throwable ignored) {
            // 吞掉执行异常，调度器继续运行
        }
    }
}
