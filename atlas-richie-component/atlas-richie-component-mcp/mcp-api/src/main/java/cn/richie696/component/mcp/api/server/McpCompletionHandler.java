package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpCompletionResult;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface McpCompletionHandler {
    CompletionStage<McpCompletionResult> complete(McpCompletionRequest request, McpCallContext context);
}
