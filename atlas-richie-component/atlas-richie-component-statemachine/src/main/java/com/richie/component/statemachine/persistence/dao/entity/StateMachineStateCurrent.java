package com.richie.component.statemachine.persistence.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 状态机当前状态实体
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("statemachine_state_current")
public class StateMachineStateCurrent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 状态机名称（复合主键的一部分）
     */
    private String stateMachine;

    /**
     * 业务对象ID（复合主键的一部分）
     * <p>
     * 使用 Long 类型以支持与业务表的数值型主键直接关联查询，避免类型转换带来的性能损失。
     */
    private Long businessId;

    /**
     * 当前状态
     */
    private String currentState;

    /**
     * 状态迁转序列号（单实例单调递增）
     */
    private Long seq;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

}

