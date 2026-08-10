package cn.richie696.component.mcp.api;

import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * MCP operations for Discovery-driven clients.
 *
 * <p>Unlike {@link McpOperations}, each operation receives its endpoint and
 * request headers. This makes server selection and user/service credentials a
 * request concern while keeping protocol negotiation and transport in the
 * platform component.</p>
 */
public interface McpDynamicOperations {
    CompletionStage<List<McpToolDescriptor>> listTools(McpClientRequest request);

    CompletionStage<McpToolResponse> callTool(
            McpClientRequest request,
            String toolName,
            Map<String, Object> arguments);

    CompletionStage<List<McpResourceDescriptor>> listResources(McpClientRequest request);

    default CompletionStage<List<McpResourceTemplateDescriptor>> listResourceTemplates(
            McpClientRequest request) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Resource templates are not supported"));
    }

    default CompletionStage<McpCompletionResult> complete(
            McpClientRequest request,
            Map<String, Object> reference,
            String argumentName,
            String value,
            Map<String, String> contextArguments) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Completions are not supported"));
    }

    CompletionStage<McpResourceContent> readResource(McpClientRequest request, String uri);

    CompletionStage<List<McpPromptDescriptor>> listPrompts(McpClientRequest request);

    CompletionStage<McpPromptContent> getPrompt(
            McpClientRequest request,
            String name,
            Map<String, Object> arguments);
}
