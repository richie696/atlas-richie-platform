/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.statemachine.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.Map;

/**
 * 状态转换定义
 * <p>
 * 定义从一个状态到另一个状态的转换规则，包括触发事件、转换条件、转换动作等。
 * 支持 Spring SpEL 表达式进行条件判断和动作执行。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode
public class Transition {

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
     * 转换条件（SpEL表达式）
     * <p>
     * 可选，如果设置了条件，只有当条件表达式返回 true 时才会执行转换。
     * 在表达式中可以使用 context.attributes['key'] 访问上下文属性。
     *
     */
    private String condition;

    /**
     * 转换动作（SpEL表达式）
     * <p>
     * 可选，转换执行时会被评估的表达式，可以用于计算值等操作。
     * SpEL read-only 上下文阻止方法调用，请使用 context.attributes['key'] = value 形式（属性赋值）。
     *
     */
    private String action;

    /**
     * 优先级
     * <p>
     * 当存在多个匹配的转换规则时，优先级高的规则会先执行。
     * 数字越大优先级越高，默认为 0。
     * 
     */
    private int priority = 0;

    /**
     * 转换扩展属性（用于白名单重开等：attributes.reopen=true）
     * <p>
     * 用于存储转换规则的扩展属性，例如：
     * - reopen: true - 允许从 FINAL/ERROR 状态转换
     * 
     */
    private Map<String, Object> attributes;

    /**
     * 默认构造函数
     */
    public Transition() {
    }

    /**
     * 构造函数
     *
     * @param name      转换名称
     * @param fromState 源状态
     * @param toState   目标状态
     */
    public Transition(String name, String fromState, String toState) {
        this.name = name;
        this.fromState = fromState;
        this.toState = toState;
    }

    /**
     * 构造函数
     *
     * @param name      转换名称
     * @param fromState 源状态
     * @param toState   目标状态
     * @param event     触发事件
     */
    public Transition(String name, String fromState, String toState, String event) {
        this.name = name;
        this.fromState = fromState;
        this.toState = toState;
        this.event = event;
    }
} 
