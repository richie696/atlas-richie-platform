package cn.richie696.component.mcp.api;

/**
 * MCP 组件对业务暴露的异常基类。
 */
public class McpException extends RuntimeException {
    private final String errorCode;

    public McpException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public McpException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
