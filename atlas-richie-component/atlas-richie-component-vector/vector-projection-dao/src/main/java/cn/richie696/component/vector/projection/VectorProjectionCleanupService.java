package cn.richie696.component.vector.projection;

/**
 * 延迟清理到期 RETIRING 投影版本的 SPI。
 *
 * <p>本接口是 vector 投影插件清理阶段的对外契约：扫描处于 RETIRING 状态且已超过
 * {@code cleanupAfter} 截止时间的投影版本，按 manifest 中记录的 vectorId 批量从向量库
 * 删除，并在同一关系库事务中将版本状态推进到 CLEANED，清理结果同步写入 Outbox。
 *
 * <p>它解决"如何在不伪造分布式事务的前提下，让旧版本向量数据被可靠回收"的问题——关系库
 * 状态推进与 Outbox 在同一事务中完成，向量库删除是可重试的最终一致操作；即使 provider
 * 不支持 {@code deleteByDocumentId} 或 metadata filter，也能通过 manifest 中的 vectorId
 * 列表精确回收旧数据。
 *
 * <p>调用关系：上层调度器（Quartz / XXL-Job / 业务定时任务）周期性调用本接口；本接口实现
 * 依赖 {@link VectorProjectionVersion} 读取版本信息与 manifest，依赖底层向量提供方的
 * {@code VectorRecordDeleteOperations.deleteByIds} 完成幂等删除，删除完成后向 Outbox
 * 写入清理完成事件供下游订阅。该 SPI 不暴露任何线程或调度器——调用方完全控制调度频率与
 * 并发度，插件不会偷偷启动后台线程。
 *
 * <p>关键不变量 / 失败语义：单次 {@link #cleanupDueProjections(int)} 调用按参数限额处理；
 * 中途任何异常会以实现自定义的方式向上抛出或记录日志，已删除的 vectorId 必须满足幂等
 * 删除语义，重试不会产生重复副作用；状态推进具有幂等性，重复清理同一版本是安全的。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorProjectionCleanupService {
    /**
     * 扫描并清理最多 {@code maxVersions} 个到期 RETIRING 投影版本。
     *
     * <p>判定到期的依据是 {@link VectorProjectionVersion#cleanupAfter()} 已早于当前
     * 时间。该方法按版本维度限额，而不是按 vectorId 维度限额；调用方应根据清理压力与
     * 单次执行耗时选择合理上限，防止单次执行锁定过久。
     *
     * @param maxVersions 单次调用允许处理的版本上限；必须为正整数，由调用方负责取值。
     * @return 实际完成清理的版本数量；返回 {@code 0} 表示当前没有到期版本或全部处理失败。
     * @throws IllegalArgumentException 当 {@code maxVersions} 不是正整数时抛出。
     * @throws RuntimeException         实现自定义的异常，用于表达底层向量库或关系库失败；
     *                                  调用方应捕获并按可重试策略处理；已成功的部分满足幂等。
     */
    int cleanupDueProjections(int maxVersions);
}
