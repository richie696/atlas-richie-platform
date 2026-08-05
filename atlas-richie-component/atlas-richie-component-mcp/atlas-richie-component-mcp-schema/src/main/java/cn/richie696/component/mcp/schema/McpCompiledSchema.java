package cn.richie696.component.mcp.schema;

@FunctionalInterface
public interface McpCompiledSchema {
    McpSchemaValidationResult validate(Object instance);
}
