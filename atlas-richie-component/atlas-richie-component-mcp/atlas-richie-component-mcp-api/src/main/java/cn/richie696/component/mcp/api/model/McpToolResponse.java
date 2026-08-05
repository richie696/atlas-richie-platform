package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;

/**
 * Tool 的稳定调用结果。
 */
public record McpToolResponse(
        List<Map<String, Object>> content,
        Object structuredContent,
        boolean error) {

    public McpToolResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
