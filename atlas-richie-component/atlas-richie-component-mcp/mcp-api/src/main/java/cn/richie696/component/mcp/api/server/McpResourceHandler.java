package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpResourceContent;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface McpResourceHandler {
    CompletionStage<McpResourceContent> read(String uri, McpCallContext context);
}
