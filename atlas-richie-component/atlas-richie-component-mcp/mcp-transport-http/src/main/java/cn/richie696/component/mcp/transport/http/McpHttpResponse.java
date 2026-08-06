package cn.richie696.component.mcp.transport.http;

import java.util.Map;
import java.util.List;

/** Framework-neutral HTTP response produced by the MCP endpoint adapter. */
public record McpHttpResponse(int status, String contentType, Object body, List<Map<String, Object>> notifications) {
    public McpHttpResponse(int status, String contentType, Object body) {
        this(status, contentType, body, List.of());
    }

    public McpHttpResponse {
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
    }

    public static McpHttpResponse json(int status, Object body) {
        return new McpHttpResponse(status, "application/json", body);
    }

    public static McpHttpResponse accepted() {
        return new McpHttpResponse(202, null, null);
    }

    public static McpHttpResponse sse(
            int status,
            Object body,
            List<Map<String, Object>> notifications) {
        return new McpHttpResponse(status, "text/event-stream", body, notifications);
    }

    public boolean hasBody() {
        return body != null;
    }
}
