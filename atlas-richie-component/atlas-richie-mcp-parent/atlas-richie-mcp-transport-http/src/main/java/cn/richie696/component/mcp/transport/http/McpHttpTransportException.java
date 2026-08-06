package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.Optional;

/**
 * HTTP 状态与可选 JSON-RPC 错误的组合；Web Adapter 只负责序列化。
 */
public final class McpHttpTransportException extends RuntimeException {
    private final int httpStatus;
    private final McpProtocolException protocolError;

    public McpHttpTransportException(
            int httpStatus,
            String message,
            McpProtocolException protocolError) {
        super(message, protocolError);
        this.httpStatus = httpStatus;
        this.protocolError = protocolError;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public Optional<McpProtocolException> protocolError() {
        return Optional.ofNullable(protocolError);
    }
}
