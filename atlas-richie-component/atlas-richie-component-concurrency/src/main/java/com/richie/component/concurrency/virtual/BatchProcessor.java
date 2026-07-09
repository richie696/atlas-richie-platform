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
package com.richie.component.concurrency.virtual;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 批量处理器 —— 基于 JDK 25 {@link StructuredTaskScope} 的并发批量处理工具。
 *
 * <p>提供流式 API，支持并行处理集合中的每一项，<b>单项失败不影响其他项的继续执行</b>，
 * 充分利用虚拟线程的轻量级特性。整体超时由
 * {@link StructuredTaskScope.Configuration#withTimeout} 控制；并发上限由
 * {@link Semaphore} 控制。</p>
 *
 * <h2>两种执行模式</h2>
 * <pre>{@code
 * // 方式一：处理每条记录，不需要返回值（fire-and-forget）
 * BatchResult result = BatchProcessor.of(order_list)
 *     .parallelism(20)
 *     .timeout(Duration.ofMinutes(5))
 *     .forEach(this::process_order);
 *
 * // 方式二：处理每条记录并收集结果（结果按输入顺序保留，失败项对应 null）
 * BatchMappingResult<Long, String> result = BatchProcessor.of(orderIds)
 *     .parallelism(20)
 *     .timeout(Duration.ofMinutes(5))
 *     .mapParallel(orderService::formatOrder);
 * }</pre>
 *
 * <h3>错误隔离</h3>
 * <p>
 * 每个 fork 任务内部捕获所有 {@link Throwable} 并累加到结果中；
 * 整体超时时返回已完成的部分结果。
 * </p>
 *
 * @author richie696
 * @since 1.0.0
 */
@SuppressWarnings("preview")
public final class BatchProcessor {

    private BatchProcessor() {
    }

    /**
     * 创建一个针对指定集合的批量构建器。
     *
     * <p>输入集合会被 {@link List#copyOf} 不可变拷贝，与后续配置期间的修改隔离。</p>
     *
     * @param items 要处理的元素集合
     * @param <T>   元素类型
     * @return 批量构建器
     */
    public static <T> BatchBuilder<T> of(Collection<T> items) {
        return new BatchBuilder<>(List.copyOf(items));
    }

    /**
     * 批量构建器：链式配置并行度、超时并触发执行。
     *
     * <p>默认并行度为 {@code max(2, CPU * 2)}，默认超时为 30 分钟。</p>
     *
     * @param <T> 元素类型
     */
    public static final class BatchBuilder<T> {

        private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);
        private static final int DEFAULT_PARALLELISM =
                Math.max(2, Runtime.getRuntime().availableProcessors() * 2);

        private final List<T> items;
        private int parallelism = DEFAULT_PARALLELISM;
        private Duration timeout = DEFAULT_TIMEOUT;

        private BatchBuilder(List<T> items) {
            this.items = items;
        }

        /**
         * 设置最大并发数（默认 {@code max(2, CPU * 2)}）。
         *
         * @param parallelism 最大并发数
         * @return 当前构建器
         * @throws IllegalArgumentException 如果 {@code parallelism} 小于 1
         */
        public BatchBuilder<T> parallelism(int parallelism) {
            if (parallelism < 1) {
                throw new IllegalArgumentException("parallelism must be >= 1, got: " + parallelism);
            }
            this.parallelism = parallelism;
            return this;
        }

        /**
         * 设置整体批量超时（默认 30 分钟）。
         *
         * @param timeout 超时时长
         * @return 当前构建器
         * @throws NullPointerException     如果 {@code timeout} 为 null
         * @throws IllegalArgumentException 如果 {@code timeout} 为负或零
         */
        public BatchBuilder<T> timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive, got: " + timeout);
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * 处理每一项（不需要返回值）—— fire-and-forget 的主要入口。
         *
         * <p>这是无返回值场景的<b>首选 API</b>：仅关心副作用（写库、推送、通知等）时使用本方法。
         * 如果需要按输入顺序收集每一项的处理结果，请改用 {@link #mapParallel(Function)}。</p>
         *
         * @param consumer 元素消费者
         * @return 批量结果（含成功/失败计数与异常明细）
         */
        public BatchResult forEach(Consumer<T> consumer) {
            return execute(item -> {
                consumer.accept(item);
                return null;
            });
        }

        /**
         * 并行映射每一项并按<b>输入顺序</b>收集结果。
         *
         * <p>与 {@link #forEach(Consumer)} 的关键区别在于：本方法会保留每个元素的映射结果，
         * 返回的 {@link BatchMappingResult} 同时承载统计信息与结果列表。</p>
         *
         * <h4>行为契约</h4>
         * <ul>
         *   <li><b>顺序保证：</b>{@code result.results()} 的下标与输入集合的下标一一对应，
         *       与任务实际完成顺序无关。</li>
         *   <li><b>错误隔离：</b>单个元素抛出异常不会取消其它元素；失败项对应位置
         *       的结果为 {@code null}，异常信息记录在 {@code result.errors()} 中。</li>
         *   <li><b>超时处理：</b>整体超时时，已完成项正常填充结果列表，未完成项
         *       视为失败（{@code failureCount} 增加、对应位置为 {@code null}）。</li>
         *   <li><b>空集合：</b>返回 {@code successCount=0, failureCount=0, errors=[],
         *       results=[]} 的空结果实例。</li>
         * </ul>
         *
         * <h4>结果顺序保证实现</h4>
         * <p>内部使用 {@link AtomicReferenceArray} 按输入下标原子写入结果，
         * 完成后通过 {@link List#copyOf} 构造不可变列表，保证外部读取的顺序
         * 与输入严格一致。</p>
         *
         * @param mapper 元素映射函数
         * @param <R>    映射结果类型
         * @return 包含输入顺序结果列表的批量结果
         */
        public <R> BatchMappingResult<T, R> mapParallel(Function<T, R> mapper) {
            return executeMapping(mapper);
        }

        /**
         * 映射模式的内部执行逻辑：按输入下标原子写入结果，最终构造不可变结果列表。
         *
         * <p>与 {@link #execute(Function)} 的差异：使用 {@link AtomicReferenceArray}
         * 按位置收集结果，并在最终阶段通过 {@link #collectResults} 转成不可变列表。</p>
         *
         * @param mapper 元素映射函数
         * @param <R>    映射结果类型
         * @return 包含输入顺序结果列表的批量结果
         */
        private <R> BatchMappingResult<T, R> executeMapping(Function<T, R> mapper) {
            if (items.isEmpty()) {
                return new BatchMappingResult<>(0, 0, List.of(), List.of());
            }

            var errors = new ConcurrentLinkedQueue<Throwable>();
            var successCount = new AtomicInteger(0);
            @SuppressWarnings("unchecked")
            R[] slots = (R[]) new Object[items.size()];
            var results = new AtomicReferenceArray<>(slots);

            try (var scope = StructuredTaskScope.open(
                    Joiner.<Object>awaitAll(),
                    cfg -> cfg.withTimeout(timeout)
                            .withThreadFactory(VirtualThreadFactory.of("batch-")))) {

                var semaphore = new Semaphore(parallelism);
                for (int i = 0; i < items.size(); i++) {
                    final int index = i;
                    final T item = items.get(i);
                    Callable<Object> task = () -> {
                        boolean acquired = false;
                        try {
                            semaphore.acquire();
                            acquired = true;
                            R result = mapper.apply(item);
                            results.set(index, result);
                            successCount.incrementAndGet();
                        } catch (Throwable e) {
                            errors.add(e);
                            results.set(index, null);
                        } finally {
                            if (acquired) {
                                semaphore.release();
                            }
                        }
                        return null;
                    };
                    scope.fork(task);
                }

                scope.join();
                return new BatchMappingResult<>(
                        successCount.get(),
                        errors.size(),
                        List.copyOf(errors),
                        collectResults(results, items.size()));

            } catch (TimeoutException e) {
                // 超时时已完成项正常填充结果列表，未完成项对应位置保持 null
                // failureCount = items.size() - successCount 包含「运行失败 + 未完成」两类
                int failures = items.size() - successCount.get();
                return new BatchMappingResult<>(
                        successCount.get(),
                        failures,
                        List.copyOf(errors),
                        collectResults(results, items.size()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                int failures = items.size() - successCount.get();
                return new BatchMappingResult<>(
                        successCount.get(),
                        failures,
                        List.copyOf(errors),
                        collectResults(results, items.size()));
            }
        }

        /**
         * 从 {@link AtomicReferenceArray} 按下标顺序构造不可变结果列表。
         *
         * <p>失败的槽位保持为 {@code null}，与成功项共存于同一列表中，
         * 调用方可通过 {@code null} 检测定位失败位置。</p>
         *
         * <p>由于结果列表可能包含 {@code null}（失败/超时槽位），不能使用 {@link List#copyOf}
         * 或 {@link List#of}（它们禁止 null 元素），改用
         * {@link java.util.Collections#unmodifiableList} 包装可空元素列表。</p>
         *
         * @param array  结果数组（按下标写入，槽位可能为 null）
         * @param length 数组有效长度
         * @param <R>    结果元素类型
         * @return 按输入顺序的不可变结果列表（允许 null 元素）
         */
        private static <R> List<R> collectResults(AtomicReferenceArray<R> array, int length) {
            var copy = new ArrayList<R>(length);
            for (int i = 0; i < length; i++) {
                copy.add(array.get(i));
            }
            return java.util.Collections.unmodifiableList(copy);
        }

        /**
         * 内部执行逻辑：fork 所有任务到 {@link StructuredTaskScope}，使用 {@link Semaphore} 限流。
         *
         * <ul>
         *   <li>集合为空时直接返回 {@link BatchResult#empty()}</li>
         *   <li>整体超时时返回已完成的部分结果（剩余项记为失败）</li>
         *   <li>线程被中断时恢复中断标志并返回部分结果</li>
         * </ul>
         *
         * @param function 作用于每个元素的函数
         * @return 批量结果
         */
        private BatchResult execute(Function<T, ?> function) {
            if (items.isEmpty()) {
                return BatchResult.empty();
            }

            var errors = new ConcurrentLinkedQueue<Throwable>();
            var successCount = new AtomicInteger(0);

            try (var scope = StructuredTaskScope.open(
                    Joiner.<Object>awaitAll(),
                    cfg -> cfg.withTimeout(timeout)
                            .withThreadFactory(VirtualThreadFactory.of("batch-")))) {

                var semaphore = new Semaphore(parallelism);
                for (T item : items) {
                    Callable<Object> task = () -> {
                        boolean acquired = false;
                        try {
                            semaphore.acquire();
                            acquired = true;
                            function.apply(item);
                            successCount.incrementAndGet();
                        } catch (Throwable e) {
                            errors.add(e);
                        } finally {
                            if (acquired) {
                                semaphore.release();
                            }
                        }
                        return null;
                    };
                    scope.fork(task);
                }

                scope.join();
                return new BatchResult(successCount.get(), errors.size(), List.copyOf(errors));

            } catch (TimeoutException e) {
                return new BatchResult(successCount.get(),
                        items.size() - successCount.get(),
                        List.copyOf(errors));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new BatchResult(successCount.get(),
                        items.size() - successCount.get(),
                        List.copyOf(errors));
            }
        }
    }
}