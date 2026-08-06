package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpException;

import java.util.Objects;

/**
 * 业务可恢复错误；Dispatcher 将其转换为 isError=true 的 Tool Result。
 */
public final class McpToolExecutionException extends McpException {
    private final Object structuredContent;

    public McpToolExecutionException(String modelSafeMessage) {
        this(modelSafeMessage, null, null);
    }

    public McpToolExecutionException(
            String modelSafeMessage,
            Object structuredContent,
            Throwable cause) {
        super("MCP_TOOL_EXECUTION_ERROR", requireMessage(modelSafeMessage), cause);
        this.structuredContent = structuredContent;
    }

    public Object structuredContent() {
        return structuredContent;
    }

    private static String requireMessage(String message) {
        if (Objects.requireNonNull(message, "modelSafeMessage").isBlank()) {
            throw new IllegalArgumentException("modelSafeMessage must not be blank");
        }
        return message;
    }
}
