package cn.richie696.component.mcp.security.oauth;

import java.time.Instant;
import java.util.Set;

public record McpOAuthIntrospectionResponse(
        boolean active,
        String clientId,
        String subject,
        String tokenType,
        Instant expiresAt,
        Set<String> scopes,
        String issuer,
        String resource) {

    public McpOAuthIntrospectionResponse {
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
