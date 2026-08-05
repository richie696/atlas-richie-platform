package cn.richie696.component.mcp.api;

import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 业务侧唯一需要依赖的 MCP Client 操作面。
 */
public interface McpOperations {
    CompletionStage<List<McpToolDescriptor>> listTools(String serverId);

    CompletionStage<McpToolResponse> callTool(String serverId, String toolName, Map<String, Object> arguments);

    CompletionStage<List<McpResourceDescriptor>> listResources(String serverId);

    CompletionStage<McpResourceContent> readResource(String serverId, String uri);

    CompletionStage<List<McpPromptDescriptor>> listPrompts(String serverId);

    CompletionStage<McpPromptContent> getPrompt(String serverId, String name, Map<String, Object> arguments);
}
