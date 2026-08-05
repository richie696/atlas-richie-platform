package cn.richie696.component.mcp.transport.http;

import java.util.Map;

/** Framework-neutral HTTP response produced by the MCP endpoint adapter. */
public record McpHttpResponse(int status, String contentType, Object body) {
    public static McpHttpResponse json(int status, Object body) {
        return new McpHttpResponse(status, "application/json", body);
    }

    public static McpHttpResponse accepted() {
        return new McpHttpResponse(202, null, null);
    }

    public boolean hasBody() {
        return body != null;
    }
}
