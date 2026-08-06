package cn.richie696.component.mcp.protocol.compatibility;

import java.util.List;

/**
 * Transport 将探测结果归一化为该事件，不把 HTTP/进程实现泄漏给协商核心。
 */
public record McpProbeEvent(
        Type type,
        Integer httpStatus,
        Integer jsonRpcErrorCode,
        List<String> advertisedVersions) {

    public McpProbeEvent {
        advertisedVersions = advertisedVersions == null ? List.of() : List.copyOf(advertisedVersions);
    }

    public static McpProbeEvent discoverResult(List<String> supportedVersions) {
        return new McpProbeEvent(Type.DISCOVER_RESULT, null, null, supportedVersions);
    }

    public static McpProbeEvent modernSuccess(int httpStatus) {
        return new McpProbeEvent(Type.MODERN_SUCCESS, httpStatus, null, List.of());
    }

    public static McpProbeEvent jsonRpcError(
            Integer httpStatus,
            int jsonRpcErrorCode,
            List<String> advertisedVersions) {
        return new McpProbeEvent(
                Type.JSON_RPC_ERROR,
                httpStatus,
                jsonRpcErrorCode,
                advertisedVersions);
    }

    public static McpProbeEvent transportError(Integer httpStatus) {
        return new McpProbeEvent(Type.TRANSPORT_ERROR, httpStatus, null, List.of());
    }

    public static McpProbeEvent timeout() {
        return new McpProbeEvent(Type.TIMEOUT, null, null, List.of());
    }

    public enum Type {
        DISCOVER_RESULT,
        MODERN_SUCCESS,
        JSON_RPC_ERROR,
        TRANSPORT_ERROR,
        TIMEOUT
    }
}
