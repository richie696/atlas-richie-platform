/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.support;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工具回调注册中心
 * <p>
 * 在 Spring 启动时按 {@link ToolDefinition#name()} 索引所有 {@link ToolCallback} Bean，
 * 供 {@code AiModelServiceImpl} 按请求中的 {@code toolNames} 解析为可调用列表。
 * <p>
 * 重名工具：先注册者获胜，重复条目静默丢弃（避免业务侧无意引入的 Bean 冲突放大成运行时错误）。
 */
@Component
public class ToolRegistry {

    private final Map<String, ToolCallback> byName;

    public ToolRegistry(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            this.byName = Map.of();
            return;
        }
        this.byName = toolCallbacks.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableMap(
                        tc -> tc.getToolDefinition().name(),
                        tc -> tc,
                        (existing, duplicate) -> existing
                ));
    }

    /**
     * 按名称解析工具回调
     *
     * @param names 请求中声明的工具名集合（可为 {@code null} 或空）
     * @return 命中的工具回调列表（保持入参顺序，缺失项静默跳过并记录 WARN）
     */
    public List<ToolCallback> resolve(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        return names.stream()
                .filter(Objects::nonNull)
                .filter(name -> {
                    if (byName.containsKey(name)) {
                        return true;
                    }
                    org.slf4j.LoggerFactory.getLogger(ToolRegistry.class)
                            .warn("未找到工具回调: {}", name);
                    return false;
                })
                .map(byName::get)
                .toList();
    }

    /**
     * 当前已注册的工具名集合（只读）
     */
    public java.util.Set<String> registeredNames() {
        return Collections.unmodifiableSet(byName.keySet());
    }
}
