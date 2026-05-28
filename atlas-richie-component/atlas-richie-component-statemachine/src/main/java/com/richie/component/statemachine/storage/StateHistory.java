package com.richie.component.statemachine.storage;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 状态历史记录
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class StateHistory {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 状态机名称
     */
    private String stateMachineName;

    /**
     * 业务对象ID
     * <p>
     * 使用 Long 类型以支持与业务表的数值型主键直接关联查询，避免类型转换带来的性能损失。
     * 
     */
    private Long businessId;

    /**
     * 源状态
     */
    private String fromState;

    /**
     * 目标状态
     */
    private String toState;

    /**
     * 触发事件
     */
    private String event;

    /**
     * 状态迁转序列号（单实例单调递增）
     */
    private Long seq;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 备注
     */
    private String remark;

    /**
     * 扩展属性
     */
    private Map<String, Object> attributes;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 默认构造函数
     * <p>
     * 创建时间自动设置为当前时间。
     * 
     */
    public StateHistory() {
        this.createTime = LocalDateTime.now();
    }

    /**
     * 构造函数
     * <p>
     * 创建状态历史记录对象，创建时间自动设置为当前时间。
     * 
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID
     * @param fromState        源状态
     * @param toState          目标状态
     * @param event            触发事件
     */
    public StateHistory(String stateMachineName, Long businessId, String fromState, String toState, String event) {
        this();
        this.stateMachineName = stateMachineName;
        this.businessId = businessId;
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
    }
} 
