package com.richie.component.concurrency.virtual;

import java.util.List;

/**
 * 批量处理结果 —— 记录批量任务的成功/失败统计及异常明细。
 *
 * <p>使用 record 封装，字段不可变；{@link #errors()} 在构造时已通过 {@link List#copyOf}
 * 进行防御性拷贝，对外暴露的列表不可修改。</p>
 *
 * <h2>基本使用</h2>
 * <pre>{@code
 * BatchResult result = BatchProcessor.of(order_list)
 *     .parallelism(20)
 *     .timeout(Duration.ofMinutes(5))
 *     .forEach(this::process_order);
 *
 * if (result.hasError()) {
 *     log.warn("批量处理完成但有失败项: success={}, failure={}",
 *         result.successCount(), result.failureCount());
 *     result.errors().forEach(log::error);
 * }
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 */
public record BatchResult(int successCount, int failureCount, List<Throwable> errors) {

    /**
     * 全局共享的空结果缓存，避免重复分配。
     */
    private static final BatchResult EMPTY = new BatchResult(0, 0, List.of());

    /**
     * 规范化构造器：使用 {@link List#copyOf} 对 {@code errors} 进行防御性拷贝。
     */
    public BatchResult {
        errors = List.copyOf(errors);
    }

    /**
     * 返回一个所有计数均为零、无异常的空结果实例（全局缓存，避免重复分配）。
     *
     * @return 共享的 {@link #EMPTY} 实例
     */
    public static BatchResult empty() {
        return EMPTY;
    }

    /**
     * 是否存在失败项。
     *
     * @return 当且仅当 {@link #failureCount()} 大于 0 时返回 {@code true}
     */
    public boolean hasError() {
        return failureCount > 0;
    }
}
