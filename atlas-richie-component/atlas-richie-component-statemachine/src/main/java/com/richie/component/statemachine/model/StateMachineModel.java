package com.richie.component.statemachine.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 状态机运行模型
 * <p>
 * 表示一个完整的状态机定义，包含所有状态和转换规则。
 * 从配置文件（YAML/JSON）加载后转换为该模型对象。
 *
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
public class StateMachineModel {

    /**
     * 状态机名称，唯一标识一个状态机
     */
    private String name;

    /**
     * 状态机描述，用于说明状态机的业务用途
     */
    private String description;

    /**
     * 初始状态，业务对象创建时的默认状态
     */
    private String initialState;

    /**
     * 状态列表，包含该状态机定义的所有状态
     */
    private List<State> states = new ArrayList<>();

    /**
     * 转换列表，包含该状态机定义的所有转换规则
     */
    private List<Transition> transitions = new ArrayList<>();

    /**
     * 版本号，用于状态机定义的版本管理
     */
    private String version = "1.0.0";

    /**
     * 默认构造函数
     */
    public StateMachineModel() {
    }

    /**
     * 构造函数
     *
     * @param name 状态机名称
     */
    public StateMachineModel(String name) {
        this.name = name;
    }

    /**
     * 构造函数
     *
     * @param name        状态机名称
     * @param description 状态机描述
     */
    public StateMachineModel(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 添加状态
     *
     * @param state 状态对象
     */
    public void addState(State state) {
        this.states.add(state);
    }

    /**
     * 添加转换
     *
     * @param transition 转换对象
     */
    public void addTransition(Transition transition) {
        this.transitions.add(transition);
    }

    /**
     * 获取状态
     * <p>
     * 根据状态名称查找对应的状态对象。
     *
     *
     * @param stateName 状态名称
     * @return 状态对象，如果不存在则返回 null
     */
    public State getState(String stateName) {
        return states.stream()
                .filter(state -> state.getName().equals(stateName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取转换
     * <p>
     * 根据源状态和事件查找匹配的转换规则。
     * 如果 event 为 null，则返回所有从指定状态出发的转换。
     *
     *
     * @param fromState 源状态
     * @param event     触发事件，如果为 null 则匹配所有事件
     * @return 匹配的转换规则列表
     */
    public List<Transition> getTransitions(String fromState, String event) {
        return transitions.stream()
                .filter(transition -> (transition.getFromState() == null || transition.getFromState().equals(fromState))
                        && (event == null || event.equals(transition.getEvent())))
                .toList();
    }
}
