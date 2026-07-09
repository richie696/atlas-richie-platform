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
package com.richie.component.statemachine.config;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 状态机定义注册表（缓存）
 * <p>
 * 用于缓存从配置文件加载的状态机定义，供引擎查询使用。
 * 使用线程安全的 ConcurrentHashMap 存储状态机定义。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
@Component
public class StateMachineDefinitionRegistry {

    /**
     * 状态机定义缓存
     * <p>
     * Key: 状态机名称
     * Value: 状态机定义对象
     * 
     */
    private final Map<String, StateMachineDefinition> definitionCache = new ConcurrentHashMap<>();

    /**
     * 注册状态机定义
     * <p>
     * 将状态机定义注册到缓存中。如果定义为 null 或名称为 null，则不会注册。
     * 
     *
     * @param definition 状态机定义对象
     */
    public void register(StateMachineDefinition definition) {
        if (definition != null && definition.getName() != null) {
            definitionCache.put(definition.getName(), definition);
        }
    }

    /**
     * 获取状态机定义
     * <p>
     * 根据状态机名称从缓存中获取对应的状态机定义。
     * 
     *
     * @param stateMachineName 状态机名称
     * @return 状态机定义对象，如果不存在则返回 null
     */
    public StateMachineDefinition getDefinition(String stateMachineName) {
        return definitionCache.get(stateMachineName);
    }

    /**
     * 移除状态机定义
     * <p>
     * 从缓存中移除指定名称的状态机定义。
     * 
     *
     * @param stateMachineName 状态机名称
     */
    public void remove(String stateMachineName) {
        definitionCache.remove(stateMachineName);
    }

    /**
     * 清空所有定义
     * <p>
     * 移除缓存中的所有状态机定义。
     * 
     */
    public void clear() {
        definitionCache.clear();
    }

    /**
     * 获取已注册的状态机数量
     * <p>
     * 返回当前缓存中已注册的状态机定义数量。
     * 
     *
     * @return 状态机数量
     */
    public int size() {
        return definitionCache.size();
    }
}

