package cn.richie696.component.vector.projection;

import java.time.Duration;
import java.util.Optional;

/**
 * 向量投影版本的创建、激活与失败状态机 SPI。
 *
 * <p>本接口是 vector 投影插件状态机的入口：在关系库中创建一条新的 projection version
 * 记录，并推动状态沿 PREPARING → WRITING → READY → ACTIVE → RETIRING → CLEANED 流转，
 * 发生不可恢复错误时转入 FAILED。
 *
 * <p>它解决"如何让多 provider 场景下的版本切换对业务透明、且失败可诊断可重试"的问题——
 * 每次重建都生成新的 versionId，业务侧只读取当前 ACTIVE 版本；失败版本保留诊断信息但
 * 不阻塞后续重建；同 sourceVersion 的失败重建可以安全重试。
 *
 * <p>调用关系：业务重建流程依次调用
 * {@link #beginRebuild} → {@link VectorProjectionWriter#write} → {@link #activate}；
 * 出错路径调用 {@link #markFailed}。实现内部依赖关系库事务、Outbox 与
 * {@link VectorProjectionVersion} 快照；查询路径 {@link #findVersion} 仅用于状态展示与
 * 调试，不应进入业务热路径。本 SPI 与 {@link VectorProjectionCleanupService} 协作，
 * 由后者完成 RETIRING → CLEANED 的最终推进。
 *
 * <p>关键不变量：(1) 同一 {@link VectorProjectionReference} 同一时刻至多一个 ACTIVE
 * 版本；(2) 状态机不允许倒退——已进入 CLEANED 的版本不可再激活；(3) FAILED 状态保留
 * {@code failureReason} 用于事后追溯；(4) 所有状态推进必须在关系库事务中完成。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorProjectionLifecycleService {
    /**
     * 为指定业务文档创建一个新的投影版本，初始状态为 PREPARING。
     *
     * <p>调用方应立即获取返回的 {@link VectorProjectionVersion#versionId()}，并将其作为
     * 后续 {@link VectorProjectionWriter#write} 与 {@link #activate} 的入参；同
     * {@link VectorProjectionReference} 上已有的 ACTIVE 版本不会被本方法影响，新版本需要
     * 在写入完成后通过 {@link #activate} 才接管检索。
     *
     * @param reference 业务文档的稳定引用，三元组 (tenantId, knowledgeBaseId, documentRef)；
     *                  不允许为空或纯空白。
     * @param specification 本次重建的不可变规格，三元组 (sourceVersion, indexName, embeddingSpaceId)；
     *                      不允许为空或纯空白。
     * @return 新建的投影版本快照，包含 versionId 与状态 PREPARING。
     * @throws IllegalArgumentException 当 reference 或 specification 任一字段为空时。
     * @throws IllegalStateException 当同 reference 已存在同规格处于 PREPARING/WRITING 的版本时，
     *                               由实现选择拒绝或允许重复创建。
     */
    VectorProjectionVersion beginRebuild(VectorProjectionReference reference, VectorProjectionSpecification specification);

    /**
     * 将指定版本从 READY 推进到 ACTIVE，并把同一 {@link VectorProjectionReference} 上的旧
     * ACTIVE 版本转为 RETIRING。
     *
     * <p>调用方应在 {@link VectorProjectionWriter#write} 的 {@link reactor.core.publisher.Flux} 正常完成、且版本
     * 状态已由实现内部推进到 READY 之后再调用本方法。RETIRING 版本的清理延迟由本参数控制——
     * 等待期可作为读流量切换、业务回滚与对账窗口，到期后由
     * {@link VectorProjectionCleanupService#cleanupDueProjections(int)} 回收。
     *
     * @param versionId 目标版本的唯一标识；必须由 {@link #beginRebuild} 生成且当前为 READY。
     * @param cleanupDelay 旧 ACTIVE 版本进入 RETIRING 后，延迟多长时间才允许清理；不能为 null。
     * @throws IllegalArgumentException 当 versionId 为空或 cleanupDelay 为 null 时。
     * @throws IllegalStateException 当版本不存在，或当前状态不允许激活（如仍处于 WRITING、
     *                               已是 FAILED / CLEANED）。
     */
    void activate(String versionId, Duration cleanupDelay);

    /**
     * 将指定版本标记为 FAILED，并记录失败原因。
     *
     * <p>通常在写入流异常、或任何后续校验失败的不可恢复场景下调用；FAILED 状态保留
     * {@code reason} 用于事后追溯与告警，但不会再被激活。同一 sourceVersion 的下一次
     * 重建仍可生成新的 projection version，互不影响。
     *
     * @param versionId 失败版本的唯一标识；不能为空。
     * @param reason 失败原因描述，建议为可定位到具体步骤的短语（不要写入敏感信息）；不能为空。
     * @throws IllegalArgumentException 当 versionId 或 reason 为空 / 纯空白时。
     * @throws IllegalStateException 当版本不存在或已处于终态（CLEANED）。
     */
    void markFailed(String versionId, String reason);

    /**
     * 按 versionId 查询投影版本快照。
     *
     * <p>用于状态展示、运维诊断与上层 UI 拉取；不应进入业务热路径——业务热路径应基于 ACTIVE
     * 版本缓存与索引名直接检索向量。返回值包含最新状态、已写入与失败计数、cleanupAfter
     * 与失败原因（如有）。
     *
     * @param versionId 待查询的版本标识；不能为空。
     * @return 命中时返回版本快照的 {@link Optional}；未命中或已清理后返回
     *         {@link Optional#empty()}。
     * @throws IllegalArgumentException 当 versionId 为空时。
     */
    Optional<VectorProjectionVersion> findVersion(String versionId);
}
