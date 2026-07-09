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
package com.richie.component.statemachine.registry;

import com.richie.component.statemachine.model.StateMachineModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 状态机注册表
 * <p>
 * 管理所有已加载的状态机模型，提供注册、查询、移除等功能。
 * 使用线程安全的 ConcurrentHashMap 存储状态机模型。
 * 
 * <p>
 * 注意：此类使用 Spring {@code @Component} 注解，默认构造函数由 Spring 自动生成。
 * 
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
public class StateMachineRegistry {

    /**
     * 状态机模型存储映射表，key 为状态机名称，value 为状态机模型
     */
    private final Map<String, StateMachineModel> stateMachines = new ConcurrentHashMap<>();

    /**
     * 注册状态机
     * <p>
     * 将状态机模型注册到注册表中，如果状态机名称为空或状态机对象为 null，将抛出异常。
     * 
     *
     * @param stateMachine 状态机模型对象
     * @throws IllegalArgumentException 如果状态机为 null 或名称为空
     */
    public void register(StateMachineModel stateMachine) {
        if (stateMachine == null || stateMachine.getName() == null) {
            throw new IllegalArgumentException("状态机名称不能为空");
        }
        
        stateMachines.put(stateMachine.getName(), stateMachine);
        log.info("状态机注册成功: {}", stateMachine.getName());
    }

    /**
     * 获取状态机
     * <p>
     * 根据状态机名称获取对应的状态机模型。
     * 
     *
     * @param name 状态机名称
     * @return 状态机模型，如果不存在则返回 null
     */
    public StateMachineModel getStateMachine(String name) {
        return stateMachines.get(name);
    }

    /**
     * 移除状态机
     * <p>
     * 从注册表中移除指定名称的状态机模型。
     * 
     *
     * @param name 状态机名称
     * @return 被移除的状态机模型，如果不存在则返回 null
     */
    public StateMachineModel remove(String name) {
        StateMachineModel removed = stateMachines.remove(name);
        if (removed != null) {
            log.info("状态机移除成功: {}", name);
        }
        return removed;
    }

    /**
     * 检查状态机是否存在
     * <p>
     * 检查指定名称的状态机是否已注册。
     * 
     *
     * @param name 状态机名称
     * @return true 表示存在，false 表示不存在
     */
    public boolean contains(String name) {
        return stateMachines.containsKey(name);
    }

    /**
     * 获取所有状态机名称
     * <p>
     * 返回所有已注册的状态机名称集合。
     * 
     *
     * @return 状态机名称集合（不可修改的视图）
     */
    public java.util.Set<String> getStateMachineNames() {
        return stateMachines.keySet();
    }

    /**
     * 获取所有状态机
     * <p>
     * 返回所有已注册的状态机模型的副本，修改返回的 Map 不会影响注册表。
     * 
     *
     * @return 状态机模型映射表的副本
     */
    public Map<String, StateMachineModel> getAllStateMachines() {
        return new ConcurrentHashMap<>(stateMachines);
    }

    /**
     * 清空所有状态机
     * <p>
     * 移除注册表中的所有状态机模型。
     * 
     */
    public void clear() {
        stateMachines.clear();
        log.info("所有状态机已清空");
    }
} 
