package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/** Refreshing OAuth token provider suitable for wiring into the MCP Client Starter. */
public final class McpOAuthTokenManager implements McpOAuthTokenProvider {
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final McpOAuthTokenClient client;
    private final URI tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final URI resource;
    private final Set<String> configuredScopes;
    private final AtomicReference<McpOAuthAccessToken> accessToken = new AtomicReference<>();
    private final AtomicReference<String> refreshToken = new AtomicReference<>();

    public McpOAuthTokenManager(
            McpOAuthTokenClient client,
            URI tokenEndpoint,
            String clientId,
            String clientSecret,
            URI resource,
            Set<String> configuredScopes) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.tokenEndpoint = java.util.Objects.requireNonNull(tokenEndpoint, "tokenEndpoint");
        this.clientId = java.util.Objects.requireNonNull(clientId, "clientId");
        this.clientSecret = clientSecret;
        this.resource = java.util.Objects.requireNonNull(resource, "resource");
        this.configuredScopes = configuredScopes == null ? Set.of() : Set.copyOf(configuredScopes);
    }

    public void accept(McpOAuthTokenResponse response) {
        java.util.Objects.requireNonNull(response, "response");
        accessToken.set(response.withRefreshTokenMetadata());
        if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
            refreshToken.set(response.refreshToken());
        }
    }

    @Override
    public CompletionStage<Optional<McpOAuthAccessToken>> tokenFor(
            URI requestedResource,
            Set<String> requiredScopes) {
        if (requestedResource != null && !resource.equals(requestedResource)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Set<String> required = requiredScopes == null ? Set.of() : Set.copyOf(requiredScopes);
        McpOAuthAccessToken current = accessToken.get();
        if (usable(current, required)) {
            return CompletableFuture.completedFuture(Optional.of(current));
        }
        synchronized (this) {
            current = accessToken.get();
            if (usable(current, required)) {
                return CompletableFuture.completedFuture(Optional.of(current));
            }
            McpOAuthTokenResponse refreshed = refreshToken.get() == null
                    ? client.clientCredentials(tokenEndpoint, clientId, clientSecret, resource, requestedScopes(required))
                    : client.refreshToken(tokenEndpoint, clientId, clientSecret, refreshToken.get(), resource, requestedScopes(required));
            accept(refreshed);
            return CompletableFuture.completedFuture(Optional.of(accessToken.get()));
        }
    }

    private boolean usable(McpOAuthAccessToken token, Set<String> requiredScopes) {
        return token != null && !token.expired(CLOCK_SKEW) && token.scopes().containsAll(requiredScopes);
    }

    private Set<String> requestedScopes(Set<String> required) {
        LinkedHashSet<String> scopes = new LinkedHashSet<>(configuredScopes);
        scopes.addAll(required);
        return Set.copyOf(scopes);
    }
}
