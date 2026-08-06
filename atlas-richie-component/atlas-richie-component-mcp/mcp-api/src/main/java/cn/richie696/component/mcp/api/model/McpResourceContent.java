package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;

public record McpResourceContent(List<Map<String, Object>> contents) {
    public McpResourceContent {
        contents = contents == null ? List.of() : List.copyOf(contents);
    }
}
