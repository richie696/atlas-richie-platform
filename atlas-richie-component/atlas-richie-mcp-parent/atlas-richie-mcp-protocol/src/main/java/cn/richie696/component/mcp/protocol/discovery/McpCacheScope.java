package cn.richie696.component.mcp.protocol.discovery;

public enum McpCacheScope {
    PUBLIC("public"),
    PRIVATE("private");

    private final String wireValue;

    McpCacheScope(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static McpCacheScope fromWireValue(String value) {
        return switch (value) {
            case "public" -> PUBLIC;
            case "private" -> PRIVATE;
            default -> throw new IllegalArgumentException("Unsupported MCP cache scope: " + value);
        };
    }
}
