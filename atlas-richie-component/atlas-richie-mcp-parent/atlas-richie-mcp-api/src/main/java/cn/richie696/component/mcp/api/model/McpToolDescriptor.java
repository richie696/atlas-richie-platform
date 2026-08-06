package cn.richie696.component.mcp.api.model;

import java.util.Map;
import java.util.Objects;

/**
 * 与具体协议版本无关的 Tool 描述。
 */
public record McpToolDescriptor(
        String name,
        String title,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> annotations) {

    public McpToolDescriptor {
        name = Objects.requireNonNull(name, "name");
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        annotations = annotations == null ? Map.of() : Map.copyOf(annotations);
    }
}
