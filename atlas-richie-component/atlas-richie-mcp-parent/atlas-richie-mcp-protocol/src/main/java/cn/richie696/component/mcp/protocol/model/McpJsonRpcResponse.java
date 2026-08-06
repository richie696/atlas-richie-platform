package cn.richie696.component.mcp.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpJsonRpcResponse(String jsonrpc, Object id, Map<String, Object> result, McpJsonRpcError error) {
    public McpJsonRpcResponse {
        boolean resultPresent = result != null;
        if (resultPresent == (error != null)) {
            throw new IllegalArgumentException("JSON-RPC response must contain exactly one of result or error");
        }
        result = result == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }
}
