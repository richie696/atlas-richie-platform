package com.richie.context.common.api.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 通用基础实体类（BOH专用）
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-26 14:16:27
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class BehaviorDomain extends Domain {

    /**
     * 创建人id
     */
    @TableField(value = "create_id", fill = FieldFill.INSERT)
    protected String createId;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    protected LocalDateTime createTime;

    /**
     * 更新人id
     */
    @TableField(value = "update_id", fill = FieldFill.INSERT_UPDATE)
    protected String updateId;

    /**
     * 更新时间
     */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    protected LocalDateTime updateTime;

    /**
     * 删除标识,N:未删除,Y:删除
     */
    @TableLogic
    @TableField(value = "del_flag", fill = FieldFill.INSERT)
    protected String delFlag;

}
