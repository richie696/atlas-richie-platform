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
package com.richie.component.statemachine;

import com.richie.component.statemachine.engine.StateMachineEngine;
import com.richie.component.statemachine.engine.StateTransitionResult;
import com.richie.component.statemachine.storage.StateHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 状态机静态调用门面（ID模式）
 * 仅接受 businessId 与可选上下文属性，避免与领域对象耦合
 */
@Component
public class StateMachine {

    private static final AtomicReference<StateMachineEngine> DELEGATE = new AtomicReference<>();

    private StateMachine() {
    }

    /**
     * 触发状态转换（基础版）
     * 使用场景：仅需触发事件，不额外携带上下文，也不显式传入当前状态。
     * 说明：当前状态将从存储中读取，若不存在则使用状态机初始状态。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识（Long 类型，支持与业务表的数值型主键直接关联查询）
     * @return 状态转换结果（成功/失败、上下文等）
     */
    public static StateTransitionResult fire(Enum<?> stateMachineName, Enum<?> event, Long businessId) {
        return getEngine().fire(stateMachineName, event, businessId);
    }

    /**
     * 触发状态转换（带上下文属性）
     * 使用场景：规则条件/动作需要额外的上下文数据（如操作者、原因、请求参数等）。
     * 说明：上下文属性将注入到 StateContext，可被条件/动作表达式访问。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识
     * @param attributes       额外上下文（可为 null）
     * @return 状态转换结果
     */
    public static StateTransitionResult fire(Enum<?> stateMachineName, Enum<?> event, Long businessId, Map<String, Object> attributes) {
        return getEngine().fire(stateMachineName, event, businessId, attributes);
    }

    /**
     * 触发状态转换（带枚举型当前状态）
     * 使用场景：调用方已知业务的当前状态（如缓存/下游返回），希望显式指定以避免一次读存储。
     * 说明：当显式传入当前状态时，将跳过存储的“当前状态读取”。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识
     * @param currentStateEnum 业务当前状态（枚举，传 null 表示从存储读取）
     * @param <ST>             状态枚举类型参数
     * @return 状态转换结果
     */
    public static <ST extends Enum<ST>> StateTransitionResult fire(Enum<?> stateMachineName, Enum<?> event, Long businessId, ST currentStateEnum) {
        return getEngine().fire(stateMachineName, event, businessId, currentStateEnum);
    }

    /**
     * 触发状态转换（带上下文属性与枚举型当前状态）
     * 使用场景：同时需要上下文数据与显式当前状态的高性能路径（避免一次存储读取）。
     * 说明：适用于对延迟敏感、且业务侧已维护当前状态的场景。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识
     * @param attributes       额外上下文（可为 null）
     * @param currentStateEnum 业务当前状态（枚举，传 null 表示从存储读取）
     * @param <ST>             状态枚举类型参数
     * @return 状态转换结果
     */
    public static <ST extends Enum<ST>> StateTransitionResult fire(Enum<?> stateMachineName, Enum<?> event, Long businessId, Map<String, Object> attributes, ST currentStateEnum) {
        return getEngine().fire(stateMachineName, event, businessId, attributes, currentStateEnum);
    }

    /**
     * 获取当前状态（字符串）
     * 使用场景：仅需展示/存档原始状态字符串（如日志、导出、监控面板）。
     * 说明：若存储中无记录，返回状态机初始状态。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param businessId       业务唯一标识
     * @return 当前状态（字符串）
     */
    public static String getCurrentState(Enum<?> stateMachineName, Long businessId) {
        return getEngine().getCurrentState(stateMachineName, businessId);
    }

    /**
     * 获取当前状态（枚举）
     * 使用场景：希望以强类型使用状态（编译期校验、IDE 补全，更安全的分支判断）。
     * 说明：内部会将存储中的字符串状态转换为指定的枚举类型。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param businessId       业务唯一标识
     * @param stateEnumClass   目标状态枚举类型
     * @param <ST>             状态枚举类型参数
     * @return 当前状态（枚举），无则返回 null
     */
    public static <ST extends Enum<ST>> ST getCurrentState(Enum<?> stateMachineName, Long businessId, Class<ST> stateEnumClass) {
        return getEngine().getCurrentState(stateMachineName, businessId, stateEnumClass);
    }

    /**
     * 检查是否可执行事件
     * 使用场景：按钮态/前置校验/灰度放行等“能力预判”，避免无效触发。
     * 说明：内部会读取（或推断）当前状态，并匹配是否存在针对该事件的可达转换。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param event            触发事件（枚举）
     * @param businessId       业务唯一标识
     * @return true 可执行；false 不可执行
     */
    public static boolean canTransitionTo(Enum<?> stateMachineName, Enum<?> event, Long businessId) {
        return getEngine().canTransitionTo(stateMachineName, event, businessId);
    }

    /**
     * 获取状态历史
     * 使用场景：审计追踪、问题排查、可视化时间线等。
     * 说明：返回该业务ID在该状态机下的历史变更记录（按实现定义的排序返回）。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param businessId       业务唯一标识
     * @return 状态历史记录列表
     */
    public static List<StateHistory> getStateHistory(Enum<?> stateMachineName, Long businessId) {
        return getEngine().getStateHistory(stateMachineName, businessId);
    }

    /**
     * 删除状态机状态数据
     * <p>
     * 使用场景：
     * <ul>
     *     <li>业务对象“完结”或归档后，显式清理该业务在状态机下的当前状态与历史列表</li>
     *     <li>配合长生命周期业务的手工清理策略，避免 Redis 中长期堆积无效状态</li>
     * </ul>
     * 说明：仅删除 Redis 中的状态数据，不影响数据库持久化表（statemachine_state_*），
     * 数据库仍可作为审计/报表的数据源。
     *
     * @param stateMachineName 状态机名称（枚举）
     * @param businessId       业务唯一标识
     */
    public static void deleteState(Enum<?> stateMachineName, Long businessId) {
        getEngine().deleteState(stateMachineName, businessId);
    }

    /**
     * 初始化状态机工具（由 Spring 注入）
     * 使用场景：框架内部使用，业务代码勿调用。
     *
     * @param stateMachineEngine 状态机引擎实例
     */
    @Autowired
    public void setStateMachineEngine(StateMachineEngine stateMachineEngine) {
        if (StateMachine.DELEGATE.get() == null) {
            synchronized (StateMachine.class) {
                if (StateMachine.DELEGATE.get() == null) {
                    StateMachine.DELEGATE.set(stateMachineEngine);
                }
            }
        }
    }

    /**
     * 获取引擎实例（内部）
     *
     * @return 状态机引擎实例
     */
    private static StateMachineEngine getEngine() {
        StateMachineEngine engine = DELEGATE.get();
        if (engine == null) {
            throw new IllegalStateException("状态机引擎未初始化，请确保已启用状态机组件并完成Spring初始化");
        }
        return engine;
    }
}


