package cn.richie696.component.vector.projection.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link VectorProjectionEntity} 对应的 MyBatis-Plus Mapper。
 *
 * <p><b>CRUD 边界</b>：本接口<b>不声明任何自定义查询方法</b>，仅通过继承
 * {@link BaseMapper} 获得 MyBatis-Plus 提供的通用 CRUD 能力，包括但不限于：
 * {@code insert / updateById / deleteById / deleteByIds / selectById / selectBatchIds /
 * selectList / selectCount} 等。所有按 {@code (tenantId, knowledgeBaseId, documentRef)}
 * 三元组的精确查询、按 id 列表的批量拉取，都由调用方通过 {@code LambdaQueryWrapper}
 * 配合 {@link BaseMapper} 的通用方法完成。</p>
 *
 * <p><b>业务规则不归本 Mapper</b>：版本切换、首次激活、按版本清理等跨表事务动作全部由
 * {@link cn.richie696.component.vector.projection.impl.DefaultVectorProjectionService}
 * 在事务内组合多张表的 Mapper 完成，本 Mapper 只承担对
 * {@link VectorProjectionEntity} 的单表持久化职责，不参与状态机或并发控制。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Mapper
public interface VectorProjectionMapper extends BaseMapper<VectorProjectionEntity> {
}
