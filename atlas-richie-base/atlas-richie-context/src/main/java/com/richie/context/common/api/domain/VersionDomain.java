package com.richie.context.common.api.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 乐观锁领域基类
 *
 * <p>在 {@link Domain} 基础上增加了乐观锁版本字段 {@code version}。
 * 适用于需要防止并发更新的业务表。
 * 配合 MyBatis-Plus {@code OptimisticLockerInnerInterceptor} 使用。</p>
 *
 * @author richie696
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class VersionDomain extends Domain {

    /**
     * 乐观锁版本号
     */
    @Version
    @TableField(fill = FieldFill.INSERT)
    protected Integer version;

}
