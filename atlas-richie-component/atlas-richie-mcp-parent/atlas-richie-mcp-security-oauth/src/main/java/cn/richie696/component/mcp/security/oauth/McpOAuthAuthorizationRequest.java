package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Immutable authorization-code request builder with mandatory S256 PKCE. */
public record McpOAuthAuthorizationRequest(
        URI authorizationEndpoint,
        String clientId,
        URI redirectUri,
        String scope,
        String resource,
        String state,
        String codeChallenge) {

    public McpOAuthAuthorizationRequest {
        Objects.requireNonNull(authorizationEndpoint, "authorizationEndpoint");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(redirectUri, "redirectUri");
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state must not be blank");
        }
        if (codeChallenge == null || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("codeChallenge must not be blank");
        }
    }

    public URI toUri() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri.toString());
        if (scope != null && !scope.isBlank()) params.put("scope", scope);
        if (resource != null && !resource.isBlank()) params.put("resource", resource);
        params.put("state", state);
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", "S256");
        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        try {
            return new URI(authorizationEndpoint.getScheme(), authorizationEndpoint.getRawAuthority(),
                    authorizationEndpoint.getPath(), query, authorizationEndpoint.getFragment());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid authorization endpoint", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
