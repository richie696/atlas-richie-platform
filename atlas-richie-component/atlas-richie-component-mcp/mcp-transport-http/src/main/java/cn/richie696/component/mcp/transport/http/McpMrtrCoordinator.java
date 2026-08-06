package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.api.McpInputProvider;
import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.net.URI;
import java.util.Map;

/** Client-side bounded coordinator for tools/call input_required round trips. */
public final class McpMrtrCoordinator {
    private final McpHttpToolClient client;
    private final int maxRounds;

    public McpMrtrCoordinator(McpHttpToolClient client) {
        this(client, 3);
    }

    public McpMrtrCoordinator(McpHttpToolClient client, int maxRounds) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        if (maxRounds < 1 || maxRounds > 10) throw new IllegalArgumentException("maxRounds must be 1..10");
        this.maxRounds = maxRounds;
    }

    public McpToolResponse callTool(
            URI endpoint,
            String toolName,
            Map<String, Object> arguments,
            Map<String, String> headers,
            McpInputProvider inputProvider) {
        Map<String, Object> inputResponses = Map.of();
        String requestState = null;
        for (int round = 0; round < maxRounds; round++) {
            McpToolResponse response = client.callTool(
                    endpoint, toolName, arguments, headers, inputResponses, requestState);
            if (!"input_required".equals(response.resultType())) return response;
            if (inputProvider == null) {
                throw new IllegalStateException("MCP server requested input but no input provider is configured");
            }
            inputResponses = inputProvider.collect(response.inputRequests())
                    .toCompletableFuture().join();
            requestState = response.requestState();
        }
        throw new IllegalStateException("MCP MRTR exceeded configured maximum rounds");
    }
}
