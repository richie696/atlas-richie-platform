package cn.richie696.component.vector.bulk;

import java.time.Instant;

/**
 * 批量操作的领域事件流。
 *
 * <p>{@link BulkIngestionPipeline} 在"嵌入 → 攒批 → 持久化"三段流程中产出的所有状态变更都通过
 * 这个 sealed 事件流暴露给上层：开始、单条进入阶段、单条成功、单条失败、批次终态。本质上它是 RAG
 * 入库编排的"反应式 UI 协议"——业务埋点（{@code VectorProjectionWriter}、知识库入库门面、运维看板）
 * 订阅这一份流就能拿到统一的进度和失败归因，而不需要知道具体是哪个 provider。</p>
 *
 * <p>事件不暴露内部异常对象，仅透出稳定的 {@code errorCode}（异常类名）和被截断的 {@code message}，
 * 因而可安全地跨线程、跨进程持久化或转发；{@code operationId} 是整批相关事件的关联键，{@code itemId}
 * 是单条记录在事件流层面的追踪键（不一定等于 provider 内部 vectorId）。</p>
 *
 * <p>消费约束：消费者应保证每批"恰好消费一次" {@link Completed}，即便流以错误终止也建议
 * 配合 {@code onErrorResume} 收尾，因为 {@link BulkIngestionPipeline#execute} 内部已经将
 * 异常降级为事件流，但额外错误（如下游 collector 异常）仍可能让流提前结束。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public sealed

interface BulkOperationEvent permits BulkOperationEvent.Started,
        BulkOperationEvent.ItemStarted, BulkOperationEvent.ItemSucceeded,
        BulkOperationEvent.ItemFailed, BulkOperationEvent.Completed {

    /**
     * 整批事件共享的关联键；同一批入库的所有 {@link BulkOperationEvent} 都具有相同值。
     *
     * @return 由 {@link BulkIngestionPipeline} 在批次开始时生成的 UUID
     */
    String operationId();

    /**
     * 该事件在编排器内的发生时刻（{@link Instant}）。
     *
     * @return 事件发射时的时间戳
     */
    Instant occurredAt();

    /**
     * 整批开始的标记事件。
     *
     * <p>由 {@link BulkIngestionPipeline#execute} 在流的第一个位置 emit；携带本次批次的
     * {@link BulkOperationType}，便于消费者在 UPSERT/DELETE 混合场景下做事件分流。
     * 监听者应以此事件为颗粒度起点分配进度 UI 或打开"批次结束"等待器。</p>
     *
     * @param operationId   批次关联键，与同一批其他 {@link BulkOperationEvent} 共享。
     * @param operationType 本次批次的操作类型（UPSERT / DELETE）。
     * @param occurredAt    事件发生时刻。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record Started(
            String operationId,
            BulkOperationType operationType,
            Instant occurredAt) implements

    BulkOperationEvent {
    }

    /**
     * 单条记录进入某个处理阶段。
     *
     * <p>由 {@link BulkIngestionPipeline} 在每个 item 进入 {@link BulkProcessingStage} 时 emit；
     * 同一 item 可能在 EMBEDDING 和 PERSISTING 两个阶段各产生一条。消费者应通过
     * {@code (operationId, itemId, stage)} 三元组完成进度去重，避免同 item 重复计数。</p>
     *
     * @param operationId 批次关联键。
     * @param itemId      单条记录的事件追踪键（可能为 {@code "unknown"}）。
     * @param stage       当前进入的处理阶段。
     * @param occurredAt  事件发生时刻。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record ItemStarted(
            String operationId,
            String itemId,
            BulkProcessingStage stage,
            Instant occurredAt) implements

    BulkOperationEvent {
    }

    /**
     * 单条记录成功落库的终态事件。
     *
     * <p>只在持久化阶段（{@link BulkProcessingStage#PERSISTING}）成功后 emit，携带 provider 分配的
     * {@code vectorId}（与 {@link VectorRecord#id} 可能相同，也可能由库自生成）。
     * 业务侧可借此机会把"已入库成功"与外部文档状态机对齐、清理重试表或推动下一阶段管线。</p>
     *
     * @param operationId 批次关联键。
     * @param itemId      单条记录的事件追踪键。
     * @param vectorId    provider 侧的向量记录 ID。
     * @param occurredAt  事件发生时刻。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record ItemSucceeded(
            String operationId,
            String itemId,
            String vectorId,
            Instant occurredAt) implements

    BulkOperationEvent {
    }

    /**
     * 单条记录在某个阶段失败的事件。
     *
     * <p>由 {@link BulkIngestionPipeline} 在嵌入或写库失败时 emit：异常被本地 {@code onErrorResume}
     * 捕获、序列化为稳定的 {@code errorCode}（异常类名）和被截断的 {@code message}，从而
     * 不会因为异常对象不可序列化而中断反应式流。同一批中其他 item 仍会继续处理。</p>
     *
     * <p>消费者应根据 {@link BulkProcessingStage} 选择不同的补偿策略：{@code EMBEDDING} 阶段失败
     * 适合重试或切换嵌入模型，{@code PERSISTING} 阶段失败则需要排查 provider 容量/网络或检查
     * 单条记录是否破坏了 schema 约束。</p>
     *
     * @param operationId 批次关联键。
     * @param itemId      单条记录的事件追踪键（解析失败时记为 {@code "unknown"}）。
     * @param stage       失败发生的处理阶段。
     * @param errorCode   异常类名（稳定、不含包路径），便于跨进程转发时仍能定位。
     * @param message     异常消息（被截断到 512 字符以避免事件过大）。
     * @param occurredAt  事件发生时刻。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record ItemFailed(
            String operationId,
            String itemId,
            BulkProcessingStage stage,
            String errorCode,
            String message,
            Instant occurredAt) implements

    BulkOperationEvent {
    }

    /**
     * 整批结束的终态事件。
     *
     * <p>无论中间是否发生失败、是否被外层错误终止，{@link BulkIngestionPipeline} 都会在
     * 反应式流的末尾 emit 一条 {@code Completed} 事件，附带 {@link BulkOperationSummary} 终态统计。
     * 消费者应以此事件作为"批次级清理"的稳定锚点——它保证了对账、释放资源、关闭进度 UI
     * 等操作总能执行一次。</p>
     *
     * @param operationId   批次关联键。
     * @param operationType 本次批次的操作类型。
     * @param summary       终态统计（成功/失败/嵌入调用/写库调用/耗时）。
     * @param occurredAt    事件发生时刻。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    record Completed(
            String operationId,
            BulkOperationType operationType,
            BulkOperationSummary summary,
            Instant occurredAt) implements

    BulkOperationEvent {
    }
}
