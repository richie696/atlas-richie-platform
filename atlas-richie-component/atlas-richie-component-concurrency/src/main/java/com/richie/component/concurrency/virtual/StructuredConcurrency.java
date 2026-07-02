package com.richie.component.concurrency.virtual;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.function.Supplier;

/**
 * 结构化并发工具类 —— 对 JDK 25 {@link StructuredTaskScope} 的高频场景封装。
 *
 * <p>在线程池/虚拟线程时代，结构化并发是管理并发任务生命周期的最佳实践。
 * 本工具提供四大核心模式，无需记忆 {@link Joiner} API 的冗长名称：</p>
 *
 * <ol>
 *   <li><b>汇聚模式 (Gather):</b>全部子任务成功才返回，任一失败则整体取消</li>
 *   <li><b>竞速模式 (Race):</b>首个成功的子任务结果返回，其余自动取消</li>
 *   <li><b>超时模式 (Deadline):</b>超时未完成则抛出异常</li>
 *   <li><b>尽力汇聚 (Best-Effort Gather):</b>任何子任务失败不影响其他子任务，返回成功与失败明细</li>
 * </ol>
 *
 * <h3>基本使用</h3>
 * <pre>{@code
 * // 汇聚：并行查询两个服务
 * var results = StructuredConcurrency.gatherAll(List.of(
 *     (Callable<User>) () -> userService.findById(1L),
 *     (Callable<List<Order>>) () -> orderService.findByUser(1L)
 * ));
 * User user = (User) results.get(0);
 *
 * // 竞速：从多个缓存查询（任一成功即返回）
 * String result = StructuredConcurrency.race(List.of(
 *     () -> primaryCache.get(key),
 *     () -> backupCache.get(key)
 * ));
 *
 * // 超时：500ms 内必须完成
 * String data = StructuredConcurrency.withDeadline(
 *     () -> externalService.query(),
 *     Duration.ofMillis(500)
 * );
 *
 * // 尽力汇聚：5 个任务 2 个失败，最终收集到 3 个成功 + 2 个失败明细
 * var outcome = StructuredConcurrency.gatherAllBestEffort(List.of(
 *     () -> queryA(),
 *     () -> queryB(),
 *     () -> { throw new RuntimeException("down"); },
 *     () -> queryD(),
 *     () -> { throw new RuntimeException("down"); }
 * ));
 * if (outcome.hasAnySuccess()) {
 *     process(outcome.successes());
 * }
 * }</pre>
 *
 * <h3>与 {@code TenantStructuredTaskScope} 的关系</h3>
 * <p>
 * {@code TenantStructuredTaskScope} 提供了 {@link java.lang.ScopedValue 租户上下文感知}
 * 的 {@code awaitAll} / {@code anySuccessful} 工厂方法。本工具提供的是 <b>通用</b>
 * 结构化并发语义，不绑定任何特定上下文。两者可配合使用：
 * </p>
 * <pre>{@code
 * try (var scope = TenantStructuredTaskScope.awaitAll()) {
 *     var f1 = scope.fork(task1);
 *     var f2 = scope.fork(task2);
 *     scope.join();
 * }
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
@SuppressWarnings("preview")
public final class StructuredConcurrency {

    private StructuredConcurrency() {
    }

    /**
     * 共享虚拟线程工厂：单实例保证线程名计数器在多次 {@code open} 调用间单调递增，
     * 避免每次新建工厂导致线程名重复。
     */
    private static final VirtualThreadFactory CONCURRENCY_VT_FACTORY = VirtualThreadFactory.of("ar-concurrency-");

    // ========== Gather ==========

    /**
     * 汇聚模式：并行执行所有任务，等待全部成功返回结果列表。
     *
     * <p>任一任务抛出异常时，立即取消其余未完成的任务，并透传异常。</p>
     *
     * @param tasks 并发执行的任务集合
     * @param <T>   任务返回类型
     * @return 按 {@code tasks} 顺序排列的结果列表
     * @throws Exception 任一任务抛出的异常
     */
    public static <T> List<T> gatherAll(Collection<? extends Callable<? extends T>> tasks) throws Exception {
        try (var scope = StructuredTaskScope.open(
                Joiner.<T>awaitAllSuccessfulOrThrow(),
                cfg -> cfg.withThreadFactory(CONCURRENCY_VT_FACTORY))) {
            var forks = tasks.stream()
                    .map(scope::fork)
                    .toList();
            scope.join();
            return forks.stream()
                    .map(f -> (T) f.get())
                    .toList();
        }
    }

    /**
     * 汇聚模式：从 {@link Supplier} 集合并发执行（无需处理受检异常）。
     *
     * @param tasks 并发执行的任务集合
     * @param <T>   任务返回类型
     * @return 按 {@code tasks} 顺序排列的结果列表
     * @throws Exception 任一任务抛出的异常
     */
    public static <T> List<T> gatherAllSuppliers(Collection<? extends Supplier<? extends T>> tasks) throws Exception {
        return gatherAll(tasks.stream()
                .<Callable<? extends T>>map(s -> s::get)
                .toList());
    }

    // ========== Race ==========

    /**
     * 竞速模式：并行执行所有任务，返回首个成功的结果。
     *
     * <p>一旦有任意子任务成功返回，其余未完成的子任务被自动取消。
     * 如果全部任务都抛出异常，则抛出最后一个异常。</p>
     *
     * @param tasks 并发执行的任务集合
     * @param <T>   任务返回类型
     * @return 首个成功的结果
     * @throws Exception 所有任务均失败时抛出
     */
    public static <T> T race(Collection<? extends Callable<? extends T>> tasks) throws Exception {
        try (var scope = StructuredTaskScope.open(
                Joiner.<T>anySuccessfulResultOrThrow(),
                cfg -> cfg.withThreadFactory(CONCURRENCY_VT_FACTORY))) {
            for (var task : tasks) {
                scope.fork(task);
            }
            return scope.join();
        }
    }

    /**
     * 竞速模式：从 {@link Supplier} 集合并发执行（无需处理受检异常）。
     *
     * @param tasks 并发执行的任务集合
     * @param <T>   任务返回类型
     * @return 首个成功的结果
     * @throws Exception 所有任务均失败时抛出
     */
    public static <T> T raceSuppliers(Collection<? extends Supplier<? extends T>> tasks) throws Exception {
        return race(tasks.stream()
                .<Callable<? extends T>>map(s -> s::get)
                .toList());
    }

    // ========== Deadline ==========

    /**
     * 超时模式：在指定时限内执行一个任务，超时则抛出异常。
     *
     * <p>利用 JDK 25 {@link java.util.concurrent.StructuredTaskScope.Configuration#withTimeout(Duration)}
     * 能力，超时后 scope 自动取消所有子任务。</p>
     *
     * @param task    要执行的任务
     * @param timeout 超时时间
     * @param <T>     任务返回类型
     * @return 任务执行结果
     * @throws Exception 任务异常或超时时抛出
     */
    public static <T> T withDeadline(Callable<? extends T> task, Duration timeout) throws Exception {
        try (var scope = StructuredTaskScope.open(
                Joiner.<T>awaitAllSuccessfulOrThrow(),
                cfg -> cfg.withTimeout(timeout).withThreadFactory(CONCURRENCY_VT_FACTORY))) {
            var fork = scope.fork(task);
            scope.join();
            return fork.get();
        }
    }

    // ========== Batch ==========

    /**
     * 分批汇聚：当任务数量很大时，分批执行以避免单个 scope 内 fork 过多。
     *
     * <p>每批执行 {@code batchSize} 个任务，批次间串行；但批次内部全并行。
     * 适用于需要限制并发度的场景，如数据库批量查询。</p>
     *
     * @param tasks     所有任务
     * @param batchSize 每批并发数
     * @param <T>       任务返回类型
     * @return 所有批次结果的扁平列表
     * @throws Exception 任一任务异常
     */
    public static <T> List<T> gatherBatched(Collection<? extends Callable<? extends T>> tasks, int batchSize)
            throws Exception {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, got: " + batchSize);
        }
        var taskList = List.copyOf(tasks);
        var results = new ArrayList<T>(taskList.size());
        for (int i = 0; i < taskList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, taskList.size());
            results.addAll(gatherAll(taskList.subList(i, end)));
        }
        return results;
    }

    // ========== Best-Effort Gather ==========

    /**
     * 尽力汇聚模式：并行执行所有任务，捕获每个子任务的执行结果（含成功与失败明细）。
     *
     * <p>与 {@link #gatherAll} 的关键区别：本方法<b>不</b>因单个任务失败而取消其他任务，
     * 而是收集全部任务的执行结果：成功的返回结果值，失败的任务其 {@link Throwable} 被记录。
     * 适用于"部分失败不影响整体"的容错场景，例如批量数据获取时偶尔几个数据源临时不可用。</p>
     *
     * <h3>返回值详解</h3>
     * <p>{@link BestEffortResult} 包含三个核心部分：</p>
     * <ul>
     *   <li>{@link BestEffortResult#successes() successes} — 成功结果集合（按原始顺序排列）</li>
     *   <li>{@link BestEffortResult#failures() failures} — 失败任务对应的异常集合</li>
     *   <li>{@link BestEffortResult#failedIndices() failedIndices} — 失败任务在原始集合中的下标集合</li>
     * </ul>
     *
     * <h3>实现说明</h3>
     * <p>内部 fork 出与任务数相等的虚拟线程，每个任务独立捕获 {@link Throwable}（含 {@link Error}），
     * 并将其包装为 {@link Outcome}。然后用 {@link Joiner#awaitAllSuccessfulOrThrow()} 等待所有 fork 完成，
     * scope 层面不会因单个任务失败而中断其他任务。最后从 {@link Outcome} 中还原成功结果与失败明细。</p>
     *
     * @param tasks 并发执行的任务集合
     * @param <T>   任务返回类型
     * @return 包含成功/失败明细的 {@link BestEffortResult}
     */
    public static <T> BestEffortResult<T> gatherAllBestEffort(Collection<? extends Callable<? extends T>> tasks) {
        // 没有任务时直接返回空结果，避免无意义的 scope 创建
        if (tasks.isEmpty()) {
            return new BestEffortResult<>(List.of(), List.of(), List.of());
        }
        var taskList = List.copyOf(tasks);
        try (var scope = StructuredTaskScope.open(
                Joiner.<Outcome<T>>awaitAllSuccessfulOrThrow(),
                cfg -> cfg.withThreadFactory(CONCURRENCY_VT_FACTORY))) {
            // 按输入顺序保留每个 fork 的引用，便于最终按位置归集结果
            List<Subtask<? extends Outcome<? extends T>>> forks = new ArrayList<>(taskList.size());
            for (int i = 0; i < taskList.size(); i++) {
                final int index = i;
                forks.add(scope.fork(() -> {
                    try {
                        T value = taskList.get(index).call();
                        return new Success<>(value);
                    } catch (Throwable t) {
                        // 捕获全部 Throwable（含 Error）防止单任务污染 scope
                        return new Failure<>(t);
                    }
                }));
            }
            scope.join();

            var successes = new ArrayList<T>(forks.size());
            var failures = new ArrayList<Throwable>();
            var failedIndices = new ArrayList<Integer>();
            for (int i = 0; i < forks.size(); i++) {
                Outcome<? extends T> outcome = forks.get(i).get();
                if (outcome instanceof Success<? extends T>(T value)) {
                    successes.add(value);
                } else if (outcome instanceof Failure<? extends T>(Throwable error)) {
                    failures.add(error);
                    failedIndices.add(i);
                }
            }
            return new BestEffortResult<>(
                    List.copyOf(successes),
                    List.copyOf(failures),
                    List.copyOf(failedIndices));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BestEffortResult<>(List.of(), List.of(), List.of());
        }
    }

    /**
     * 尽力汇聚模式：从 {@link Supplier} 集合并发执行（无需处理受检异常）。
     *
     * @param tasks 并发执行的任务集合
     * @param <T>   任务返回类型
     * @return 包含成功/失败明细的 {@link BestEffortResult}
     */
    public static <T> BestEffortResult<T> gatherAllBestEffortSuppliers(Collection<? extends Supplier<? extends T>> tasks) {
        return gatherAllBestEffort(tasks.stream()
                .<Callable<? extends T>>map(s -> s::get)
                .toList());
    }

    // ========== Outcome 内部类型 ==========

    /**
     * 尽力汇聚的子任务内部结果包装：标记成功值或失败异常。
     *
     * @param <T> 元素类型
     */
    private sealed interface Outcome<T> permits Success, Failure {
    }

    /**
     * 子任务成功结果包装。
     */
    private record Success<T>(T value) implements Outcome<T> {
    }

    /**
     * 子任务失败结果包装。
     */
    private record Failure<T>(Throwable error) implements Outcome<T> {
    }

    // ========== BestEffortResult 公共 record ==========

    /**
     * 尽力汇聚结果 —— 同时保存成功结果与失败明细的不可变记录。
     *
     * <p>所有 list 字段在构造时通过 {@link List#copyOf} 进行防御性拷贝，对外暴露的列表不可修改。
     * 成功数加失败索引数等于原始任务数：{@code successes().size() + failedIndices().size() == 原始任务数}。</p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * var outcome = StructuredConcurrency.gatherAllBestEffort(List.of(
     *     () -> queryA(),
     *     () -> { throw new IOException("network"); },
     *     () -> queryC()
     * ));
     * // outcome.successes() = [resultA, resultC]
     * // outcome.failures() = [IOException("network")]
     * // outcome.failedIndices() = [1]
     *
     * if (outcome.hasAnySuccess()) {
     *     log.info("partial success: {}/{}",
     *         outcome.successCount(),
     *         outcome.successCount() + outcome.failureCount());
     * }
     * }</pre>
     *
     * @param successes     成功结果集合，按原始任务顺序排列
     * @param failures      失败任务对应的异常集合，与 {@link #failedIndices} 一一对应
     * @param failedIndices 失败任务在原始集合中的下标集合
     * @param <T>           任务返回类型
     */
    public record BestEffortResult<T>(
            List<T> successes,
            List<Throwable> failures,
            List<Integer> failedIndices) {

        /**
         * 规范化构造器：使用 {@link List#copyOf} 对每个集合字段进行防御性拷贝。
         */
        public BestEffortResult {
            successes = List.copyOf(successes);
            failures = List.copyOf(failures);
            failedIndices = List.copyOf(failedIndices);
        }

        /**
         * 是否存在成功任务。
         */
        public boolean hasAnySuccess() {
            return !successes.isEmpty();
        }

        /**
         * 成功任务数量。
         */
        public int successCount() {
            return successes.size();
        }

        /**
         * 失败任务数量。
         */
        public int failureCount() {
            return failures.size();
        }
    }
}
