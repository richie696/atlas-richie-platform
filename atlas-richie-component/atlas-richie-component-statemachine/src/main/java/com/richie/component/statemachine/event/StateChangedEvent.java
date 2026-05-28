package com.richie.component.statemachine.event;

import com.richie.component.statemachine.context.StateContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * 状态变更事件
 * <p>
 * Spring 应用事件，当状态机执行状态转换时发布。
 * 可以通过 Spring 的事件监听机制监听状态变更，执行相应的业务逻辑。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Getter
public class StateChangedEvent extends ApplicationEvent {

    /**
     * 状态机名称
     */
    private final String stateMachineName;

    /**
     * 业务对象ID
     */
    private final Long businessId;

    /**
     * 前一状态
     */
    private final String previousState;

    /**
     * 当前状态
     */
    private final String currentState;

    /**
     * 触发事件名称
     */
    private final String eventName;

    /**
     * 上下文属性
     */
    private final Map<String, Object> attributes;

    /**
     * 事件发生时间
     */
    private final LocalDateTime occurredAt = LocalDateTime.now();

    /**
     * 构造函数
     *
     * @param source           事件源对象（通常是 StateContext）
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID
     * @param previousState    前一状态
     * @param currentState     当前状态
     * @param eventName        触发事件名称
     * @param attributes       上下文属性
     */
    public StateChangedEvent(Object source,
                             String stateMachineName,
                             Long businessId,
                             String previousState,
                             String currentState,
                             String eventName,
                             Map<String, Object> attributes) {
        super(source);
        this.stateMachineName = stateMachineName;
        this.businessId = businessId;
        this.previousState = previousState;
        this.currentState = currentState;
        this.eventName = eventName;
        this.attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
    }

    /**
     * 从状态上下文创建状态变更事件
     * <p>
     * 便捷方法，从状态上下文对象创建状态变更事件。
     *
     *
     * @param stateMachineName 状态机名称
     * @param businessId       业务对象ID
     * @param eventName        触发事件名称
     * @param context          状态上下文对象
     * @return 状态变更事件对象
     */
    public static StateChangedEvent from(String stateMachineName, Long businessId, String eventName, StateContext context) {
        return new StateChangedEvent(context,
                stateMachineName,
                businessId,
                context.getPreviousState(),
                context.getCurrentState(),
                eventName,
                context.getAttributes());
    }
}



