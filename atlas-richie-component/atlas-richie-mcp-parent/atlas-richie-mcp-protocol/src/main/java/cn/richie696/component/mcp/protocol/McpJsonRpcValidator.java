package cn.richie696.component.mcp.protocol;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

/**
 * 在进入 Dialect 前执行与版本无关的 JSON-RPC 2.0 校验。
 */
public final class McpJsonRpcValidator {
    private McpJsonRpcValidator() {
    }

    public static void validate(McpJsonRpcRequest request) {
        if (request == null) {
            throw invalidRequest("Request must not be null");
        }
        if (!"2.0".equals(request.jsonrpc())) {
            throw invalidRequest("jsonrpc must be exactly 2.0");
        }
        if (request.method() == null || request.method().isBlank()) {
            throw invalidRequest("method must not be blank");
        }
        if (request.id() != null && !validId(request.id())) {
            throw invalidRequest("id must be a string or an integer number");
        }
    }

    private static boolean validId(Object id) {
        if (id instanceof String) {
            return true;
        }
        if (id instanceof Byte || id instanceof Short || id instanceof Integer || id instanceof Long
                || id instanceof BigInteger) {
            return true;
        }
        if (id instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().scale() <= 0;
        }
        return false;
    }

    private static McpProtocolException invalidRequest(String message) {
        return new McpProtocolException("MCP_INVALID_REQUEST", -32600, message, Map.of());
    }
}
