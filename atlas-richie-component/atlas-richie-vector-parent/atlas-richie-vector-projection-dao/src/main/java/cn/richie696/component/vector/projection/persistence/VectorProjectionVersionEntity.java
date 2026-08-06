package cn.richie696.component.vector.projection.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次投影重建对应的版本实体，承载"写入 / 激活 / 清理"全生命周期字段。
 *
 * <p><b>本表职责</b>：以"行 = 一次重建尝试"为单位，记录每一次向量投影重建的状态机推进、
 * 写入统计、激活时间、下线窗口与失败诊断。一个 {@link VectorProjectionEntity} 可同时存在
 * 多条版本，但只有一条进入 {@code ACTIVE} 状态被业务检索使用；其余版本按
 * {@link #cleanupAfter} 进入 {@code RETIRING} 并最终清理。即便重建中途失败，旧版本仍可
 * 继续被检索，失败诊断与重试也不影响线上数据。</p>
 *
 * <p><b>在 4 表关系中的位置</b>：本表是版本生命周期的事实来源，与
 * {@link VectorProjectionEntity}（激活版本指针）、{@link VectorProjectionRecordEntity}
 * （version→vectorId manifest）、{@link VectorProjectionOutboxEntity}（延迟清理事件）
 * 三表协同工作。版本状态推进驱动主表 {@code active_version_id} 切换；进入 {@code RETIRING}
 * 时由 manifest 表精确删除向量库数据，Outbox 表承载失败重试。</p>
 *
 * <p><b>状态机</b>（{@link #state} 取值，对应枚举 {@code VectorProjectionState}）：
 * <pre>
 *   PREPARING ──► WRITING ──► READY ──► ACTIVE ──► RETIRING ──► CLEANED
 *                       └──────────────► FAILED
 * </pre>
 * <ul>
 *   <li>{@code PREPARING}：版本事务已开启，资源尚未真正写入向量库。</li>
 *   <li>{@code WRITING}：向量库逐条写入中，{@link #writtenRecords} 与
 *       {@link #failedRecords} 持续累加。</li>
 *   <li>{@code READY}：向量库写入全部完成，可被 {@code activate}。</li>
 *   <li>{@code ACTIVE}：已写入主表 {@code active_version_id}，业务检索入口。</li>
 *   <li>{@code RETIRING}：已被新版本替换，等待 {@link #cleanupAfter} 到期后清理。</li>
 *   <li>{@code FAILED}：写入失败，{@link #failureReason} 有值，可由相同
 *       {@link #sourceVersion} 重新触发重建。</li>
 *   <li>{@code CLEANED}：向量库数据已被按 manifest 删除完毕，可归档。</li>
 * </ul>
 *
 * <p><b>关键不变量</b>：
 * <ul>
 *   <li>同一 {@code (projectionId, sourceVersion)} 可对应多条版本（重建时生成新行而非覆盖），
 *       SQL 索引 {@code idx_rag_vector_projection_version_source} 加速按这两个字段查询。</li>
 *   <li>同一 {@link VectorProjectionEntity} 任意时刻最多只有一条 {@code state = ACTIVE} 的版本。</li>
 *   <li>失败字段（{@link #failedRecords} / {@link #failureReason}）独立保留，使相同
 *       {@link #sourceVersion} 的多次重建互不污染，便于安全重试。</li>
 * </ul>
 *
 * <p><b>上下游协作</b>：状态机推进、激活、清理由
 * {@link cn.richie696.component.vector.projection.impl.DefaultVectorProjectionService}
 * 在事务内组合完成；本实体不直接暴露给上层 Service，需经对应
 * {@link VectorProjectionVersionMapper}（MyBatis-Plus {@code BaseMapper}）访问。
 * 调度端按索引 {@code idx_rag_vector_projection_version_cleanup(state, cleanup_after)}
 * 高效扫描到期版本。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@TableName("rag_vector_projection_version")
public class VectorProjectionVersionEntity {
    /**
     * 主键，调用方生成的 UUID（{@code VARCHAR(64)}，列名 {@code id}）。
     *
     * <p>使用 {@link IdType#INPUT}，便于业务侧在事务开启时立即拿到 id 并贯穿
     * {@link #writtenRecords} 累加、{@link VectorProjectionRecordEntity} 写入、
     * {@link VectorProjectionOutboxEntity} 投递等多个步骤。</p>
     */
    @TableId(type = IdType.INPUT)
    private String id;
    /**
     * 所属 projection 主表 id（{@code String}，列名 {@code projection_id}，
     * {@code VARCHAR(64)} NOT NULL）。
     *
     * <p>指向 {@link VectorProjectionEntity#getId()}；SQL 索引
     * {@code idx_rag_vector_projection_version_source(projection_id, source_version)}
     * 加速按本字段查询。本字段<b>不</b>建立外键约束，避免与主表生命周期管理耦合。</p>
     */
    private String projectionId;
    /**
     * 业务侧的源文档版本标识（{@code String}，列名 {@code source_version}，
     * {@code VARCHAR(128)} NOT NULL）。
     *
     * <p>由业务侧传入（与文档原始版本号、ETag、Hash 等语义相关）；相同 {@code sourceVersion}
     * 的多次重建会<b>生成新版本行而非覆盖</b>——失败字段独立保留，便于保留失败诊断并安全重试，
     * 不会与历史重建数据互相污染。</p>
     */
    private String sourceVersion;
    /**
     * 本次重建使用的向量库索引名（{@code String}，列名 {@code index_name}，
     * {@code VARCHAR(256)} NOT NULL）。
     *
     * <p>写入阶段按本字段路由到对应 provider 索引；与 {@link #embeddingSpaceId} 共同决定
     * 向量语义空间，版本切换若任一项变更就必须新建版本。</p>
     */
    private String indexName;
    /**
     * 嵌入空间标识（{@code String}，列名 {@code embedding_space_id}，
     * {@code VARCHAR(256)} NOT NULL）。
     *
     * <p>标识本次重建使用的 embedding 模型 / 维度 / 归一化方案；不同 {@code embeddingSpaceId}
     * 之间的向量<b>不可比较</b>（距离无意义），必须随版本固化下来，避免后续重建误用旧空间
     * 的 {@code vectorId}。</p>
     */
    private String embeddingSpaceId;
    /**
     * 当前生命周期状态（{@code String}，列名 {@code state}，{@code VARCHAR(32)} NOT NULL）。
     *
     * <p>取值对应 {@code VectorProjectionState} 枚举：
     * {@code PREPARING / WRITING / READY / ACTIVE / RETIRING / FAILED / CLEANED}。
     * 状态机推进由
     * {@link cn.richie696.component.vector.projection.impl.DefaultVectorProjectionService}
     * 在事务内完成；扫描到期版本时通常按 {@code state = RETIRING AND cleanup_after <= now()}
     * 过滤（SQL 索引 {@code idx_rag_vector_projection_version_cleanup}）。</p>
     */
    private String state;
    /**
     * 已成功写入的记录数（{@code Integer}，列名 {@code written_records}，
     * {@code INTEGER} NOT NULL DEFAULT 0）。
     *
     * <p>在 {@code WRITING} 阶段按向量库写入成功的回调逐条累加；{@code READY} 时不再变更。
     * 用于进度展示、失败重试的"已完成数量"基准，与 {@link VectorProjectionRecordEntity}
     * 的 manifest 行数保持一致。</p>
     */
    private Integer writtenRecords;
    /**
     * 已记录失败的记录数（{@code Integer}，列名 {@code failed_records}，
     * {@code INTEGER} NOT NULL DEFAULT 0）。
     *
     * <p>仅用于统计与告警；<b>不阻塞其它记录的继续写入</b>。在 {@code READY} 阶段若
     * {@link #failedRecords} &gt; 0，{@link #failureReason} 应保留最近一次摘要。</p>
     */
    private Integer failedRecords;
    /**
     * 失败原因摘要（{@code String}，列名 {@code failure_reason}，
     * {@code VARCHAR(1000)}，可空）。
     *
     * <p>仅在状态为 {@code FAILED} 或本次写入存在失败项时有值；用于人工排查与重试决策。
     * 成功路径下为 {@code null}；不参与状态机判定。</p>
     */
    private String failureReason;
    /**
     * 计划进入 {@code RETIRING} 后允许清理的时间戳（{@link LocalDateTime}，列名
     * {@code cleanup_after}，{@code TIMESTAMP}，可空，UTC）。
     *
     * <p>由激活时配置的下线窗口（{@code platform.component.vector.projection.cleanup-delay}）
     * 决定；进入 {@code RETIRING} 时由业务侧回填。清理 Job 扫描条件为
     * {@code state = RETIRING AND cleanup_after &lt;= now()}，配合索引
     * {@code idx_rag_vector_projection_version_cleanup(state, cleanup_after)}
     * 高效定位到期版本。</p>
     */
    private LocalDateTime cleanupAfter;
    /**
     * 实际进入 {@code ACTIVE} 的时间戳（{@link LocalDateTime}，列名 {@code activated_at}，
     * {@code TIMESTAMP}，可空，UTC）。
     *
     * <p>仅在状态推进到 {@code ACTIVE} 时由 {@code activate} 事务写入；从未激活或中途失败
     * 的版本为 {@code null}。与 {@link VectorProjectionEntity#getUpdatedAt()} 配合可还原
     * "何时上线 / 何时被替换" 的完整时间线。</p>
     */
    private LocalDateTime activatedAt;
    /**
     * 创建时间（{@link LocalDateTime}，列名 {@code created_at}，{@code TIMESTAMP} NOT NULL，UTC）。
     *
     * <p>本行首次插入（{@code PREPARING} 阶段）时由调用方在事务内显式赋值，不依赖数据库默认值；
     * 写入后不再变更。</p>
     */
    private LocalDateTime createdAt;
    /**
     * 最近一次更新时间（{@link LocalDateTime}，列名 {@code updated_at}，
     * {@code TIMESTAMP} NOT NULL，UTC）。
     *
     * <p>每次 {@link #state} 推进、{@link #writtenRecords} / {@link #failedRecords}
     * 累加、{@link #cleanupAfter} / {@link #activatedAt} 变更时由业务事务刷新，用于审计
     * 与"何时切到当前状态"的问题定位。</p>
     */
    private LocalDateTime updatedAt;
}
