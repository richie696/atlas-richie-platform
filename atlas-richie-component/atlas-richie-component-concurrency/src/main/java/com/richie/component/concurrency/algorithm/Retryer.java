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
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 重试执行器 —— 带指数退避、可选抖动的通用重试工具。
 *
 * <p>在分布式调用、网络请求、外部依赖等不稳定场景中，重试是提升可用性的基本手段。
 * 本工具封装了<b>指数退避</b>(Exponential Backoff)、<b>全量抖动</b>(Full Jitter)、
 * <b>异常类型过滤</b>三大关键能力，避免业务代码重复实现。</p>
 *
 * <h2>基本用法</h2>
 * <pre>{@code
 * // 简单重试：3 次尝试，初始退避 100ms
 * String result = Retryer.of(Duration.ofMillis(100))
 *         .maxAttempts(3)
 *         .execute(() -> callRemoteService());
 *
 * // 带 fallback，永不抛异常
 * String result = Retryer.of(Duration.ofMillis(100))
 *         .maxAttempts(3)
 *         .execute(() -> callRemoteService(), "default");
 *
 * // 启用全量抖动，避免惊群
 * Retryer.of(Duration.ofMillis(100))
 *         .jitter(true)
 *         .execute(() -> callRemoteService());
 *
 * // 仅对特定异常类型重试
 * Retryer.of(Duration.ofMillis(100))
 *         .retryOn(IOException.class)
 *         .execute(() -> callRemoteService());
 * }</pre>
 *
 * <h3>退避策略</h3>
 * <p>
 * 退避时间按 {@code initialBackoff * 2^(n-1)} 指数增长，最大不超过 {@code maxBackoff}。
 * 启用 {@link RetryBuilder#jitter(boolean) 抖动} 后，实际睡眠时间在 {@code [backoff/2, backoff]} 区间内随机选取，
 * 即 AWS 架构师 Marc Brooker 提出的"全量抖动"(Full Jitter)算法，
 * 在多客户端并发重试同一服务时显著降低惊群效应。
 * </p>
 *
 * <h3>异常处理</h3>
 * <ul>
 *   <li><b>命中重试类型</b>：捕获后按策略退避重试，耗尽后抛出 {@link RetryExhaustedException}</li>
 *   <li><b>未命中重试类型</b>：通过"sneaky throw"立即透传原异常，不重试</li>
 *   <li><b>{@link InterruptedException}</b>：恢复线程中断标志后抛出 {@link RetryExhaustedException}</li>
 *   <li><b>线程已处于中断状态</b>：下一轮重试前检测到中断，立即抛出 {@link RetryExhaustedException}</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class Retryer {

    private Retryer() {
    }

    /**
     * 创建一个 {@link RetryBuilder} 构建器实例。
     *
     * @param initialBackoff 初始退避时间（必须非负）
     * @return 构建器实例
     * @throws NullPointerException     如果 {@code initialBackoff} 为 null
     * @throws IllegalArgumentException 如果 {@code initialBackoff} 为负
     */
    public static RetryBuilder of(Duration initialBackoff) {
        return new RetryBuilder(initialBackoff);
    }

    // ---- inner types ----

    /**
     * {@link Retryer} 构建器，采用链式 API 设计，所有配置项均有合理默认值。
     */
    public static final class RetryBuilder {

        private static final int DEFAULT_MAX_ATTEMPTS = 3;
        private static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(30);
        private static final int MAX_SAFE_SHIFT = 30;

        private final Duration initialBackoff;
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration maxBackoff = DEFAULT_MAX_BACKOFF;
        private boolean jitter = false;

        @SuppressWarnings("unchecked")
        private Class<? extends Throwable>[] retryOnTypes = new Class[]{Exception.class};

        private RetryBuilder(Duration initialBackoff) {
            Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
            if (initialBackoff.isNegative()) {
                throw new IllegalArgumentException(
                        "initialBackoff must not be negative, got: " + initialBackoff);
            }
            this.initialBackoff = initialBackoff;
        }

        /**
         * 设置最大尝试次数（默认：{@value #DEFAULT_MAX_ATTEMPTS}）。
         *
         * <p>首次执行算作第 1 次，因此 {@code maxAttempts(3)} 表示"最多重试 2 次"。</p>
         *
         * @param maxAttempts 最大尝试次数（必须 ≥ 1）
         * @return 当前构建器
         * @throws IllegalArgumentException 如果 {@code maxAttempts} &lt; 1
         */
        public RetryBuilder maxAttempts(int maxAttempts) {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be >= 1, got: " + maxAttempts);
            }
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * 设置最大退避时间上限（默认：30 秒）。
         *
         * <p>指数退避达到该值后不再增长，避免在长时间运行的重试场景下等待数小时。</p>
         *
         * @param maxBackoff 最大退避时间（必须非负）
         * @return 当前构建器
         * @throws NullPointerException     如果 {@code maxBackoff} 为 null
         * @throws IllegalArgumentException 如果 {@code maxBackoff} 为负
         */
        public RetryBuilder maxBackoff(Duration maxBackoff) {
            Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
            if (maxBackoff.isNegative()) {
                throw new IllegalArgumentException(
                        "maxBackoff must not be negative, got: " + maxBackoff);
            }
            this.maxBackoff = maxBackoff;
            return this;
        }

        /**
         * 设置是否启用全量抖动（默认：{@code false}）。
         *
         * <p>启用后，实际退避时间在 {@code [backoff/2, backoff]} 区间内随机选取，
         * 适用于多客户端并发调用同一服务的场景，避免惊群效应(Thundering Herd)。</p>
         *
         * @param jitter 是否启用抖动
         * @return 当前构建器
         */
        public RetryBuilder jitter(boolean jitter) {
            this.jitter = jitter;
            return this;
        }

        /**
         * 设置触发重试的异常类型（默认：{@link Exception}）。
         *
         * <p>仅当任务抛出的异常属于任一指定类型（或其子类）时才会触发重试；
         * 其它类型的异常会通过"sneaky throw"立即透传，不重试。</p>
         *
         * <p>注意：{@link InterruptedException} 不受此配置约束，永远会被视为中断信号
         * 立即终止重试循环。</p>
         *
         * @param types 异常类型数组（至少一个）
         * @return 当前构建器
         * @throws NullPointerException     如果 {@code types} 为 null
         * @throws IllegalArgumentException 如果 {@code types} 为空数组
         */
        @SafeVarargs
        public final RetryBuilder retryOn(Class<? extends Throwable>... types) {
            Objects.requireNonNull(types, "types must not be null");
            if (types.length == 0) {
                throw new IllegalArgumentException("retryOn requires at least one exception type");
            }
            this.retryOnTypes = types.clone();
            return this;
        }

        // ---- execute ----

        /**
         * 执行任务并在失败时按策略重试。
         *
         * <p>行为约定：</p>
         * <ul>
         *   <li>任务正常返回 → 立即返回该结果，不再重试</li>
         *   <li>命中 {@link #retryOn} 的异常 → 按退避策略重试直至耗尽，
         *       最终抛出 {@link RetryExhaustedException}（包含最后一次异常作为 cause）</li>
         *   <li>未命中 {@link #retryOn} 的异常 → 立即透传原异常，不重试</li>
         *   <li>捕获到 {@link InterruptedException} 或线程已处于中断状态 →
         *       恢复中断标志后抛出 {@link RetryExhaustedException}</li>
         * </ul>
         *
         * @param task 要执行的任务
         * @param <T>  任务返回类型
         * @return 任务执行结果
         * @throws RetryExhaustedException 重试耗尽或线程被中断
         * @throws NullPointerException     如果 {@code task} 为 null
         */
        public <T> T execute(Callable<T> task) {
            Objects.requireNonNull(task, "task must not be null");
            Throwable lastException = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new RetryExhaustedException(
                            "Retry interrupted before attempt " + attempt, lastException);
                }
                try {
                    return task.call();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException(
                            "Retry interrupted by InterruptedException", e);
                } catch (Exception e) {
                    if (!shouldRetryOn(e)) {
                        sneakyThrow(e);
                    }
                    lastException = e;
                    if (attempt < maxAttempts) {
                        sleepBackoff(computeBackoff(attempt));
                    }
                }
            }
            String detail = lastException != null && lastException.getMessage() != null
                    ? lastException.getMessage()
                    : "unknown";
            throw new RetryExhaustedException(
                    "Retry exhausted after " + maxAttempts + " attempts, last error: " + detail,
                    lastException);
        }

        /**
         * 执行任务并在失败时按策略重试，最终失败时返回 fallback 而非抛出异常。
         *
         * <p>此方法对 {@link Exception}（含 {@link RuntimeException} 及受检异常）永不抛出，
         * 适用于必须返回默认值的容错场景，例如：</p>
         * <ul>
         *   <li>缓存预热失败时返回 {@code null} 或本地兜底值</li>
         *   <li>非关键路径的远程调用降级</li>
         *   <li>定时任务的健康检查调用</li>
         * </ul>
         *
         * <p><b>关于 {@link Error} 的行为：</b>本方法仅捕获 {@link Exception}，不会捕获
         * {@link Error}（如 {@link OutOfMemoryError}、{@link StackOverflowError}）。{@link Error}
         * 表示 JVM 级严重问题，应当被传播至顶层处理器（如 {@code Thread.UncaughtExceptionHandler}），
         * 而非静默吞掉。调用方无需为此额外处理，但需了解该语义以避免在 catch 块中对 {@link Error}
         * 做错误假设。</p>
         *
         * @param task     要执行的任务
         * @param fallback 重试耗尽、被中断、或抛出非重试异常时返回的默认值
         * @param <T>      任务返回类型
         * @return 任务执行结果，或 {@code fallback}
         */
        public <T> T execute(Callable<T> task, T fallback) {
            try {
                return execute(task);
            } catch (Exception e) {
                return fallback;
            }
        }

        /**
         * 执行无返回值的任务并在失败时按策略重试。
         *
         * <p>内部通过 {@link Executors#callable(Runnable, Object) Executors.callable(runnable, null)}
         * 包装为 {@link Callable} 后复用通用重试逻辑，行为与 {@link #execute(Callable)} 一致。</p>
         *
         * @param task 要执行的任务
         * @throws RetryExhaustedException 重试耗尽或线程被中断
         * @throws NullPointerException     如果 {@code task} 为 null
         */
        public void execute(Runnable task) {
            Objects.requireNonNull(task, "task must not be null");
            Callable<Object> callable = Executors.callable(task, null);
            execute(callable);
        }

        // ---- private helpers ----

        /**
         * 判断给定异常是否属于需要重试的类型集合。
         *
         * <p>使用 {@link Class#isInstance(Object)} 检查，支持子类匹配。</p>
         */
        private boolean shouldRetryOn(Throwable t) {
            for (Class<? extends Throwable> type : retryOnTypes) {
                if (type.isInstance(t)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 计算第 {@code attempt} 次失败后的退避时间。
         *
         * <p>公式：{@code backoff_n = min(initialBackoff * 2^(n-1), maxBackoff)}；
         * 启用抖动后，实际睡眠在 {@code [backoff/2, backoff]} 区间内随机选取。</p>
         */
        private Duration computeBackoff(int attempt) {
            long initialMs = initialBackoff.toMillis();
            long maxMs = maxBackoff.toMillis();

            // 限制左移位数避免 long 溢出
            long shift = Math.min(attempt - 1, MAX_SAFE_SHIFT);
            long backoffMs;
            try {
                backoffMs = Math.min(Math.multiplyExact(initialMs, 1L << shift), maxMs);
            } catch (ArithmeticException overflow) {
                backoffMs = maxMs;
            }

            if (jitter) {
                long minMs = backoffMs / 2;
                if (minMs >= backoffMs) {
                    return Duration.ofMillis(backoffMs);
                }
                long jitteredMs = ThreadLocalRandom.current().nextLong(minMs, backoffMs + 1);
                return Duration.ofMillis(jitteredMs);
            }
            return Duration.ofMillis(backoffMs);
        }

        /**
         * 在两次重试之间执行退避睡眠，睡眠期间被中断立即抛出 {@link RetryExhaustedException}。
         */
        private void sleepBackoff(Duration backoff) {
            long sleepMs = backoff.toMillis();
            if (sleepMs <= 0) {
                return;
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RetryExhaustedException(
                        "Retry interrupted during backoff sleep", e);
            }
        }

        /**
         * Sneaky throw —— 通过泛型擦除绕过 Java 受检异常检查，
         * 用于将不符合 {@link #retryOn} 的异常原样透传给调用方。
         */
        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable t) throws E {
            throw (E) t;
        }
    }
}