package cn.richie696.component.mcp.security.oauth;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OAuth metadata discovery 客户端。网络访问与内部 DTO 映射集中在此处。
 */
public final class McpOAuthMetadataClient {
    private final HttpClient httpClient;
    private final Duration timeout;
    private final McpOAuthUriPolicy uriPolicy;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public McpOAuthMetadataClient(HttpClient httpClient, Duration timeout) {
        this(httpClient, timeout, McpOAuthUriPolicy.httpsOnly());
    }

    public McpOAuthMetadataClient(
            HttpClient httpClient,
            Duration timeout,
            McpOAuthUriPolicy uriPolicy) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.uriPolicy = Objects.requireNonNull(uriPolicy, "uriPolicy");
    }

    public McpProtectedResourceMetadata fetchProtectedResourceMetadata(URI metadataUri) {
        Map<String, Object> raw = get(metadataUri);
        URI resource = uri(raw.get("resource"), "resource");
        List<URI> authorizationServers = uriList(raw.get("authorization_servers"), "authorization_servers");
        List<String> scopes = stringList(raw.get("scopes_supported"), "scopes_supported");
        return new McpProtectedResourceMetadata(resource, authorizationServers, scopes, raw);
    }

    public McpAuthorizationServerMetadata fetchAuthorizationServerMetadata(URI metadataUri) {
        Map<String, Object> raw = get(metadataUri);
        return new McpAuthorizationServerMetadata(
                uri(raw.get("issuer"), "issuer"),
                optionalUri(raw.get("authorization_endpoint"), "authorization_endpoint"),
                optionalUri(raw.get("token_endpoint"), "token_endpoint"),
                optionalUri(raw.get("registration_endpoint"), "registration_endpoint"),
                stringList(raw.get("response_types_supported"), "response_types_supported"),
                stringList(raw.get("grant_types_supported"), "grant_types_supported"),
                stringList(raw.get("code_challenge_methods_supported"), "code_challenge_methods_supported"));
    }

    private Map<String, Object> get(URI uri) {
        uriPolicy.validate(uri);
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OAuth metadata endpoint returned HTTP " + response.statusCode());
            }
            Map<?, ?> raw = jsonMapper.readValue(response.body(), Map.class);
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> {
                if (key instanceof String text) {
                    result.put(text, value);
                }
            });
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OAuth metadata request interrupted", exception);
        } catch (IOException | JacksonException | ClassCastException exception) {
            throw new IllegalStateException("OAuth metadata response is not valid JSON", exception);
        }
    }

    private URI uri(Object value, String field) {
        URI uri = optionalUri(value, field);
        if (uri == null) {
            throw new IllegalArgumentException("OAuth metadata field " + field + " must be present");
        }
        return uri;
    }

    private URI optionalUri(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("OAuth metadata field " + field + " must be a URI string");
        }
        try {
            URI uri = URI.create(text);
            uriPolicy.validate(uri);
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid OAuth metadata URI in " + field, exception);
        }
    }

    private List<URI> uriList(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("OAuth metadata field " + field + " must be an array");
        }
        List<URI> result = new ArrayList<>();
        for (Object entry : list) {
            result.add(uri(entry, field + "[]"));
        }
        return List.copyOf(result);
    }

    private List<String> stringList(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("OAuth metadata field " + field + " must be an array");
        }
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("OAuth metadata field " + field + " contains an invalid value");
            }
            result.add(text);
        }
        return List.copyOf(result);
    }
}
