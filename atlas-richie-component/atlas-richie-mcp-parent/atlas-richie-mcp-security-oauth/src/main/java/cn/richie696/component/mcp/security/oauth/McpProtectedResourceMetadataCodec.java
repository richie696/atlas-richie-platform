package cn.richie696.component.mcp.security.oauth;

import java.util.LinkedHashMap;
import java.util.Map;

/** Maps the internal PRM model to RFC 9728 JSON field names. */
public final class McpProtectedResourceMetadataCodec {
    private McpProtectedResourceMetadataCodec() {
    }

    public static Map<String, Object> encode(McpProtectedResourceMetadata metadata) {
        java.util.Objects.requireNonNull(metadata, "metadata");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resource", metadata.resource().toString());
        if (!metadata.authorizationServers().isEmpty()) {
            result.put("authorization_servers", metadata.authorizationServers().stream()
                    .map(java.net.URI::toString)
                    .toList());
        }
        if (!metadata.scopesSupported().isEmpty()) {
            result.put("scopes_supported", metadata.scopesSupported());
        }
        result.putAll(metadata.extensions());
        return Map.copyOf(result);
    }
}
