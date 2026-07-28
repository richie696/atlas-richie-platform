package cn.richie696.component.vector.bulk;

/**
 * 单条记录在批量操作中的可观测处理阶段。
 *
 * <p>它被 {@link BulkOperationEvent#operationId()} 关联的子事件（{@link BulkOperationEvent.ItemStarted}、
 * {@link BulkOperationEvent.ItemFailed}）携带，是 RAG 流程中"现在到哪一步了"和"在哪一步失败"的
 * 最小颗粒度标签。消费者应把它当作反应式 UI 进度条 / 失败补偿路由的唯一依据：值是
 * 有限枚举，因此可以放心在 {@code switch} 中穷尽，避免漏处理新增阶段。</p>
 *
 * <p>设计原则：阶段只描述"在哪一步"，不描述"为什么"——失败原因由
 * {@link BulkOperationEvent.ItemFailed#errorCode()} 单独承载。这样运维侧既能按阶段聚合
 * 失败率，也能按异常类聚合根因。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public enum BulkProcessingStage {
    /**
     * 嵌入阶段：调用 {@code EmbeddingModel} 把 {@link VectorRecord#content} 的内容转为稠密向量。
     *
     * <p>嵌入失败通常意味着：嵌入模型未配置（图像模态）、上游模型调用超时 / 限额、
     * 单条内容过长触发模型拒绝等。RAG 业务可在此阶段决定是否降级（切换更小模型）或跳过该条。</p>
     */
    EMBEDDING,
    /**
     * 持久化阶段：把已嵌入的记录（或库管嵌入路径下的原始记录）批量写入 provider 索引。
     *
     * <p>落库失败通常意味着：provider 容量/网络/索引约束违反、单条数据 schema 不兼容、整批写超时
     * 触发的回滚等。该阶段失败建议对 chunk 内全部 item 走相同的重试或告警策略，因为它们共享一次
     * provider 写调用。</p>
     */
    PERSISTING,
    /**
     * 删除阶段：按 {@code vectorId} 走 provider 原生删除路径（不经嵌入-攒批管线）。
     *
     * <p>用于"按 ID 批量删除"等管理型操作；与嵌入/持久化失败原因通常不同：删除失败的根因更多
     * 是幂等性（idempotency）和并发（race）问题，而非内容合法性。当前
     * {@link BulkIngestionPipeline} 未直接产生该阶段事件，作为未来删除流扩展点保留。</p>
     */
    DELETING
}
