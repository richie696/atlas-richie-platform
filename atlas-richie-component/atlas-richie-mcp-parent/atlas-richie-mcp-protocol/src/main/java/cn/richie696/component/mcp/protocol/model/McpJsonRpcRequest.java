package cn.richie696.component.mcp.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON 解码后的最小线格式；传输模块负责将 JSON 映射到该类型。
 */
public record McpJsonRpcRequest(String jsonrpc, Object id, String method, Map<String, Object> params) {
    public McpJsonRpcRequest {
        params = params == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }

    public boolean notification() {
        return id == null;
    }
}
