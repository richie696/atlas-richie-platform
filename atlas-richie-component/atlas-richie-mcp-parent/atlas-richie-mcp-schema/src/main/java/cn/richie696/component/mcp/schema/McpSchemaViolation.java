package cn.richie696.component.mcp.schema;

public record McpSchemaViolation(
        String instanceLocation,
        String schemaLocation,
        String keyword,
        String message) {
}
