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

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    @Test
    void resolve_shouldReturnMatchingToolsInOrder() {
        ToolCallback first = stubTool("search");
        ToolCallback second = stubTool("calc");
        ToolRegistry registry = new ToolRegistry(List.of(first, second));

        List<ToolCallback> resolved = registry.resolve(List.of("calc", "search", "missing"));

        assertThat(resolved).containsExactly(second, first);
        assertThat(registry.registeredNames()).containsExactlyInAnyOrder("search", "calc");
    }

    @Test
    void resolve_emptyNames_shouldReturnEmptyList() {
        ToolRegistry registry = new ToolRegistry(List.of(stubTool("search")));
        assertThat(registry.resolve(List.of())).isEmpty();
        assertThat(registry.resolve(null)).isEmpty();
    }

    private static ToolCallback stubTool(String name) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
            }

            @Override
            public String call(String toolInput) {
                return toolInput;
            }
        };
    }
}
