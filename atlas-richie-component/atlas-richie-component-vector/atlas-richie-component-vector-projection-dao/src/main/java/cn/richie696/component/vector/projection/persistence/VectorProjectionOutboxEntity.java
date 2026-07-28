package cn.richie696.component.vector.projection.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投影版本生命周期事件表（Outbox），用于承载延迟清理等可重试动作。
 *
 * <p><b>本表职责</b>：在投影主表与版本表的同一关系库事务中，
 * 把"关系库状态变更"与"向量库最终一致性动作"解耦。事务提交后向量库删除仍可能失败
 * （网络抖动、provider 限流、临时不可用等），此时本表记录仍停留在
 * {@code state = PENDING}，等待业务侧 Job 轮询重试；不会出现"关系库声明已成功
 * 而向量库完全没动作"的脏状态，也不会因事务回滚而丢失待执行的清理意图。</p>
 *
 * <p><b>在 4 表关系中的位置</b>：本表是 4 表协作中的"待办队列"——
 * {@link VectorProjectionVersionEntity} 进入 {@code RETIRING}、写完
 * {@link VectorProjectionRecordEntity} manifest 之后，由事务在同一连接里同步
 * 插入一条 {@code PENDING} Outbox 记录；调度 Job 按 {@code (eventType, state, executeAfter)}
 * 扫描（SQL 索引 {@code idx_rag_vector_projection_outbox_due}），成功后改为
 * {@code PROCESSED} 并写入 {@link #processedAt}，失败则累加 {@link #attempts} 并
 * 退后 {@link #executeAfter}。本表不持有向量库原始数据，仅承载"待办事件"。</p>
 *
 * <p><b>关键不变量</b>：
 * <ul>
 *   <li>本表与投影主表/版本表的写入在同一事务内，事务回滚则 Outbox 记录同样回滚，
 *       不会出现"幽灵事件"。</li>
 *   <li>同一事件的处理只能由一个调度实例推进：业务侧应使用 {@code SELECT ... FOR UPDATE}
 *       或乐观锁，避免重复执行。</li>
 *   <li>{@link #attempts} 单调递增；到达业务侧阈值后应转人工或死信队列。</li>
 *   <li>插件<b>不内置调度线程</b>：扫描、退避、状态推进全部由业务侧
 *       (Quartz / XXL-Job / 应用层定时任务) 通过
 *       {@link VectorProjectionOutboxMapper} 与 {@code LambdaQueryWrapper} 完成。</li>
 * </ul>
 *
 * <p><b>上下游协作</b>：写入方为
 * {@link cn.richie696.component.vector.projection.impl.DefaultVectorProjectionService}；
 * 读取与状态推进由业务侧 Job 通过
 * {@link VectorProjectionOutboxMapper} 完成。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@TableName("rag_vector_projection_outbox")
public class VectorProjectionOutboxEntity {
    /**
     * 主键，调用方生成的 UUID（{@code VARCHAR(64)}，列名 {@code id}）。
     *
     * <p>使用 {@link IdType#INPUT} 便于业务侧在事务内同步生成并落库；调度 Job 在失败重试
     * 时通过本字段锁定具体事件行。</p>
     */
    @TableId(type = IdType.INPUT)
    private String id;
    /**
     * 事件类型（{@code String}，列名 {@code event_type}，{@code VARCHAR(64)} NOT NULL）。
     *
     * <p>约定枚举值例如 {@code VECTOR_PROJECTION_CLEANUP}（清理 RETIRING 版本对应的
     * 向量库向量）。未来扩展可继续追加：索引切换完成、失败告警等。所有事件共用本表，
     * 由调度端按本字段分发到不同处理逻辑。</p>
     */
    private String eventType;
    /**
     * 关联的 projection version id（{@code String}，列名 {@code version_id}，
     * {@code VARCHAR(64)} NOT NULL）。
     *
     * <p>指向 {@link VectorProjectionVersionEntity#getId()}，用于在事件被消费时
     * 重新拉取版本当前状态（如 state / cleanupAfter / manifest）。本字段<b>不</b>建立
     * 外键约束，避免与历史版本的生命周期管理耦合。</p>
     */
    private String versionId;
    /**
     * 事件状态（{@code String}，列名 {@code state}，{@code VARCHAR(32)} NOT NULL）。
     *
     * <p>业务约定的有限状态机取值，当前包含但不限于：
     * {@code PENDING}（待处理）、{@code PROCESSED}（已成功完成）。
     * 调度端只扫描 {@code state = PENDING} 且 {@link #executeAfter} 已到期的记录。</p>
     */
    private String state;
    /**
     * 已尝试处理的次数（{@code Integer}，列名 {@code attempts}，{@code INTEGER} NOT NULL DEFAULT 0）。
     *
     * <p>由调度端在每次重试失败后累加；用于实现指数退避（结合 {@link #executeAfter}）
     * 以及在达到业务阈值后触发告警/死信。成功处理后不再变更。</p>
     */
    private Integer attempts;
    /**
     * 最近一次失败的错误信息摘要（{@code String}，列名 {@code last_error}，
     * {@code VARCHAR(1000)}，可空）。
     *
     * <p>由调度端在处理失败时写入，便于人工排查与重放决策；成功处理后清空或保留最近一次
     * 错误由业务侧自行决定，本字段不参与状态机判定。</p>
     */
    private String lastError;
    /**
     * 最早可执行时间（{@link LocalDateTime}，列名 {@code execute_after}，
     * {@code TIMESTAMP} NOT NULL，UTC）。
     *
     * <p>调度端扫描时必须过滤 {@code execute_after <= now()}，从而支持退避与"延迟清理"
     * 语义（如 version 进入 {@code RETIRING} 后等若干小时再真正删除）。事件创建时
     * 通常为当前时间，重试时由业务侧按指数策略后移。</p>
     */
    private LocalDateTime executeAfter;
    /**
     * 处理成功的时间戳（{@link LocalDateTime}，列名 {@code processed_at}，
     * {@code TIMESTAMP}，可空，UTC）。
     *
     * <p>仅在 {@link #state} 推进到 {@code PROCESSED} 时写入；尚未成功处理前为
     * {@code null}。业务侧可基于"非空 + 距今时间"清理已完成的历史事件以控制表膨胀。</p>
     */
    private LocalDateTime processedAt;
    /**
     * 创建时间（{@link LocalDateTime}，列名 {@code created_at}，{@code TIMESTAMP} NOT NULL，UTC）。
     *
     * <p>由调用方在事务内显式赋值，不依赖数据库默认值；写入后不再变更，用于审计与历史归档。</p>
     */
    private LocalDateTime createdAt;
    /**
     * 最近一次更新时间（{@link LocalDateTime}，列名 {@code updated_at}，
     * {@code TIMESTAMP} NOT NULL，UTC）。
     *
     * <p>每次 {@link #state}、{@link #attempts}、{@link #executeAfter} 或
     * {@link #processedAt} 变更时由业务事务刷新，与 {@link #createdAt} 配合用于追溯事件
     * 的全生命周期时间线。</p>
     */
    private LocalDateTime updatedAt;
}
