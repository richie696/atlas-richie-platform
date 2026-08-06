package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record McpPromptDescriptor(
        String name,
        String title,
        String description,
        List<Map<String, Object>> arguments) {

    public McpPromptDescriptor {
        name = Objects.requireNonNull(name, "name");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
