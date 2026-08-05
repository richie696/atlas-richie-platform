package cn.richie696.component.mcp.transport.http;

/**
 * Origin 验证策略。Origin 缺失表示非浏览器客户端，由调用方决定是否另行鉴权。
 */
@FunctionalInterface
public interface McpOriginPolicy {
    boolean isAllowed(String origin);

    static McpOriginPolicy denyAllPresentOrigins() {
        return origin -> false;
    }
}
