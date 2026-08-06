package cn.richie696.component.vector.projection;

/**
 * 向量投影版本的生命周期状态。
 *
 * <p>本枚举是 vector 投影插件状态机的合法状态集合，规范了 {@link VectorProjectionVersion}
 * 在 {@link VectorProjectionLifecycleService} 推进下的所有可能取值。
 *
 * <p>状态机定义：
 * <pre>
 * PREPARING → WRITING → READY → ACTIVE → RETIRING → CLEANED
 *                     └──────────────→ FAILED
 * </pre>
 *
 * <p>它解决"如何让向量投影版本的推进路径可枚举、可校验、可观测"的问题——把分散在
 * lifecycle 实现里的字符串字面量收敛为一组有限状态值，关系库表 schema、Outbox 事件、
 * 监控指标与 UI 展示都基于本枚举。
 *
 * <p>调用关系：被持久化到关系库的投影版本表（{@code state} 列），由 lifecycle 实现负责
 * 推进；由 {@link VectorProjectionCleanupService} 识别 RETIRING 状态以执行清理；由上层
 * UI、监控与告警用于状态展示与统计聚合。
 *
 * <p>关键不变量：(1) 同一 {@link VectorProjectionReference} 同一时刻至多一个 ACTIVE；
 * (2) 终态 CLEANED 与 FAILED 不允许再被激活或回到前置状态；(3) 状态机不允许倒退，由
 * lifecycle 实现在事务内校验；(4) FAILED 与 CLEANED 是终态，PREPARING 是起点。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public enum VectorProjectionState {
    /**
     * 已创建 projection version 记录，等待写入流开始；{@link VectorProjectionLifecycleService#beginRebuild} 后的初始状态。
     */
    PREPARING,
    /**
     * 写入流订阅成功，正在向向量库写入向量与 manifest；尚未收到全部完成事件。
     */
    WRITING,
    /**
     * 写入流已正常完成，全部 vectorId 已记录到 manifest，等待业务侧显式 activate。
     */
    READY,
    /**
     * 当前业务可检索的目标版本；同一 {@link VectorProjectionReference} 同一时刻有且仅有一个 ACTIVE。
     */
    ACTIVE,
    /**
     * 被新 ACTIVE 替换下来的旧版本；处于可清理的"宽限期"，到期后由 cleanup 任务按 manifest 精确回收。
     */
    RETIRING,
    /**
     * 写入或激活过程中发生不可恢复错误；保留 {@code failureReason} 不再被激活，等待业务侧重试或人工介入。
     */
    FAILED,
    /**
     * 已被清理任务从向量库删除并完成 Outbox 事件，生命周期结束；为不可逆终态。
     */
    CLEANED
}
