package cn.richie696.component.mcp.protocol;

/**
 * MCP 2026-07-28 保留的协议级 _meta 键。
 */
public final class McpMetaKeys {
    public static final String PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    public static final String CLIENT_INFO = "io.modelcontextprotocol/clientInfo";
    public static final String CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    public static final String SERVER_INFO = "io.modelcontextprotocol/serverInfo";
    public static final String LOG_LEVEL = "io.modelcontextprotocol/logLevel";
    public static final String SUBSCRIPTION_ID = "io.modelcontextprotocol/subscriptionId";
    public static final String PROGRESS_TOKEN = "progressToken";

    private McpMetaKeys() {
    }
}
