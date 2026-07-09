/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

/**
 * 状态定义
 * <p>
 * 表示状态机中的一个状态，包含状态名称、描述和类型。
 * 状态类型用于区分初始状态、普通状态、终态和错误状态。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode
public class State {

    /**
     * 状态名称，唯一标识一个状态
     */
    private String name;

    /**
     * 状态描述，用于说明状态的业务含义
     */
    private String description;

    /**
     * 状态类型，默认为 NORMAL（普通状态）
     */
    private StateType type = StateType.NORMAL;

    /**
     * 状态类型枚举
     * <p>
     * 用于区分不同类型的状态，影响状态转换的行为：
     * - INITIAL: 初始状态，业务对象创建时的默认状态
     * - NORMAL: 普通状态，可以正常进行状态转换
     * - FINAL: 终态，默认不允许转换（除非转换规则设置了 reopen=true）
     * - ERROR: 错误状态，默认不允许转换（除非转换规则设置了 reopen=true）
     * 
     */
    public enum StateType {
        /**
         * 正常状态，可以正常进行状态转换
         */
        NORMAL,
        /**
         * 初始状态，业务对象创建时的默认状态
         */
        INITIAL,
        /**
         * 终态状态，默认不允许转换（除非转换规则设置了 reopen=true）
         */
        FINAL,
        /**
         * 异常状态，默认不允许转换（除非转换规则设置了 reopen=true）
         */
        ERROR
    }

    /**
     * 默认构造函数
     */
    public State() {
    }

    /**
     * 构造函数
     *
     * @param name 状态名称
     */
    public State(String name) {
        this.name = name;
    }

    /**
     * 构造函数
     *
     * @param name        状态名称
     * @param description 状态描述
     */
    public State(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * 构造函数
     *
     * @param name        状态名称
     * @param description 状态描述
     * @param type        状态类型
     */
    public State(String name, String description, StateType type) {
        this.name = name;
        this.description = description;
        this.type = type;
    }
} 
