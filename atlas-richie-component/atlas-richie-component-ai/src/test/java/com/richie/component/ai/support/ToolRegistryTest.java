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
