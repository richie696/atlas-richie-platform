package cn.richie696.component.vector.bulk;

import java.time.Duration;

/**
 * 批量操作结束时的不可变统计快照。
 *
 * <p>它是 {@link BulkOperationEvent.Completed} 携带的终态事实，由 {@link BulkIngestionPipeline}
 * 在批次收尾时构建；RAG 上层（{@code VectorProjectionWriter}、知识库入库门面、运维看板）可以拿
 * 它来驱动"批次是否对账成功"判断、调用 provider 配额评估、容量规划等"批次级"决策。
 * 与单条事件不同，这里给出的是累计数而非流式增量。</p>
 *
 * <p>所有计数器都是单调非递减的（来自 {@link java.util.concurrent.atomic.AtomicLong}）。
 * 计数器描述的是"API 调用次数"而非"记录数"：一次嵌入 API 调用处理一条记录，但一次写库
 * API 调用可能包含 {@code writeBatchSize} 条记录——这是为了便于直接和 provider 计费/SLA 对齐。</p>
 *
 * @param succeeded         整批中成功落库的单条记录数；与 {@link BulkOperationEvent.ItemSucceeded}
 *                          累计条数一一对应；不区分嵌入成功但落库失败的混合情况（落库失败计入
 *                          {@code failed}）。
 * @param failed            整批中失败的 item 数；一条记录在嵌入和落库两阶段都可能失败，但每次
 *                          失败只计一次。对应 {@link BulkOperationEvent.ItemFailed} 累计条数；
 *                          与 {@code succeeded} 一起可还原输入总数（当输入流可数时）。
 * @param elapsed           从 {@link BulkOperationEvent.Started} 到
 *                          {@link BulkOperationEvent.Completed} 的墙钟耗时；用于 SLA 评估，
 *                          不计入业务下游消费的耗时。
 * @param embeddingRequests 嵌入阶段实际触发的 {@code EmbeddingModel} 调用次数，与记录数相等
 *                          （嵌入按单条调用）。用于评估嵌入模型配额与按调用计费的成本。
 * @param writeRequests     写库阶段实际触发的 provider 写入 API 调用次数，一次调用可能写入
 *                          {@code writeBatchSize} 条记录。用于评估 provider 网络/吞吐成本；
 *                          与 {@code writeBatchSize} 的乘积才是写入记录数。
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record BulkOperationSummary(
        long succeeded,
        long failed,
        Duration elapsed,
        long embeddingRequests,
        long writeRequests
) {
    /**
     * 已处理记录数 = {@link #succeeded} + {@link #failed}。
     *
     * <p>为输入可数（{@link java.util.Collection} 或已物化的 {@link reactor.core.publisher.Flux}）的
     * 批次提供"完成度"指标；与预期总数比较即可得未处理（pending）数。注意：对无限流
     * 输入而言"未处理"概念无意义，应仅依赖 {@link #succeeded} / {@link #failed} 做累计评估。</p>
     *
     * @return 已被嵌入阶段或持久化阶段判定的记录总数
     */
    public long processed () {
        return succeeded + failed;
    }
}
