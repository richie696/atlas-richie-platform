package cn.richie696.component.vector.projection.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link VectorProjectionRecordEntity} 对应的 MyBatis-Plus Mapper。
 *
 * <p><b>CRUD 边界</b>：本接口<b>不声明任何自定义查询方法</b>，仅通过继承
 * {@link BaseMapper} 获得 MyBatis-Plus 提供的通用 CRUD 能力（{@code insert /
 * updateById / deleteById / deleteByIds / selectById / selectList /
 * selectBatchIds / selectCount} 等）。manifest 的批量写入（{@code WRITING} 阶段）
 * 、按 {@code versionId} 拉取全部 {@code vectorId}（清理阶段）、
 * 按 version 批量删除（{@code CLEANED} 之后归档），均通过 {@code LambdaQueryWrapper}
 * 配合 {@link BaseMapper} 的通用方法完成。</p>
 *
 * <p><b>provider 无关性</b>：本 Mapper 显式不在 SQL 层暴露任何 provider-specific 过滤条件
 * （例如 metadata filter、collection 限定等），保证清理语义对 7 种 provider 一致成立。
 * 业务规则集中在
 * {@link cn.richie696.component.vector.projection.VectorProjectionCleanupService}。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Mapper
public interface VectorProjectionRecordMapper extends BaseMapper<VectorProjectionRecordEntity> {
}
