package com.richie.context.common.api.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审计领域基类
 *
 * <p>在 {@link Domain} 基础上增加了审计字段（创建人、创建时间、更新人、更新时间）
 * 和逻辑删除标记。适用于大多数需要记录数据变更轨迹的业务表。</p>
 *
 * @author richie696
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AuditDomain extends Domain implements SoftDeletable {

    /**
     * 创建人 ID
     */
    @TableField(value = "create_id", fill = FieldFill.INSERT)
    protected String createId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    protected LocalDateTime createTime;

    /**
     * 更新人 ID
     */
    @TableField(value = "update_id", fill = FieldFill.INSERT_UPDATE)
    protected String updateId;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    protected LocalDateTime updateTime;

    /**
     * 逻辑删除标识
     */
    @TableLogic
    @TableField(value = "deleted", fill = FieldFill.INSERT)
    protected Boolean deleted;

    @Override
    public Boolean getDeleted() {
        return deleted;
    }

    @Override
    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

}
