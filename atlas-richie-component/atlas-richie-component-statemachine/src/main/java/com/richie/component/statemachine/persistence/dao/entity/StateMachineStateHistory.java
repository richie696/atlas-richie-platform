package com.richie.component.statemachine.persistence.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 状态机历史记录实体
 * <p>
 * 用于持久化状态机的状态变更历史到数据库。
 * 对应数据库表：statemachine_state_history
 * 
 * <p>
 * 注意：此类使用 Lombok 注解（{@code @NoArgsConstructor}、{@code @AllArgsConstructor}、{@code @Builder}），
 * 构造函数由 Lombok 自动生成。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("statemachine_state_history")
public class StateMachineStateHistory implements Serializable {

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 状态机名称
     */
    private String stateMachine;

    /**
     * 业务对象ID
     * <p>
     * 使用 Long 类型以支持与业务表的数值型主键直接关联查询，避免类型转换带来的性能损失。
     * 
     */
    private Long businessId;

    /**
     * 前一个状态（源状态）
     */
    private String prevState;

    /**
     * 当前状态（目标状态）
     */
    private String currState;

    /**
     * 触发事件名称
     */
    private String eventName;

    /**
     * 状态迁转序列号（单实例单调递增）
     */
    private Long seq;

    /**
     * 状态变更发生时间
     */
    private LocalDateTime occurredAt;

    private static final long serialVersionUID = 1L;
}

