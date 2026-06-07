package com.richie.component.statemachine.config;

import com.richie.component.statemachine.config.properties.DbPersistenceMode;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 状态机定义配置
 * <p>
 * 表示从配置文件（YAML/JSON）加载的状态机定义。
 * 包含状态机的基本信息、状态列表、转换规则列表和扩展配置。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class StateMachineDefinition {

    /**
     * 状态机名称，唯一标识一个状态机
     */
    private String name;

    /**
     * 状态机描述，用于说明状态机的业务用途
     */
    private String description;

    /**
     * 状态机级数据库持久化模式
     * <p>
     * 可选值：ASYNC、SYNC。为空时使用全局默认配置。
     */
    private DbPersistenceMode dbPersistenceMode;

    /**
     * 状态定义列表，包含该状态机定义的所有状态
     */
    private List<StateDefinition> states;

    /**
     * 转换定义列表，包含该状态机定义的所有转换规则
     */
    private List<TransitionDefinition> transitions;

    /**
     * 扩展配置，用于存储状态机级别的扩展属性
     */
    private Map<String, Object> extensions;

    /**
     * 状态定义
     * <p>
     * 表示状态机中的一个状态定义，从配置文件中加载。
     *
     */
    @Data
    public static class StateDefinition {
        /**
         * 状态名称，唯一标识一个状态
         */
        private String name;

        /**
         * 状态描述，用于说明状态的业务含义
         */
        private String description;

        /**
         * 状态类型，可选值：INITIAL、NORMAL、FINAL、ERROR，默认为 NORMAL
         */
        private String type = "NORMAL";

        /**
         * 状态级数据库持久化模式
         * <p>
         * 可选值：ASYNC、SYNC。用于覆盖状态机级默认模式。
         */
        private DbPersistenceMode statePersistenceMode;

        /**
         * 状态标签，用于对状态进行分类或标记
         */
        private List<String> tags;

        /**
         * 状态扩展属性，用于存储状态级别的扩展配置
         */
        private Map<String, Object> attributes;
    }

    /**
     * 转换定义
     * <p>
     * 表示状态机中的一个转换规则定义，从配置文件中加载。
     *
     */
    @Data
    public static class TransitionDefinition {
        /**
         * 转换名称，唯一标识一个转换规则
         */
        private String name;

        /**
         * 转换描述，用于说明转换的业务含义
         */
        private String description;

        /**
         * 源状态，转换的起始状态
         */
        private String fromState;

        /**
         * 目标状态，转换的目标状态
         */
        private String toState;

        /**
         * 触发事件，触发此转换的事件名称
         */
        private String event;

        /**
         * 转换条件（SpEL表达式），可选，只有当条件为 true 时才执行转换
         */
        private String condition;

        /**
         * 转换动作（SpEL表达式），可选，转换执行时会被评估
         */
        private String action;

        /**
         * 优先级，数字越大优先级越高，默认为 0
         */
        private int priority = 0;

        /**
         * 转换标签，用于对转换进行分类或标记
         */
        private List<String> tags;

        /**
         * 转换扩展属性，用于存储转换级别的扩展配置（如 reopen: true）
         */
        private Map<String, Object> attributes;
    }
}
