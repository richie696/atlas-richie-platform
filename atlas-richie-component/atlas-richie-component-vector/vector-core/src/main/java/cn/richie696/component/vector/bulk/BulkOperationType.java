package cn.richie696.component.vector.bulk;

/**
 * 可流式执行的向量数据操作类型。
 *
 * <p>出现在 {@link BulkOperationEvent.Started} 和 {@link BulkOperationEvent.Completed} 两条事件上，
 * 是消费者区分同一份反应式流中"入库"和"删除"批次的唯一依据。RAG 上层（{@code VectorProjectionWriter}、
 * 知识库入库门面、运维看板）拿到事件流后按该值路由到不同的下游管道（document 状态机 vs 删除对账）。</p>
 *
 * <p>设计原则：枚举值是反应式流协议的一部分，新增类型时必须同时考虑已落库消费者对未知值的兼容
 * 策略（建议默认走 "log + 跳过"，并把未知值写入死信队列以备升级）。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public enum BulkOperationType {
    /**
     * 写入或更新：把 {@link cn.richie696.component.vector.model.VectorRecord} 嵌入并落库；记录已存在时按 provider 语义覆盖。
     *
     * <p>典型场景：知识库重建、RAG 文档切分入库、用户问答长期记忆写入。当前
     * {@link BulkIngestionPipeline} 唯一的"主路径"即对应此类型。</p>
     */
    UPSERT,
    /**
     * 删除：按 {@code vectorId} 列表走 provider 原生删除路径，不走嵌入-攒批管线。
     *
     * <p>典型场景：文档下架、合规删除、用户主动删除 RAG 历史。当前
     * {@link BulkIngestionPipeline} 主要服务于 UPSERT；DELETE 作为事件协议扩展点保留，
     * 单独的删除编排器在 {@code VectorService.deleteAll} 路径上实现，但事件流协议共享。</p>
     */
    DELETE
}
