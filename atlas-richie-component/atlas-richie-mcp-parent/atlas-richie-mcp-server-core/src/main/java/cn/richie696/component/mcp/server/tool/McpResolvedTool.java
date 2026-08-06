package cn.richie696.component.mcp.server.tool;

import cn.richie696.component.mcp.schema.McpCompiledSchema;

import java.util.Objects;
import java.util.Optional;

public record McpResolvedTool(
        McpToolRegistration registration,
        McpCompiledSchema inputSchema,
        McpCompiledSchema outputSchema) {

    public McpResolvedTool {
        registration = Objects.requireNonNull(registration, "registration");
        inputSchema = Objects.requireNonNull(inputSchema, "inputSchema");
    }

    public Optional<McpCompiledSchema> optionalOutputSchema() {
        return Optional.ofNullable(outputSchema);
    }
}
