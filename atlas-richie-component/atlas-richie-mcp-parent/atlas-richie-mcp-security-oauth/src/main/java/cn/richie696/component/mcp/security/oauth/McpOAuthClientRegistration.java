package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.util.List;
import java.util.Set;

public record McpOAuthClientRegistration(
        String clientId,
        String clientSecret,
        String tokenEndpointAuthMethod,
        List<URI> redirectUris,
        Set<String> grantTypes,
        Set<String> scopes,
        URI registrationClientUri,
        String registrationAccessToken) {

    public McpOAuthClientRegistration(
            String clientId,
            String clientSecret,
            String tokenEndpointAuthMethod,
            List<URI> redirectUris,
            Set<String> grantTypes,
            Set<String> scopes) {
        this(clientId, clientSecret, tokenEndpointAuthMethod, redirectUris, grantTypes, scopes, null, null);
    }

    public McpOAuthClientRegistration {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        redirectUris = redirectUris == null ? List.of() : List.copyOf(redirectUris);
        grantTypes = grantTypes == null ? Set.of() : Set.copyOf(grantTypes);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
