package cn.richie696.component.vector.projection.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link VectorProjectionVersionEntity} 对应的 MyBatis-Plus Mapper。
 *
 * <p><b>CRUD 边界</b>：本接口<b>不声明任何自定义查询方法</b>，仅通过继承
 * {@link BaseMapper} 获得 MyBatis-Plus 提供的通用 CRUD 能力（{@code insert /
 * updateById / deleteById / deleteByIds / selectById / selectList / selectCount}
 * 等）。版本状态推进（{@code PREPARING → WRITING → READY → ACTIVE → RETIRING → CLEANED}）、
 * 按 {@code (state, cleanupAfter)} 过滤到期版本的扫描、按 {@code (projectionId, sourceVersion)}
 * 查询历史重建等场景，均通过 {@code LambdaQueryWrapper} 配合
 * {@link BaseMapper} 的通用方法完成，索引
 * {@code idx_rag_vector_projection_version_cleanup(state, cleanup_after)} 与
 * {@code idx_rag_vector_projection_version_source(projection_id, source_version)}
 * 由 schema 层提供，本 Mapper 不重复建立。</p>
 *
 * <p><b>业务规则不归本 Mapper</b>：状态机合法性校验、激活事务、并发切换保护全部集中在
 * {@link cn.richie696.component.vector.projection.impl.DefaultVectorProjectionService}。
 * 本 Mapper 只负责对 {@link VectorProjectionVersionEntity} 的单表持久化。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Mapper
public interface VectorProjectionVersionMapper extends BaseMapper<VectorProjectionVersionEntity> {
}
