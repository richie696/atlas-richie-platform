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
package com.richie.component.concurrency.virtual;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StructuredConcurrency}.
 *
 * <p>针对 JDK 25 {@code StructuredTaskScope} 高频场景封装的契约级测试，覆盖以下六个公开 API：</p>
 *
 * <ol>
 *   <li>{@code gatherAll} / {@code gatherAllSuppliers} —— 汇聚模式：全部子任务成功才返回</li>
 *   <li>{@code race} / {@code raceSuppliers} —— 竞速模式：首个成功的结果立即返回</li>
 *   <li>{@code withDeadline} —— 超时模式：超时未完成抛出异常</li>
 *   <li>{@code gatherBatched} —— 分批汇聚：控制并发度</li>
 * </ol>
 *
 * <p>测试策略：</p>
 * <ul>
 *   <li>验证每个 API 的正常路径、空集合与单元素边界</li>
 *   <li>验证异常传播语义（任一失败 → 整体失败，兄弟任务被取消）</li>
 *   <li>验证虚拟线程执行（{@link Thread#isVirtual()}）</li>
 *   <li>验证超时通过 {@code StructuredTaskScope.TimeoutException} 反馈</li>
 *   <li>使用 {@code @Timeout} 防止任何意外的挂起影响测试套件</li>
 * </ul>
 *
 * <p>每个测试相互独立，不共享可变状态。</p>
 */
class StructuredConcurrencyTest {

    private static final Duration QUICK = Duration.ofMillis(50);
    private static final Duration SAFETY = Duration.ofSeconds(2);

    // ============================================================================================
    // 构造器契约
    // ============================================================================================

    @Test
    @Timeout(2)
    @DisplayName("构造器：工具类不可实例化（私有构造器）")
    void constructor_isPrivate() throws NoSuchMethodException {
        Constructor<StructuredConcurrency> constructor = StructuredConcurrency.class.getDeclaredConstructor();

        assertThat(Modifier.isPrivate(constructor.getModifiers()))
                .as("StructuredConcurrency must expose only a private constructor")
                .isTrue();
    }

    // ============================================================================================
    // gatherAll —— Callable 版本
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("gatherAll: 全部任务成功时按输入顺序返回结果列表")
    void gatherAll_allSucceed_returnsAllResults() throws Exception {
        List<Callable<Integer>> tasks = List.of(
                () -> 1,
                () -> 2,
                () -> 3,
                () -> 4,
                () -> 5);

        List<Integer> results = StructuredConcurrency.gatherAll(tasks);

        assertThat(results).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAll: 空集合返回空列表，不抛异常")
    void gatherAll_emptyCollection_returnsEmptyList() throws Exception {
        List<Integer> results = StructuredConcurrency.gatherAll(Collections.<Callable<Integer>>emptyList());

        assertThat(results).isEmpty();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAll: 单元素集合返回单元素列表")
    void gatherAll_singleTask_returnsSingleResult() throws Exception {
        List<String> results = StructuredConcurrency.gatherAll(List.of(() -> "solo"));

        assertThat(results).containsExactly("solo");
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAll: 任一任务抛异常 → 异常传播给调用方")
    void gatherAll_oneTaskFails_propagatesException() {
        IllegalStateException failure = new IllegalStateException("task-2 failed");

        assertThatThrownBy(() -> StructuredConcurrency.gatherAll(List.<Callable<String>>of(
                () -> "ok-1",
                () -> {
                    throw failure;
                },
                () -> "ok-3")))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAll: 异常信息保留原始消息")
    void gatherAll_exceptionMessageIsPreserved() {
        assertThatThrownBy(() -> StructuredConcurrency.gatherAll(List.<Callable<String>>of(
                () -> {
                    throw new IllegalArgumentException("specific boom");
                })))
                .isInstanceOf(Exception.class)
                .hasStackTraceContaining("specific boom");
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAll: 子任务运行在虚拟线程上")
    void gatherAll_runsOnVirtualThread() throws Exception {
        AtomicReference<Boolean> isVirtual = new AtomicReference<>(Boolean.FALSE);

        StructuredConcurrency.gatherAll(List.<Callable<String>>of(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            return "done";
        }));

        assertThat(isVirtual.get())
                .as("StructuredTaskScope must use virtual threads by default (JDK 25)")
                .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAll: 子任务之间的执行顺序不保证，但结果顺序与输入一致")
    void gatherAll_preservesResultOrderDespiteScheduling() throws Exception {
        List<Callable<Integer>> tasks = List.of(
                () -> {
                    Thread.sleep(30);
                    return 0;
                },
                () -> {
                    Thread.sleep(10);
                    return 1;
                },
                () -> {
                    Thread.sleep(20);
                    return 2;
                });

        List<Integer> results = StructuredConcurrency.gatherAll(tasks);

        assertThat(results)
                .as("completion order may differ but result order must match input")
                .containsExactly(0, 1, 2);
    }

@Test
    @Timeout(5)
    @DisplayName("gatherAll: 全部成功时整体耗时近似最长单个任务，而非所有任务之和")
    void gatherAll_runsTasksInParallel() throws Exception {
        // 三个各 100ms 的任务若串行执行将 ≥ 300ms；若并发执行约 100ms。
        Callable<Void> slowTask = () -> {
            Thread.sleep(QUICK.toMillis() * 2);
            return null;
        };
        List<Callable<Void>> tasks = List.of(slowTask, slowTask, slowTask);

        long start = System.nanoTime();
        List<Void> results = StructuredConcurrency.<Void>gatherAll(tasks);
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(results).hasSize(3);
        assertThat(elapsedMillis)
                .as("parallel gatherAll of 3x100ms tasks should finish well below 3x100ms serial time")
                .isLessThan(250L);
    }

    // ============================================================================================
    // gatherAllSuppliers —— Supplier 版本
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("gatherAllSuppliers: 全部任务成功时按输入顺序返回结果列表")
    void gatherAllSuppliers_allSucceed_returnsAllResults() throws Exception {
        List<Supplier<Integer>> suppliers = List.of(
                () -> 10,
                () -> 20,
                () -> 30);

        List<Integer> results = StructuredConcurrency.gatherAllSuppliers(suppliers);

        assertThat(results).containsExactly(10, 20, 30);
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllSuppliers: 空集合返回空列表")
    void gatherAllSuppliers_emptyCollection_returnsEmptyList() throws Exception {
        List<String> results = StructuredConcurrency.gatherAllSuppliers(Collections.<Supplier<String>>emptyList());

        assertThat(results).isEmpty();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllSuppliers: 任一 Supplier 抛异常时整体失败")
    void gatherAllSuppliers_oneFails_propagatesException() {
        assertThatThrownBy(() -> StructuredConcurrency.gatherAllSuppliers(List.<Supplier<String>>of(
                () -> "ok",
                () -> {
                    throw new RuntimeException("supplier boom");
                })))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasStackTraceContaining("supplier boom");
    }

    // ============================================================================================
    // race —— Callable 版本
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("race: 首个成功结果立即返回，慢任务被取消")
    void race_firstSuccessWins_returnsFirstResult() throws Exception {
        String result = StructuredConcurrency.race(List.<Callable<String>>of(
                () -> "fast",
                () -> {
                    Thread.sleep(SAFETY.toMillis());
                    return "slow";
                },
                () -> {
                    Thread.sleep(SAFETY.toMillis());
                    return "slower";
                }));

        assertThat(result).isEqualTo("fast");
    }

    @Test
    @Timeout(5)
    @DisplayName("race: 全部任务失败时抛出异常")
    void race_allFail_throwsException() {
        assertThatThrownBy(() -> StructuredConcurrency.race(List.<Callable<String>>of(
                () -> {
                    throw new IllegalStateException("fail-1");
                },
                () -> {
                    throw new IllegalArgumentException("fail-2");
                })))
                .isInstanceOf(Exception.class);
    }

    @Test
    @Timeout(5)
    @DisplayName("race: 单任务成功时返回该任务结果")
    void race_singleTask_returnsSingleResult() throws Exception {
        String result = StructuredConcurrency.race(List.<Callable<String>>of(() -> "only"));

        assertThat(result).isEqualTo("only");
    }

    @Test
    @Timeout(5)
    @DisplayName("race: 空集合快速失败而非永久阻塞")
    void race_emptyCollection_throwsException() {
        assertThatThrownBy(() -> StructuredConcurrency.<String>race(Collections.<Callable<String>>emptyList()))
                .as("race with no candidates cannot yield a winning result")
                .isInstanceOf(Exception.class);
    }

    @Test
    @Timeout(5)
    @DisplayName("race: 首个成功返回后，其余慢任务被中断")
    void race_slowTasksAreInterrupted() throws Exception {
        AtomicInteger interruptedCount = new AtomicInteger();

        Callable<String> fastWinner = () -> "winner";
        Callable<String> slowSibling = () -> {
            try {
                Thread.sleep(SAFETY.toMillis());
                return "should-not-complete";
            } catch (InterruptedException e) {
                interruptedCount.incrementAndGet();
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        String winner = StructuredConcurrency.race(List.of(fastWinner, slowSibling));

        assertThat(winner).isEqualTo("winner");
        assertThat(interruptedCount.get())
                .as("scope.close() must interrupt the slow sibling once the winner is returned")
                .isEqualTo(1);
    }

    // ============================================================================================
    // raceSuppliers —— Supplier 版本
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("raceSuppliers: 首个 Supplier 结果立即返回")
    void raceSuppliers_allSucceed_returnsFirstResult() throws Exception {
        String result = StructuredConcurrency.raceSuppliers(List.<Supplier<String>>of(
                () -> "primary",
                () -> {
                    try {
                        Thread.sleep(SAFETY.toMillis());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return "backup";
                }));

        assertThat(result).isEqualTo("primary");
    }

    @Test
    @Timeout(5)
    @DisplayName("raceSuppliers: 全部 Supplier 失败时抛出异常")
    void raceSuppliers_allFail_throwsException() {
        assertThatThrownBy(() -> StructuredConcurrency.raceSuppliers(List.<Supplier<String>>of(
                () -> {
                    throw new IllegalStateException("sup-1");
                },
                () -> {
                    throw new IllegalStateException("sup-2");
                })))
                .isInstanceOf(Exception.class)
                .hasStackTraceContaining("sup-");
    }

    // ============================================================================================
    // withDeadline —— 超时模式
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("withDeadline: 任务在超时内完成时返回结果")
    void withDeadline_completesInTime_returnsResult() throws Exception {
        String result = StructuredConcurrency.withDeadline(() -> "quick", Duration.ofSeconds(2));

        assertThat(result).isEqualTo("quick");
    }

    @Test
    @Timeout(5)
    @DisplayName("withDeadline: 任务超过超时时间时抛出 TimeoutException")
    void withDeadline_exceedsDeadline_throwsTimeoutException() {
        Callable<String> slowTask = () -> {
            try {
                Thread.sleep(SAFETY.toMillis());
                return "should-not-return";
            } catch (InterruptedException e) {
                // StructuredTaskScope interrupts on timeout; rethrow to surface cancellation.
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        long start = System.nanoTime();
        assertThatThrownBy(() -> StructuredConcurrency.withDeadline(slowTask, QUICK))
                .isInstanceOf(Exception.class)
                .satisfiesAnyOf(
                        ex -> assertThat(ex).isInstanceOf(StructuredTaskScope.TimeoutException.class),
                        ex -> assertThat(ex).hasCauseInstanceOf(StructuredTaskScope.TimeoutException.class),
                        ex -> assertThat(ex).hasStackTraceContaining("Timeout"),
                        ex -> assertThat(ex).hasStackTraceContaining("InterruptedException"));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMillis)
                .as("withDeadline must return shortly after the deadline, not wait for the slow task")
                .isLessThan(1000L);
    }

    @Test
    @Timeout(5)
    @DisplayName("withDeadline: 任务自身抛异常时按受检异常透传")
    void withDeadline_taskThrows_propagatesException() {
        assertThatThrownBy(() -> StructuredConcurrency.withDeadline(
                () -> {
                    throw new IllegalStateException("inner failure");
                },
                Duration.ofSeconds(2)))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("inner failure");
    }

    @Test
    @Timeout(5)
    @DisplayName("withDeadline: 子任务运行在虚拟线程上")
    void withDeadline_runsOnVirtualThread() throws Exception {
        AtomicReference<Boolean> isVirtual = new AtomicReference<>(Boolean.FALSE);

        StructuredConcurrency.withDeadline(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            return "done";
        }, Duration.ofSeconds(2));

        assertThat(isVirtual.get())
                .as("withDeadline must execute the task on a virtual thread by default")
                .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("withDeadline: 超时触发后任务观察到中断标志")
    void withDeadline_observesInterruptOnTimeout() {
        AtomicReference<Boolean> sawInterrupt = new AtomicReference<>(Boolean.FALSE);

        Callable<String> slowTask = () -> {
            try {
                Thread.sleep(SAFETY.toMillis());
                return "should-not-return";
            } catch (InterruptedException e) {
                sawInterrupt.set(Boolean.TRUE);
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        };

        assertThatThrownBy(() -> StructuredConcurrency.withDeadline(slowTask, QUICK))
                .isInstanceOf(Exception.class);

        assertThat(sawInterrupt.get())
                .as("the task must be interrupted when the deadline fires")
                .isTrue();
    }

    // ============================================================================================
    // gatherBatched —— 分批汇聚
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("gatherBatched: batchSize=1 时每项独立成批，结果仍按输入顺序")
    void gatherBatched_batchSizeOne_processesAllInOrder() throws Exception {
        List<Callable<Integer>> tasks = IntStream.range(0, 5)
                .mapToObj(i -> (Callable<Integer>) () -> i * 10)
                .toList();

        List<Integer> results = StructuredConcurrency.gatherBatched(tasks, 1);

        assertThat(results).containsExactly(0, 10, 20, 30, 40);
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherBatched: batchSize 大于任务总数时所有任务同批执行")
    void gatherBatched_batchSizeLargerThanItems_works() throws Exception {
        List<Callable<Integer>> tasks = List.of(() -> 1, () -> 2, () -> 3);

        List<Integer> results = StructuredConcurrency.gatherBatched(tasks, 100);

        assertThat(results).containsExactly(1, 2, 3);
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherBatched: batchSize 等于任务总数时单批完成")
    void gatherBatched_batchSizeEqualsItems_works() throws Exception {
        List<Callable<String>> tasks = List.of(() -> "a", () -> "b", () -> "c");

        List<String> results = StructuredConcurrency.gatherBatched(tasks, 3);

        assertThat(results).containsExactly("a", "b", "c");
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherBatched: batchSize=0 抛出 IllegalArgumentException")
    void gatherBatched_zeroBatchSize_throwsIllegalArgument() {
        assertThatThrownBy(() -> StructuredConcurrency.<String>gatherBatched(List.of(() -> "x"), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherBatched: batchSize 为负数时抛出 IllegalArgumentException")
    void gatherBatched_negativeBatchSize_throwsIllegalArgument() {
        assertThatThrownBy(() -> StructuredConcurrency.<String>gatherBatched(List.of(() -> "x"), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batchSize");
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherBatched: 空集合返回空列表")
    void gatherBatched_emptyCollection_returnsEmptyList() throws Exception {
        List<Integer> results = StructuredConcurrency.gatherBatched(
                Collections.<Callable<Integer>>emptyList(), 5);

        assertThat(results).isEmpty();
    }

    @Test
    @Timeout(10)
    @DisplayName("gatherBatched: 任一批次内任务失败时整体抛出异常")
    void gatherBatched_oneBatchFails_propagatesException() {
        List<Callable<String>> tasks = new ArrayList<>();
        tasks.add(() -> "ok-0");
        tasks.add(() -> "ok-1");
        tasks.add(() -> {
            throw new IllegalStateException("batch fail");
        });
        tasks.add(() -> "ok-3");

        assertThatThrownBy(() -> StructuredConcurrency.gatherBatched(tasks, 2))
                .isInstanceOf(Exception.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @Timeout(10)
    @DisplayName("gatherBatched: 大批任务按批切片，最终结果按输入顺序合并")
    void gatherBatched_largeBatch_combinesResultsInInputOrder() throws Exception {
        int total = 50;
        List<Callable<Integer>> tasks = IntStream.range(0, total)
                .mapToObj(i -> (Callable<Integer>) () -> i)
                .toList();

        List<Integer> results = StructuredConcurrency.gatherBatched(tasks, 7);

        assertThat(results).hasSize(total);
        assertThat(results).containsExactlyElementsOf(IntStream.range(0, total).boxed().toList());
    }

    // ============================================================================================
    // gatherAllBestEffort —— 尽力汇聚模式
    // ============================================================================================

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffort: 全部任务成功时，全部进入 successes，failures 为空")
    void gatherAllBestEffort_allSucceed_collectsAllSuccesses() {
        StructuredConcurrency.BestEffortResult<Integer> result = StructuredConcurrency.gatherAllBestEffort(List.of(
                (Callable<Integer>) () -> 1,
                (Callable<Integer>) () -> 2,
                (Callable<Integer>) () -> 3));

        assertThat(result.successes()).containsExactly(1, 2, 3);
        assertThat(result.failures()).isEmpty();
        assertThat(result.failedIndices()).isEmpty();
        assertThat(result.hasAnySuccess()).isTrue();
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isZero();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffort: 全部任务失败时，successes 为空，所有异常被收集")
    void gatherAllBestEffort_allFail_collectsAllFailures() {
        StructuredConcurrency.BestEffortResult<String> result = StructuredConcurrency.gatherAllBestEffort(List.of(
                (Callable<String>) () -> {
                    throw new IllegalStateException("boom-0");
                },
                (Callable<String>) () -> {
                    throw new IllegalArgumentException("boom-1");
                },
                (Callable<String>) () -> {
                    throw new RuntimeException("boom-2");
                }));

        assertThat(result.successes()).isEmpty();
        assertThat(result.failures()).hasSize(3);
        assertThat(result.failedIndices()).containsExactly(0, 1, 2);
        assertThat(result.hasAnySuccess()).isFalse();
        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isEqualTo(3);
        assertThat(result.failures().get(0)).isInstanceOf(IllegalStateException.class);
        assertThat(result.failures().get(1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(result.failures().get(2)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffort: 5 任务 2 失败 → 3 成功 + 2 失败明细 + 失败下标")
    void gatherAllBestEffort_partialFail_collectsBoth() {
        StructuredConcurrency.BestEffortResult<String> result = StructuredConcurrency.gatherAllBestEffort(List.of(
                (Callable<String>) () -> "ok-0",
                (Callable<String>) () -> {
                    throw new IllegalStateException("task-1-failed");
                },
                (Callable<String>) () -> "ok-2",
                (Callable<String>) () -> {
                    throw new IllegalArgumentException("task-3-failed");
                },
                (Callable<String>) () -> "ok-4"));

        assertThat(result.successes()).containsExactly("ok-0", "ok-2", "ok-4");
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failedIndices()).containsExactly(1, 3);
        assertThat(result.hasAnySuccess()).isTrue();
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(2);
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffort: 空集合返回空 BestEffortResult，不抛异常")
    void gatherAllBestEffort_emptyCollection_returnsEmptyResult() {
        StructuredConcurrency.BestEffortResult<String> result = StructuredConcurrency.gatherAllBestEffort(
                Collections.<Callable<String>>emptyList());

        assertThat(result.successes()).isEmpty();
        assertThat(result.failures()).isEmpty();
        assertThat(result.failedIndices()).isEmpty();
        assertThat(result.hasAnySuccess()).isFalse();
        assertThat(result.successCount()).isZero();
        assertThat(result.failureCount()).isZero();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffort: 单任务成功场景正常返回")
    void gatherAllBestEffort_singleSuccess_works() {
        StructuredConcurrency.BestEffortResult<String> result = StructuredConcurrency.gatherAllBestEffort(
                List.of((Callable<String>) () -> "only"));

        assertThat(result.successes()).containsExactly("only");
        assertThat(result.failures()).isEmpty();
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isZero();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffort: 受检异常也被捕获并放入 failures")
    void gatherAllBestEffort_checkedExceptionIsCaptured() {
        StructuredConcurrency.BestEffortResult<String> result = StructuredConcurrency.gatherAllBestEffort(List.of(
                (Callable<String>) () -> {
                    throw new java.io.IOException("network-down");
                }));

        assertThat(result.successes()).isEmpty();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0))
                .isInstanceOf(java.io.IOException.class)
                .hasMessage("network-down");
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffort: 失败任务不影响其他成功任务并行执行")
    void gatherAllBestEffort_independentFailuresDoNotBlockOthers() {
        AtomicReference<Boolean> firstDone = new AtomicReference<>(Boolean.FALSE);
        AtomicReference<Boolean> lastDone = new AtomicReference<>(Boolean.FALSE);

        StructuredConcurrency.BestEffortResult<String> result = StructuredConcurrency.gatherAllBestEffort(List.of(
                (Callable<String>) () -> {
                    Thread.sleep(50);
                    firstDone.set(Boolean.TRUE);
                    return "first";
                },
                (Callable<String>) () -> {
                    throw new RuntimeException("middle-boom");
                },
                (Callable<String>) () -> {
                    Thread.sleep(50);
                    lastDone.set(Boolean.TRUE);
                    return "last";
                }));

        assertThat(result.successes()).containsExactly("first", "last");
        assertThat(result.failedIndices()).containsExactly(1);
        assertThat(firstDone.get())
                .as("first success task must complete despite middle failure")
                .isTrue();
        assertThat(lastDone.get())
                .as("last success task must complete despite middle failure")
                .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffort: 子任务运行在虚拟线程上")
    void gatherAllBestEffort_runsOnVirtualThread() {
        AtomicReference<Boolean> isVirtual = new AtomicReference<>(Boolean.FALSE);

        StructuredConcurrency.gatherAllBestEffort(List.of(
                (Callable<String>) () -> {
                    isVirtual.set(Thread.currentThread().isVirtual());
                    return "done";
                }));

        assertThat(isVirtual.get())
                .as("forked best-effort tasks must execute on virtual threads")
                .isTrue();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffortSuppliers: 全成功时无须捕获受检异常")
    void gatherAllBestEffortSuppliers_allSucceed_works() {
        StructuredConcurrency.BestEffortResult<Integer> result = StructuredConcurrency.gatherAllBestEffortSuppliers(List.of(
                () -> 10,
                () -> 20,
                () -> 30));

        assertThat(result.successes()).containsExactly(10, 20, 30);
        assertThat(result.failures()).isEmpty();
    }

    @Test
    @Timeout(5)
    @DisplayName("gatherAllBestEffortSuppliers: Supplier 抛 RuntimeException 同样被捕获")
    void gatherAllBestEffortSuppliers_failureCaptured() {
        StructuredConcurrency.BestEffortResult<String> result = StructuredConcurrency.gatherAllBestEffortSuppliers(List.of(
                () -> "ok",
                () -> {
                    throw new IllegalStateException("supplier-boom");
                },
                () -> "ok-2"));

        assertThat(result.successes()).containsExactly("ok", "ok-2");
        assertThat(result.failedIndices()).containsExactly(1);
        assertThat(result.failures().get(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("supplier-boom");
    }

    @Test
    @Timeout(5)
    @DisplayName("BestEffortResult: 防御性拷贝阻止外部修改成功列表")
    void bestEffortResult_defensiveCopyPreventsExternalMutation() {
        StructuredConcurrency.BestEffortResult<String> result = StructuredConcurrency.gatherAllBestEffort(
                List.of((Callable<String>) () -> "a"));

        assertThatThrownBy(() -> result.successes().add("b"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.failures().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.failedIndices().add(99))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}