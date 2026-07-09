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

import java.util.List;

/**
 * 带结果列表的批量映射结果 —— 记录 {@link BatchProcessor.BatchBuilder#mapParallel} 的执行情况。
 *
 * <p>与 {@link BatchResult} 不同，本结果类型额外承载按<b>输入顺序</b>排列的映射结果列表。
 * 失败项对应的槽位为 {@code null}，调用方可通过 {@code result.results().get(i) == null}
 * 配合 {@code result.errors()} 精确定位失败原因与位置。</p>
 *
 * <h2>结果顺序保证</h2>
 * <ul>
 *   <li>{@link #results()} 的下标与输入集合的下标一一对应</li>
 *   <li>成功项的返回值位于其原始下标位置</li>
 *   <li>失败项的位置为 {@code null}（不影响其它项的偏移）</li>
 *   <li>即使并行完成顺序与输入不一致，结果列表也保持输入顺序</li>
 * </ul>
 *
 * <h3>基本使用</h3>
 * <pre>{@code
 * BatchMappingResult<Long, String> result = BatchProcessor.of(orderIds)
 *     .parallelism(20)
 *     .timeout(Duration.ofMinutes(5))
 *     .mapParallel(orderService::formatOrder);
 *
 * for (int i = 0; i < result.results().size(); i++) {
 *     String value = result.results().get(i);
 *     if (value == null) {
 *         log.warn("第 {} 个元素处理失败", i);
 *     } else {
 *         handle(value);
 *     }
 * }
 * }</pre>
 *
 * @param <T> 输入元素类型（仅用于结果类型推断，实际不存储元素本身）
 * @param <R> 映射结果类型
 * @author richie696
 * @since 1.0.0
 */
public record BatchMappingResult<T, R>(
        int successCount,
        int failureCount,
        List<Throwable> errors,
        List<R> results) {

    /**
     * 规范化构造器：对 {@code errors} 与 {@code results} 均进行防御性拷贝，
     * 保证外部不可修改内部状态。
     *
     * <p>{@code errors} 使用 {@link List#copyOf}（禁止 null 元素，因为异常实例不可为 null）；
     * {@code results} 使用 {@link java.util.Collections#unmodifiableList}，
     * 因为失败/超时项对应的槽位允许为 {@code null}。</p>
     *
     * @throws NullPointerException 当 {@code errors} 或 {@code results} 列表本身为 null 时
     */
    public BatchMappingResult {
        errors = List.copyOf(errors);
        results = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(results));
    }

    /**
     * 是否存在失败项。
     *
     * @return 当且仅当 {@link #failureCount()} 大于 0 时返回 {@code true}
     */
    public boolean hasError() {
        return failureCount > 0;
    }

    /**
     * 获取输入顺序下指定下标处的结果。
     *
     * @param index 元素下标（与输入集合下标一致）
     * @return 成功项返回映射结果；失败项返回 {@code null}
     * @throws IndexOutOfBoundsException 当 {@code index} 越界时
     */
    public R resultAt(int index) {
        return results.get(index);
    }
}