package cn.richie696.component.mcp.api;

/**
 * 调用被对端或本地调用方取消。
 */
public final class McpCallCancelledException extends McpException {
    public McpCallCancelledException() {
        super("MCP_CALL_CANCELLED", "MCP call was cancelled");
    }
}
