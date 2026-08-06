package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 业务 Tool 的稳定执行端口。
 */
@FunctionalInterface
public interface McpToolHandler {
    CompletionStage<McpToolResponse> handle(Map<String, Object> arguments, McpCallContext context);
}
