package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpPromptContent;

import java.util.Map;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface McpPromptHandler {
    CompletionStage<McpPromptContent> get(Map<String, Object> arguments, McpCallContext context);
}
