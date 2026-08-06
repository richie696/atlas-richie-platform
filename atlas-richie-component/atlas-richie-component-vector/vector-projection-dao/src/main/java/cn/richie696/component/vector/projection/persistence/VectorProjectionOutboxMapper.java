package cn.richie696.component.vector.projection.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * {@link VectorProjectionOutboxEntity} 对应的 MyBatis-Plus Mapper。
 *
 * <p><b>CRUD 边界</b>：本接口<b>不声明任何自定义查询方法</b>，仅通过继承
 * {@link BaseMapper} 获得 MyBatis-Plus 提供的通用 CRUD 能力（{@code insert /
 * updateById / deleteById / deleteByIds / selectById / selectList / selectCount}
 * 等）。到期记录的扫描（{@code eventType = ? AND state = 'PENDING' AND execute_after <= now()}）、
 * 状态推进（{@code PENDING → PROCESSED}）、重试计数累加，全部由业务侧 Job 通过
 * {@code LambdaQueryWrapper} 与 {@link BaseMapper#update} 等通用方法组合完成。</p>
 *
 * <p><b>业务规则不归本 Mapper</b>：退避策略、最大重试阈值、并发安全（行锁或乐观锁）
 * 均由调用方决定，本 Mapper 只负责对 {@link VectorProjectionOutboxEntity} 的单表持久化。
 * 插件不内置任何后台线程，调度入口由业务侧注册。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
@Mapper
public interface VectorProjectionOutboxMapper extends BaseMapper<VectorProjectionOutboxEntity> {
}
