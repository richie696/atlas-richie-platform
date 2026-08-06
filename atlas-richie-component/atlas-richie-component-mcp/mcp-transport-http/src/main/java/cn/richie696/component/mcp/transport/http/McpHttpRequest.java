package cn.richie696.component.mcp.transport.http;

import cn.richie696.component.mcp.protocol.model.McpJsonRpcRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 Servlet/Reactive HTTP 框架无关的请求载体。
 */
public record McpHttpRequest(
        String httpMethod,
        Map<String, List<String>> headers,
        McpJsonRpcRequest message) {
    public McpHttpRequest {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, values) -> copy.put(
                    name,
                    values == null
                            ? List.of()
                            : Collections.unmodifiableList(new ArrayList<>(values))));
        }
        headers = Collections.unmodifiableMap(copy);
    }
}
