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
package com.richie.component.statemachine.context;

import com.richie.component.statemachine.model.Transition;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 状态上下文
 * <p>
 * 状态转换过程中的上下文对象，包含当前状态、前一状态、触发事件、转换规则等信息。
 * 可以在 SpEL 表达式中通过 context 变量访问这些信息。
 *
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class StateContext {

    /**
     * 当前状态
     */
    private String currentState;

    /**
     * 前一状态
     */
    private String previousState;

    /**
     * 触发事件
     */
    private String event;

    /**
     * 当前转换规则
     */
    private Transition transition;

    /**
     * 创建时间
     */
    private LocalDateTime createTime = LocalDateTime.now();

    /**
     * 更新时间
     */
    private LocalDateTime updateTime = LocalDateTime.now();

    /**
     * 扩展属性
     * <p>
     * 用于存储状态转换过程中的自定义属性，可以在 SpEL 表达式中通过 context.attributes['key'] 访问。
     *
     */
    private Map<String, Object> attributes = new HashMap<>();

    /**
     * 默认构造函数
     * <p>
     * 创建时间和更新时间自动设置为当前时间。
     * 
     */
    public StateContext() {
    }

    /**
     * 构造函数
     *
     * @param currentState 当前状态
     */
    public StateContext(String currentState) {
        this.currentState = currentState;
    }

    /**
     * 构造函数
     *
     * @param currentState 当前状态
     * @param event         触发事件
     */
    public StateContext(String currentState, String event) {
        this.currentState = currentState;
        this.event = event;
    }

    /**
     * 设置属性
     * <p>
     * 设置上下文属性，这些属性可以在 SpEL 表达式中通过 context.attributes['key'] 访问。
     *
     *
     * @param key   属性键
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * 获取属性
     * <p>
     * 获取上下文属性，如果属性不存在则返回 null。
     * 
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回 null
     */
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * 获取属性（带默认值）
     * <p>
     * 获取上下文属性，如果属性不存在则返回默认值。
     * 
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在则返回默认值
     */
    public Object getAttribute(String key, Object defaultValue) {
        return this.attributes.getOrDefault(key, defaultValue);
    }

    /**
     * 移除属性
     *
     * @param key 属性键
     * @return 被移除的属性值，如果不存在则返回 null
     */
    public Object removeAttribute(String key) {
        return this.attributes.remove(key);
    }

    /**
     * 更新修改时间
     * <p>
     * 将 updateTime 更新为当前时间。
     * 
     */
    public void updateTime() {
        this.updateTime = LocalDateTime.now();
    }
}
