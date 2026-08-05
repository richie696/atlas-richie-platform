package cn.richie696.component.mcp.schema;

public final class McpJsonSchemaValidators {
    private McpJsonSchemaValidators() {
    }

    public static McpJsonSchemaValidator secureDefaults() {
        return new NetworkntMcpJsonSchemaValidator(64, 10_000);
    }
}
