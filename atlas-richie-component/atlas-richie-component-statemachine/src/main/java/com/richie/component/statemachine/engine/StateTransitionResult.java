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
package com.richie.component.statemachine.engine;

import com.richie.component.statemachine.context.StateContext;
import lombok.Data;

/**
 * 状态转换结果
 * <p>
 * 表示状态转换操作的执行结果，包含成功/失败状态、错误消息和状态上下文。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class StateTransitionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误消息
     */
    private String errorMessage;

    /**
     * 状态上下文
     */
    private StateContext context;

    /**
     * 创建成功结果
     *
     * @param context 状态上下文，包含转换后的状态信息
     * @return 成功的结果对象
     */
    public static StateTransitionResult success(StateContext context) {
        StateTransitionResult result = new StateTransitionResult();
        result.setSuccess(true);
        result.setContext(context);
        return result;
    }

    /**
     * 创建失败结果
     *
     * @param errorMessage 错误消息，描述失败的原因
     * @return 失败的结果对象
     */
    public static StateTransitionResult failure(String errorMessage) {
        StateTransitionResult result = new StateTransitionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    /**
     * 获取当前状态
     * <p>
     * 从状态上下文中获取转换后的当前状态。
     * 
     *
     * @return 当前状态（字符串），如果上下文为 null 或状态未设置则返回 null
     */
    public String getCurrentState() {
        return context != null ? context.getCurrentState() : null;
    }

    /**
     * 获取前一状态
     * <p>
     * 从状态上下文中获取转换前的状态。
     * 
     *
     * @return 前一状态（字符串），如果上下文为 null 或状态未设置则返回 null
     */
    public String getPreviousState() {
        return context != null ? context.getPreviousState() : null;
    }

    // 已移除 getBusinessObject()，对象模式已废弃
} 
