package cn.richie696.component.mcp.security.oauth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * 不暴露给业务的 OAuth access token 快照。
 */
public record McpOAuthAccessToken(
        String value,
        String tokenType,
        Instant expiresAt,
        String issuer,
        String resource,
        Set<String> scopes) {

    public McpOAuthAccessToken {
        value = required(value, "value");
        tokenType = tokenType == null || tokenType.isBlank() ? "Bearer" : tokenType;
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public boolean expired(Duration clockSkew) {
        if (expiresAt == null) {
            return false;
        }
        Duration skew = clockSkew == null ? Duration.ZERO : clockSkew;
        return !expiresAt.isAfter(Instant.now().plus(skew));
    }

    public String authorizationHeader() {
        return tokenType + " " + value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
