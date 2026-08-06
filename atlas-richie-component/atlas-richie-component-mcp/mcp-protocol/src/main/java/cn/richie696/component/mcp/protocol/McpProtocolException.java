package cn.richie696.component.mcp.protocol;

import cn.richie696.component.mcp.api.McpException;

import java.util.Map;

/**
 * 可无损映射为 JSON-RPC error 的协议异常。
 */
public final class McpProtocolException extends McpException {
    private final int jsonRpcCode;
    private final Map<String, Object> data;

    public McpProtocolException(String errorCode, int jsonRpcCode, String message, Map<String, Object> data) {
        super(errorCode, message);
        this.jsonRpcCode = jsonRpcCode;
        this.data = data == null ? Map.of() : Map.copyOf(data);
    }

    public McpProtocolException(
            String errorCode,
            int jsonRpcCode,
            String message,
            Map<String, Object> data,
            Throwable cause) {
        super(errorCode, message, cause);
        this.jsonRpcCode = jsonRpcCode;
        this.data = data == null ? Map.of() : Map.copyOf(data);
    }

    public int jsonRpcCode() {
        return jsonRpcCode;
    }

    public Map<String, Object> data() {
        return data;
    }
}
