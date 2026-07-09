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
package com.richie.component.statemachine.config;

import com.richie.component.statemachine.config.properties.DbPersistenceMode;

import java.util.Optional;

/**
 * 持久化模式解析器
 * <p>
 * 解析优先级遵循“就近覆盖”原则：
 * state > machine > default(ASYNC)。
 *
 * @author richie696
 * @since 1.0.0
 */
public final class StatePersistenceModeResolver {

    private StatePersistenceModeResolver() {
    }

    /**
     * 解析状态机级（machine）持久化模式。
     * <p>
     * 规则：
     * 1) 若 definition 为空，返回全局默认（未配置时回退 ASYNC）；
     * 2) 若 definition.dbPersistenceMode 有值，直接使用；
     * 3) 否则回落全局默认（未配置时回退 ASYNC）。
     *
     * @param definition 状态机定义
     * @param globalDefaultMode 全局默认模式
     * @return 状态机级持久化模式，永不返回 null
     */
    public static DbPersistenceMode resolveMachineMode(StateMachineDefinition definition,
                                                       DbPersistenceMode globalDefaultMode) {
        // 全局默认值兜底：外部未配置时回退 ASYNC。
        DbPersistenceMode safeGlobalDefaultMode = globalDefaultMode != null ? globalDefaultMode : DbPersistenceMode.ASYNC;
        // 无状态机定义时直接走全局默认。
        if (definition == null) {
            return safeGlobalDefaultMode;
        }
        // 优先状态机定义中的显式枚举；未配置则回落全局默认。
        return Optional.ofNullable(definition.getDbPersistenceMode()).orElse(safeGlobalDefaultMode);
    }

    /**
     * 按状态名解析状态级（state）持久化模式。
     * <p>
     * 逻辑：
     * 1) 在 definition.states 中定位目标状态；
     * 2) 命中后读取状态定义中的 statePersistenceMode；
     * 3) 未命中返回 empty。
     *
     * @param definition 状态机定义
     * @param stateName 目标状态名
     * @return 状态级模式，未配置或未命中返回 empty
     */
    public static Optional<DbPersistenceMode> resolveStateMode(StateMachineDefinition definition, String stateName) {
        // 任一关键入参缺失时无法解析，直接返回 empty。
        if (definition == null || definition.getStates() == null || stateName == null) {
            return Optional.empty();
        }
        // 通过状态名查找定义，命中后复用单状态解析逻辑。
        return definition.getStates().stream()
                .filter(state -> state != null && stateName.equals(state.getName()))
                .findFirst()
                .map(StateMachineDefinition.StateDefinition::getStatePersistenceMode);
    }
}

