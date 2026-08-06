package cn.richie696.component.mcp.security.oauth;

import java.time.Instant;
import java.util.Set;

public record McpOAuthTokenResponse(
        McpOAuthAccessToken accessToken,
        String refreshToken,
        Set<String> scopes) {

    public McpOAuthTokenResponse {
        java.util.Objects.requireNonNull(accessToken, "accessToken");
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public McpOAuthAccessToken withRefreshTokenMetadata() {
        return new McpOAuthAccessToken(
                accessToken.value(), accessToken.tokenType(), accessToken.expiresAt(),
                accessToken.issuer(), accessToken.resource(), scopes);
    }
}
