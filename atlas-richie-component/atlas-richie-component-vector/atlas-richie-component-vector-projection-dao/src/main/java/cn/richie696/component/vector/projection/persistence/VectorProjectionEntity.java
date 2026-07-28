package cn.richie696.component.vector.projection.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量投影"当前激活版本指针"表，记录业务文档的稳定引用与其当前生效版本之间的映射关系。
 *
 * <p><b>本表职责</b>：将业务侧的稳定引用三元组
 * {@code (tenantId, knowledgeBaseId, documentRef)} 映射到当前正在被检索的
 * {@link VectorProjectionVersionEntity#id}。业务检索通过本表先定位当前 active 版本，
 * 再到向量库按 metadata 过滤后做 TopK 召回；向量库本身不感知这层映射，关系库才是版本切换
 * 与按引用定位的权威来源。</p>
 *
 * <p><b>在 4 表关系中的位置</b>：本表是 {@code rag_vector_projection} 主表，与
 * {@link VectorProjectionVersionEntity}（版本生命周期表）、
 * {@link VectorProjectionRecordEntity}（version→vectorId manifest 表）、
 * {@link VectorProjectionOutboxEntity}（延迟事件表）协同工作。版本切换时只更新本行的
 * {@link #activeVersionId}，被替换的旧版本由 {@code version} 表负责流转到
 * {@code RETIRING}/{@code CLEANED}，按 manifest 精确删除向量库记录，再由 Outbox
 * 通知下游消费者。</p>
 *
 * <p><b>关键不变量</b>：
 * <ul>
 *   <li>{@code (tenantId, knowledgeBaseId, documentRef)} 三元组在 SQL 层唯一
 *       （约束 {@code uk_rag_vector_projection_ref}），同一业务文档引用在投影层只允许存在一行。</li>
 *   <li>{@link #activeVersionId} 为 null 表示该文档尚未建立任何版本（首次重建前的占位行）。</li>
 *   <li>{@link #activeVersionId} 非 null 时必须指向 {@code rag_vector_projection_version}
 *       中 {@code state = ACTIVE} 的一行；版本被取消激活后由事务整体原子替换为新版本 id，
 *       不会出现"两行同时 active"或"active 指向非 ACTIVE 状态行"的脏状态。</li>
 * </ul>
 *
 * <p><b>上下游协作</b>：写入与读取由
 * {@link cn.richie696.component.vector.projection.impl.DefaultVectorProjectionService}
 * 在事务内组合完成；本实体不直接暴露给上层 Service，需经对应
 * {@link VectorProjectionMapper}（MyBatis-Plus {@code BaseMapper}）访问。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Data
@TableName("rag_vector_projection")
public class VectorProjectionEntity {
    /**
     * 主键，调用方生成的 UUID（{@code VARCHAR(64)}，列名 {@code id}）。
     *
     * <p>使用 {@link IdType#INPUT} 而非自增主键，便于跨库迁移、外部追溯以及业务侧在写入前
     * 就拿到 id（例如先插入 {@code version} 再回填本表 {@link #activeVersionId}）。</p>
     */
    @TableId(type = IdType.INPUT)
    private String id;
    /**
     * 租户标识（{@code String}，列名 {@code tenant_id}，{@code VARCHAR(128)} NOT NULL）。
     *
     * <p>多租户隔离的第一维度，业务侧调用方必须显式传入；写入向量库的每条 {@code VectorRecord}
     * 都会把 {@code tenantId} 投影到 metadata，检索时作为硬过滤条件之一。不允许为空，
     * 不允许跨租户覆盖。</p>
     */
    private String tenantId;
    /**
     * 知识库标识（{@code String}，列名 {@code knowledge_base_id}，{@code VARCHAR(128)} NOT NULL）。
     *
     * <p>租户内的二级隔离维度，与业务侧知识库范围严格对齐；同样的 {@code tenantId} 下，
     * 不同 {@code knowledgeBaseId} 之间不可复用 projection 行。检索时与向量库 metadata
     * 中的 {@code knowledgeBaseId} 一致。</p>
     */
    private String knowledgeBaseId;
    /**
     * 业务文档引用（{@code String}，列名 {@code document_ref}，{@code VARCHAR(256)} NOT NULL）。
     *
     * <p>业务文档的稳定主键，可以是文档 id 或外部业务键；与 {@link #tenantId}、
     * {@link #knowledgeBaseId} 三者联合唯一（{@code uk_rag_vector_projection_ref}），
     * 在投影层唯一定位一篇文档。版本切换不会改变本字段值。</p>
     */
    private String documentRef;
    /**
     * 当前激活的 projection version id（{@code String}，列名 {@code active_version_id}，
     * {@code VARCHAR(64)}，可空）。
     *
     * <p>未激活任何版本前为 {@code null}（首次重建前的占位行）。版本切换时由业务事务
     * 整体原子替换为新版本的 id；新版本必须已进入 {@code ACTIVE} 状态才会被回填到此列，
     * 业务检索以本字段为入口。被指向的版本由 {@code rag_vector_projection_version} 表
     * 流转到 {@code RETIRING} 并最终被按 manifest 清理。</p>
     */
    private String activeVersionId;
    /**
     * 创建时间（{@link LocalDateTime}，列名 {@code created_at}，{@code TIMESTAMP} NOT NULL，UTC）。
     *
     * <p>本行首次插入时写入，由调用方在事务内显式赋值（不依赖数据库默认值），保证跨数据库实现
     * 的一致性；不再变更。</p>
     */
    private LocalDateTime createdAt;
    /**
     * 最近一次更新时间（{@link LocalDateTime}，列名 {@code updated_at}，
     * {@code TIMESTAMP} NOT NULL，UTC）。
     *
     * <p>新建行时与 {@link #createdAt} 同值；后续每次 {@link #activeVersionId} 变更
     * （切换、首次激活）都会被业务事务刷新，用于排查"何时被切换到当前版本"。</p>
     */
    private LocalDateTime updatedAt;
}
