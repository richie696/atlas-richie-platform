package cn.richie696.component.mcp.security.oauth;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/** Framework-neutral OAuth token, introspection and dynamic registration client. */
public final class McpOAuthTokenClient {
    private final HttpClient httpClient;
    private final Duration timeout;
    private final McpOAuthUriPolicy uriPolicy;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public McpOAuthTokenClient(HttpClient httpClient, Duration timeout) {
        this(httpClient, timeout, McpOAuthUriPolicy.httpsOnly());
    }

    public McpOAuthTokenClient(
            HttpClient httpClient,
            Duration timeout,
            McpOAuthUriPolicy uriPolicy) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = java.util.Objects.requireNonNull(timeout, "timeout");
        this.uriPolicy = java.util.Objects.requireNonNull(uriPolicy, "uriPolicy");
    }

    public McpOAuthTokenResponse authorizationCode(
            URI tokenEndpoint,
            String clientId,
            String clientSecret,
            String code,
            URI redirectUri,
            String codeVerifier,
            URI resource,
            Set<String> scopes) {
        Map<String, String> form = baseGrant("authorization_code", clientId, resource, scopes);
        form.put("code", required(code, "code"));
        form.put("redirect_uri", java.util.Objects.requireNonNull(redirectUri, "redirectUri").toString());
        form.put("code_verifier", required(codeVerifier, "codeVerifier"));
        return token(tokenEndpoint, clientId, clientSecret, form, resource);
    }

    public McpOAuthTokenResponse refreshToken(
            URI tokenEndpoint,
            String clientId,
            String clientSecret,
            String refreshToken,
            URI resource,
            Set<String> scopes) {
        Map<String, String> form = baseGrant("refresh_token", clientId, resource, scopes);
        form.put("refresh_token", required(refreshToken, "refreshToken"));
        return token(tokenEndpoint, clientId, clientSecret, form, resource);
    }

    public McpOAuthTokenResponse clientCredentials(
            URI tokenEndpoint,
            String clientId,
            String clientSecret,
            URI resource,
            Set<String> scopes) {
        Map<String, String> form = baseGrant("client_credentials", clientId, resource, scopes);
        return token(tokenEndpoint, clientId, clientSecret, form, resource);
    }

    public McpOAuthIntrospectionResponse introspect(
            URI introspectionEndpoint,
            String clientId,
            String clientSecret,
            String token) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("token", required(token, "token"));
        Map<String, Object> raw = postForm(introspectionEndpoint, clientId, clientSecret, form);
        boolean active = Boolean.TRUE.equals(raw.get("active"));
        Instant expiresAt = instant(raw.get("exp"));
        return new McpOAuthIntrospectionResponse(
                active,
                string(raw.get("client_id")),
                string(raw.get("sub")),
                string(raw.get("token_type")),
                expiresAt,
                scopes(raw.get("scope")),
                string(raw.get("iss")),
                string(raw.get("aud")));
    }

    public McpOAuthClientRegistration register(
            URI registrationEndpoint,
            Map<String, Object> registrationRequest) {
        uriPolicy.validate(registrationEndpoint);
        try {
            HttpRequest request = HttpRequest.newBuilder(registrationEndpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(
                            registrationRequest == null ? Map.of() : registrationRequest)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> raw = json(response);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw oauthFailure(response.statusCode(), raw);
            }
            return new McpOAuthClientRegistration(
                    required(string(raw.get("client_id")), "client_id"),
                    string(raw.get("client_secret")),
                    string(raw.get("token_endpoint_auth_method")),
                    uriList(raw.get("redirect_uris")),
                    stringSet(raw.get("grant_types")),
                    stringSet(raw.get("scope")),
                    uri(raw.get("registration_client_uri")),
                    string(raw.get("registration_access_token")));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OAuth registration request interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("OAuth registration request failed", exception);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OAuth registration response is not valid JSON", exception);
        }
    }

    private McpOAuthTokenResponse token(
            URI endpoint,
            String clientId,
            String clientSecret,
            Map<String, String> form,
            URI resource) {
        Map<String, Object> raw = postForm(endpoint, clientId, clientSecret, form);
        String value = required(string(raw.get("access_token")), "access_token");
        long expiresIn = raw.get("expires_in") instanceof Number number ? number.longValue() : 3600;
        Set<String> scopes = scopes(raw.get("scope"));
        return new McpOAuthTokenResponse(
                new McpOAuthAccessToken(value, stringOrDefault(raw.get("token_type"), "Bearer"),
                        Instant.now().plusSeconds(Math.max(0, expiresIn)), null,
                        resource == null ? null : resource.toString(), scopes),
                string(raw.get("refresh_token")),
                scopes);
    }

    private Map<String, Object> postForm(
            URI endpoint,
            String clientId,
            String clientSecret,
            Map<String, String> form) {
        uriPolicy.validate(endpoint);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json");
            if (clientId != null && !clientId.isBlank()) {
                String credentials = clientId + ":" + (clientSecret == null ? "" : clientSecret);
                builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        credentials.getBytes(StandardCharsets.UTF_8)));
            }
            HttpResponse<String> response = httpClient.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(form(form))).build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> raw = json(response);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw oauthFailure(response.statusCode(), raw);
            }
            return raw;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OAuth endpoint request interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("OAuth endpoint request failed", exception);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OAuth endpoint response is not valid JSON", exception);
        }
    }

    private Map<String, String> baseGrant(
            String grantType,
            String clientId,
            URI resource,
            Set<String> scopes) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", grantType);
        form.put("client_id", required(clientId, "clientId"));
        if (resource != null) form.put("resource", resource.toString());
        if (scopes != null && !scopes.isEmpty()) form.put("scope", String.join(" ", scopes));
        return form;
    }

    private String form(Map<String, String> values) {
        StringJoiner joiner = new StringJoiner("&");
        values.forEach((key, value) -> joiner.add(encode(key) + "=" + encode(value)));
        return joiner.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Map<String, Object> json(HttpResponse<String> response) throws JacksonException {
        Map<?, ?> raw = jsonMapper.readValue(response.body(), Map.class);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key instanceof String text) result.put(text, value);
        });
        return result;
    }

    private IllegalStateException oauthFailure(int status, Map<String, Object> raw) {
        String error = stringOrDefault(raw.get("error"), "oauth_endpoint_error");
        String description = stringOrDefault(raw.get("error_description"), "OAuth endpoint rejected the request");
        return new IllegalStateException("OAuth endpoint returned " + status + ": " + error + " - " + description);
    }

    private Set<String> scopes(Object value) {
        if (value instanceof String text) return stringSet(text);
        return value instanceof java.util.List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).collect(java.util.stream.Collectors.toUnmodifiableSet())
                : Set.of();
    }

    private Set<String> stringSet(Object value) {
        if (value instanceof String text) return Set.of(text.split("\\s+")).stream().filter(s -> !s.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return Set.of();
    }

    private java.util.List<URI> uriList(Object value) {
        if (!(value instanceof java.util.List<?> list)) return java.util.List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).map(URI::create).toList();
    }

    private Instant instant(Object value) {
        return value instanceof Number number ? Instant.ofEpochSecond(number.longValue()) : null;
    }

    private URI uri(Object value) {
        return value instanceof String text && !text.isBlank() ? URI.create(text) : null;
    }

    private String string(Object value) {
        return value instanceof String text ? text : null;
    }

    private String stringOrDefault(Object value, String fallback) {
        String result = string(value);
        return result == null || result.isBlank() ? fallback : result;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
