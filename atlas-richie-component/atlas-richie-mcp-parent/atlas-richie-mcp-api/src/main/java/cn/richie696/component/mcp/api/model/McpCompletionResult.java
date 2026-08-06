package cn.richie696.component.mcp.api.model;

import java.util.List;

public record McpCompletionResult(List<String> values, Integer total, boolean hasMore) {
    public McpCompletionResult {
        values = values == null ? List.of() : List.copyOf(values);
        if (values.size() > 100) throw new IllegalArgumentException("MCP completion values must not exceed 100");
        if (total != null && total < 0) throw new IllegalArgumentException("total must be non-negative");
    }
}
