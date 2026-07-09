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
package com.richie.component.statemachine.storage;

import com.richie.component.statemachine.context.StateContext;

import java.util.List;

/**
 * 状态存储接口
 *
 * @author richie696
 * @since 1.0.0
 */
public interface StateStorage {

    /**
     * 保存当前状态
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @param currentState 当前状态
     * @param context 状态上下文
     */
    void saveCurrentState(String stateMachineName, Long businessId, String currentState, StateContext context);

    /**
     * 获取当前状态
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @return 当前状态
     */
    String getCurrentState(String stateMachineName, Long businessId);

    /**
     * 保存状态历史
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @param fromState 源状态
     * @param toState 目标状态
     * @param event 事件
     * @param context 状态上下文
     */
    void saveStateHistory(String stateMachineName, Long businessId, String fromState, String toState, String event, StateContext context);

    /**
     * 获取状态历史
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @return 状态历史列表
     */
    List<StateHistory> getStateHistory(String stateMachineName, Long businessId);

    /**
     * 删除状态数据
     *
     * @param stateMachineName 状态机名称
     * @param businessId 业务对象ID（Long 类型，支持与业务表的数值型主键直接关联查询）
     */
    void deleteState(String stateMachineName, Long businessId);
} 
