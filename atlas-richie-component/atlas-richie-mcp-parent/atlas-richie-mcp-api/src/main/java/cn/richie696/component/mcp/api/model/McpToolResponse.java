package cn.richie696.component.mcp.api.model;

import java.util.List;
import java.util.Map;

/**
 * Tool 的稳定调用结果。
 */
public record McpToolResponse(
        List<Map<String, Object>> content,
        Object structuredContent,
        boolean error,
        String resultType,
        Map<String, Object> inputRequests,
        String requestState) {

    public McpToolResponse(
            List<Map<String, Object>> content,
            Object structuredContent,
            boolean error) {
        this(content, structuredContent, error, "complete", Map.of(), null);
    }

    public static McpToolResponse inputRequired(
            Map<String, Object> inputRequests,
            String requestState) {
        if ((inputRequests == null || inputRequests.isEmpty())
                && (requestState == null || requestState.isBlank())) {
            throw new IllegalArgumentException("input_required requires inputRequests or requestState");
        }
        return new McpToolResponse(List.of(), null, false, "input_required",
                inputRequests == null ? Map.of() : inputRequests, requestState);
    }

    public McpToolResponse {
        content = content == null ? List.of() : List.copyOf(content);
        resultType = resultType == null || resultType.isBlank() ? "complete" : resultType;
        if (!resultType.equals("complete") && !resultType.equals("input_required")) {
            throw new IllegalArgumentException("Unsupported MCP resultType: " + resultType);
        }
        inputRequests = inputRequests == null ? Map.of() : Map.copyOf(inputRequests);
        if (resultType.equals("input_required") && inputRequests.isEmpty()
                && (requestState == null || requestState.isBlank())) {
            throw new IllegalArgumentException("input_required requires inputRequests or requestState");
        }
    }
}
