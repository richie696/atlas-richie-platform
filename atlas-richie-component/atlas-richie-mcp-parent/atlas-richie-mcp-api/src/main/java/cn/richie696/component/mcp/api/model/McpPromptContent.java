package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;

public record McpPromptContent(String description, List<Map<String, Object>> messages) {
    public McpPromptContent {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
