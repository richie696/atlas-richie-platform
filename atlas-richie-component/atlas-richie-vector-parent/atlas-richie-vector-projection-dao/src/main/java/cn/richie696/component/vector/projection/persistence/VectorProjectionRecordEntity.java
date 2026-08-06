package cn.richie696.component.vector.projection.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投影版本与向量库 {@code vectorId} 之间的 manifest 记录表。
 *
 * <p><b>本表职责</b>：记录"某次 projection version 在向量库中实际写入了哪些 {@code vectorId}"。
 * 写入阶段（{@code WRITING}）由 {@code VectorProjectionWriter} 在向量库每条写入成功的回调中
 * 同步落库一行；版本进入 {@code RETIRING}/{@code CLEANED} 时，清理 Job 仅凭本表中的
 * {@code vectorId} 列表即可完成"按版本精确删除"，无需依赖 provider 是否支持
 * {@code deleteByDocumentId} 或 metadata filter。</p>
 *
 * <p><b>在 4 表关系中的位置</b>：本表是版本与向量库物理数据之间的"事实账本"——
 * {@link VectorProjectionVersionEntity} 流转到 {@code ACTIVE} 后即不再修改本表；
 * 切换版本时旧版本进入 {@code RETIRING}，由本表的 manifest 驱动精确删除，
 * 删除完成后再通过 {@link VectorProjectionOutboxEntity} 推进到 {@code CLEANED}。
 * 这是"清理语义对 7 种 provider 全部成立"的关键设计点，避免把可用性绑定在少数
 * 支持 metadata 过滤的实现上。</p>
 *
 * <p><b>关键不变量</b>：
 * <ul>
 *   <li>{@code (versionId, vectorId)} 联合唯一（SQL 约束
 *       {@code uk_rag_vector_projection_record}），同一 {@code vectorId} 在同一 version 内
 *       仅落库一次；批量写入需要"先批量 insert"或"数据库层 {@code INSERT ... ON DUPLICATE KEY UPDATE}"。</li>
 *   <li>{@link #vectorId} 必须来自向量库写入成功的真实返回值；不允许凭业务 id 伪造。</li>
 *   <li>清理 Job 通过 {@link #versionId} 拉取本页所有 {@code vectorId} 后批量调用
 *       {@code VectorRecordDeleteOperations.deleteByIds}；删除必须具备幂等性。</li>
 *   <li>本表仅在 {@code WRITING} 阶段累积；版本 {@code READY} 后不再追加，{@code RETIRING}
 *       阶段只读不写。</li>
 * </ul>
 *
 * <p><b>上下游协作</b>：写入方为
 * {@link cn.richie696.component.vector.projection.impl.DefaultVectorProjectionWriter}
 * （在向量库回调中）；读取方为
 * {@link cn.richie696.component.vector.projection.VectorProjectionCleanupService}，
 * 通过 {@link VectorProjectionRecordMapper}（MyBatis-Plus {@code BaseMapper}）访问。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@TableName("rag_vector_projection_record")
public class VectorProjectionRecordEntity {
    /**
     * 主键，调用方生成的 UUID（{@code VARCHAR(64)}，列名 {@code id}）。
     *
     * <p>使用 {@link IdType#INPUT}，由写入端在向量库回调成功后立即生成并落库，与
     * {@link #versionId}、{@link #vectorId} 一同保证 manifest 的唯一性。本字段仅用于
     * 关系库主键定位，不参与向量库语义。</p>
     */
    @TableId(type = IdType.INPUT)
    private String id;
    /**
     * 关联的 projection version id（{@code String}，列名 {@code version_id}，
     * {@code VARCHAR(64)} NOT NULL）。
     *
     * <p>指向 {@link VectorProjectionVersionEntity#getId()}；与本字段建立索引
     * {@code idx_rag_vector_projection_record_version} 以便清理 Job 按 version 拉取
     * 所有 {@code vectorId}。本字段<b>不</b>建立外键约束，避免与版本生命周期管理耦合。</p>
     */
    private String versionId;
    /**
     * 向量库返回的 {@code vectorId}（{@code String}，列名 {@code vector_id}，
     * {@code VARCHAR(256)} NOT NULL）。
     *
     * <p>删除阶段按本字段精确寻址：清理 Job 把它批量交给
     * {@code VectorRecordDeleteOperations.deleteByIds}，避免依赖 provider 的
     * {@code deleteByDocumentId} 或 metadata filter，因此对七种 provider 全部一致工作。
     * 不允许凭业务 id 伪造，必须来自向量库写入成功的真实返回值。</p>
     */
    private String vectorId;
    /**
     * 记录创建时间（{@link LocalDateTime}，列名 {@code created_at}，
     * {@code TIMESTAMP} NOT NULL，UTC）。
     *
     * <p>在向量库写入成功的回调中由调用方落库，与 {@link VectorProjectionVersionEntity#getCreatedAt()}
     * 配合可还原"何时写入该条向量"的时间线；写入后不再变更。</p>
     */
    private LocalDateTime createdAt;
}
