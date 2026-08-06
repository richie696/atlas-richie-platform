package cn.richie696.component.mcp.api.server;

import java.util.Map;

public record McpCompletionRequest(
        Map<String, Object> reference,
        String argumentName,
        String value,
        Map<String, String> contextArguments) {
    public McpCompletionRequest {
        reference = reference == null ? Map.of() : Map.copyOf(reference);
        if (argumentName == null || argumentName.isBlank()) throw new IllegalArgumentException("argumentName must not be blank");
        value = value == null ? "" : value;
        contextArguments = contextArguments == null ? Map.of() : Map.copyOf(contextArguments);
    }
}
