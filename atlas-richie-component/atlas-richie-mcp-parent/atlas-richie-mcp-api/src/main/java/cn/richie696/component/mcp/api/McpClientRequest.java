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
 */
public record McpClientRequest(String serverId, URI endpoint, Map<String, String> headers) {
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

    public McpClientRequest(URI endpoint, Map<String, String> headers) {
        this(null, endpoint, headers);
    }

    /** Cache key for protocol negotiation; credentials are intentionally excluded. */
    public String endpointCacheKey() {
        return endpoint.toString();
    }
}
