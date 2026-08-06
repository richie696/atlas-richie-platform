package cn.richie696.component.mcp.protocol.model;

import java.util.Objects;

public record McpJsonRpcError(int code, String message, Object data) {
    public McpJsonRpcError {
        message = Objects.requireNonNull(message, "message");
    }
}
