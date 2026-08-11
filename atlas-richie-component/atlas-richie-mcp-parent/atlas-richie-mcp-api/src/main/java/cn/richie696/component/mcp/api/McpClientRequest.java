package cn.richie696.component.mcp.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-request MCP target and authentication context.
 *
 * <p>The static {@link McpOperations} API remains available for applications
 * that configure servers in application properties. Discovery-driven clients
 * should create this value for every call so the endpoint and credentials can
 * change without rebuilding the Spring context.</p>
 *
 * @param serverId 业务可读的服务标识（用于日志/路由/缓存），缺省时回落为端点字符串
 * @param endpoint MCP 服务端点，必须为 {@code http} 或 {@code https}
 * @param headers 单次调用附带的 HTTP 头（如 Authorization、Trace-Id 等）
 * @author richie696
 * @since 2026-08-11
 */
public record McpClientRequest(String serverId, URI endpoint, Map<String, String> headers) {
    /**
     * 紧凑构造器：对端点协议、Header 名称做合法性校验，并对 Header 集合做不可变拷贝。
     *
     * @param serverId 服务标识
     * @param endpoint 端点 URI，必须为 http/https
     * @param headers HTTP 头，键不可为空，值允许为 {@code null}（将不写入）
     * @throws NullPointerException 当 {@code endpoint} 为 {@code null} 时
     * @throws IllegalArgumentException 当端点协议非 http/https，或 header 名称为空时
     */
    public McpClientRequest {
        endpoint = Objects.requireNonNull(endpoint, "endpoint");
        if (!"http".equalsIgnoreCase(endpoint.getScheme())
                && !"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new IllegalArgumentException("MCP endpoint must use http or https: " + endpoint);
        }
        serverId = serverId == null || serverId.isBlank() ? endpoint.toString() : serverId;
        Map<String, String> copied = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("MCP request header name must not be blank");
                }
                if (value != null) copied.put(name, value);
            });
        }
        headers = Map.copyOf(copied);
    }

    /**
     * 便捷构造器：省略 {@code serverId}，将由紧凑构造器回落为端点字符串。
     *
     * @param endpoint 端点 URI
     * @param headers HTTP 头
     */
    public McpClientRequest(URI endpoint, Map<String, String> headers) {
        this(null, endpoint, headers);
    }

    /**
     * Cache key for protocol negotiation; credentials are intentionally excluded.
     *
     * <p>仅基于端点生成缓存键，刻意排除 headers：协议协商结果与凭据无关，
     * 同一端点的不同凭据应当共享同一份协议能力描述，避免重复握手开销。</p>
     *
     * @return 用于协议能力缓存的端点字符串
     */
    public String endpointCacheKey() {
        return endpoint.toString();
    }
}
