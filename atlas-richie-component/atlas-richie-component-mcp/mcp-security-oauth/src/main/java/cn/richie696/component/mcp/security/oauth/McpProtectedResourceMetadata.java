package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * RFC 9728 Protected Resource Metadata 的内部归一化模型。
 */
public record McpProtectedResourceMetadata(
        URI resource,
        List<URI> authorizationServers,
        List<String> scopesSupported,
        Map<String, Object> extensions) {

    public McpProtectedResourceMetadata {
        resource = java.util.Objects.requireNonNull(resource, "resource");
        authorizationServers = authorizationServers == null ? List.of() : List.copyOf(authorizationServers);
        scopesSupported = scopesSupported == null ? List.of() : List.copyOf(scopesSupported);
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
    }
}
