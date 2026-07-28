package cn.richie696.component.vector.projection;

import java.time.Instant;

/**
 * 持久化投影版本的公开快照，不暴露 MyBatis 实体。
 *
 * <p>本 record 是 vector 投影插件对外暴露的投影版本视图：把持久化层（关系库 + Outbox）
 * 中关于一条 projection version 的所有关键字段聚合为不可变快照，避免上层直接依赖
 * MyBatis 实体或数据库 schema。
 *
 * <p>它解决"如何在不泄露存储实现细节的前提下，让业务/UI 拿到一个完整、可序列化、可跨进程
 * 传递的版本状态"的问题——record 的不可变语义保证快照可安全共享，{@link #state} 与
 * {@link #cleanupAfter} 字段共同驱动 cleanup 服务的到期判定，{@link #writtenRecords}
 * 与 {@link #failedRecords} 则为监控、对账与告警提供最细粒度计数。
 *
 * <p>调用关系：由 {@link VectorProjectionLifecycleService} 的
 * {@link VectorProjectionLifecycleService#beginRebuild VectorProjectionLifecycleService.beginRebuild}
 * 与 {@link VectorProjectionLifecycleService#findVersion findVersion} 返回；由
 * {@link VectorProjectionCleanupService} 在选择清理目标时按字段过滤；由上层 UI、监控与
 * 对账任务读取展示。{@link #cleanupAfter} 字段直接驱动 cleanup 任务到期判断。
 *
 * <p>关键不变量：record 不可变；任何字段更新都会生成新 snapshot 而非就地修改。终态
 * CLEANED 之后该快照不再变化；FAILED 终态保留 {@link #failureReason} 不为空。
 *
 * @param projectionId 投影维度的稳定标识（同一 {@link VectorProjectionReference} 共享同一 projectionId）。
 * @param versionId 单次重建对应的版本唯一标识，作为写入 / 激活 / 失败状态推进的主键。
 * @param reference 业务文档的稳定引用三元组 (tenantId, knowledgeBaseId, documentRef)。
 * @param specification 本次重建的不可变规格三元组 (sourceVersion, indexName, embeddingSpaceId)。
 * @param state 当前生命周期状态，参见 {@link VectorProjectionState}。
 * @param writtenRecords 已成功写入向量库的记录数（按 manifest 中 vectorId 计数）。
 * @param failedRecords 本次重建累计失败记录数；写入阶段为单条失败累计，激活后保持不变。
 * @param cleanupAfter RETIRING 状态下的清理截止时间；到期后由 cleanup 服务回收；非 RETIRING 时为 null。
 * @param failureReason FAILED 状态下由 {@link VectorProjectionLifecycleService#markFailed} 写入的失败原因；
 *                      其他状态下为 null。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public record VectorProjectionVersion(String projectionId, String versionId, VectorProjectionReference reference,
                                      VectorProjectionSpecification specification, VectorProjectionState state,
                                      int writtenRecords, int failedRecords, Instant cleanupAfter, String failureReason) {
}
