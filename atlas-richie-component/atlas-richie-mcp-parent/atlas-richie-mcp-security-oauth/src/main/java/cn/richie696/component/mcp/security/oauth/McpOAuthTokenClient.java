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

/**
 * Framework-neutral OAuth token, introspection and dynamic registration client.
 *
 * <p>框架中立的 OAuth HTTP 客户端，封装四类标准交互：</p>
 * <ul>
 *   <li>{@link #authorizationCode authorization_code} 授权码换 token（RFC 6749 §4.1.3）；</li>
 *   <li>{@link #refreshToken refresh_token} 刷新 access token（RFC 6749 §6）；</li>
 *   <li>{@link #clientCredentials client_credentials} 机机凭证流（RFC 6749 §4.4）；</li>
 *   <li>{@link #introspect token introspection}（RFC 7662）；</li>
 *   <li>{@link #register Dynamic Client Registration}（RFC 7591）。</li>
 * </ul>
 *
 * <p>类与 {@link McpOAuthMetadataClient} 是兄弟：一个负责发现阶段，一个负责运行时交互；
 * 它们共享相同的 {@link McpOAuthUriPolicy} 边界，确保 token endpoint / registration endpoint
 * 同样经过 SSRF 校验。</p>
 *
 * <p>框架中立：不绑定 Spring / Jakarta RestClient，直接基于 JDK {@link HttpClient} 实现，
 * 方便嵌入 MCP starter、CLI 与测试桩。所有 HTTP 失败统一包装为 {@link IllegalStateException}，
 * 异常链保留根因便于诊断。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpOAuthTokenClient {
    private final HttpClient httpClient;
    private final Duration timeout;
    private final McpOAuthUriPolicy uriPolicy;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 便捷构造器：默认采用 HTTPS-only URI 策略。
     *
     * @param httpClient 已配置好的 HTTP 客户端（建议复用、启用连接池）
     * @param timeout 单次请求超时
     */
    public McpOAuthTokenClient(HttpClient httpClient, Duration timeout) {
        this(httpClient, timeout, McpOAuthUriPolicy.httpsOnly());
    }

    /**
     * 主构造器：支持自定义 URI 策略。
     *
     * <p>暴露该重载是为了在受信任的内部网络场景允许 http 端点；外部使用应保留默认
     HTTPS-only 策略以防 SSRF。</p>
     *
     * @param httpClient HTTP 客户端（必填）
     * @param timeout 请求超时（必填）
     * @param uriPolicy URI 安全策略（必填）
     * @throws NullPointerException 任一参数为 null 时
     */
    public McpOAuthTokenClient(
            HttpClient httpClient,
            Duration timeout,
            McpOAuthUriPolicy uriPolicy) {
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
        this.timeout = java.util.Objects.requireNonNull(timeout, "timeout");
        this.uriPolicy = java.util.Objects.requireNonNull(uriPolicy, "uriPolicy");
    }

    /**
     * 执行 authorization_code grant：用授权码 + PKCE verifier 换取 access token。
     *
     * <p>实现符合 RFC 6749 §4.1.3 + RFC 7636：grant_type 固定为 {@code authorization_code}，
     必须携带 {@code code}、{@code redirect_uri}（须与初始授权请求一致）、{@code code_verifier}。
     {@code resource} 与 {@code scopes} 可选，用于 RFC 8707 资源指示与 scope 降级请求。</p>
     *
     * @param tokenEndpoint token 端点 URI
     * @param clientId 客户端标识
     * @param clientSecret 客户端密钥（public client 可为 null）
     * @param code 授权服务器回传的授权码
     * @param redirectUri 与授权请求一致的回调 URI
     * @param codeVerifier 与授权请求 PKCE challenge 对应的 verifier
     * @param resource 受众资源指示符（RFC 8707），可为 null
     * @param scopes 请求的 scope 集合，可为 null
     * @return 包含 access token、refresh token、scopes 的完整响应
     * @throws IllegalArgumentException 当必填参数缺失时
     * @throws IllegalStateException 当 HTTP 非 2xx、响应非 JSON 或 AS 返回 OAuth error 时
     */
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

    /**
     * 执行 refresh_token grant：用 refresh token 换取新的 access token。
     *
     * <p>实现符合 RFC 6749 §6：grant_type 固定为 {@code refresh_token}，
     必须携带 {@code refresh_token}。可选传入新的 scope 子集以缩小授权范围
     （AS 可拒绝 scope 升级请求，但允许 scope 降级）。</p>
     *
     * @param tokenEndpoint token 端点 URI
     * @param clientId 客户端标识
     * @param clientSecret 客户端密钥（public client 可为 null）
     * @param refreshToken 先前获得的 refresh token
     * @param resource 受众资源指示符（RFC 8707），可为 null
     * @param scopes 请求的 scope 子集（用于 scope 降级），可为 null
     * @return 包含新 access token、可选的新 refresh token、scopes 的响应
     * @throws IllegalArgumentException 当必填参数缺失时
     * @throws IllegalStateException 当 HTTP 非 2xx、响应非 JSON 或 AS 返回 OAuth error 时
     */
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

    /**
     * 执行 client_credentials grant：机机凭证流换 token。
     *
     * <p>实现符合 RFC 6749 §4.4：grant_type 固定为 {@code client_credentials}，
     常用于 MCP 服务端以自身名义调用受保护 API 的场景。无需用户上下文。</p>
     *
     * @param tokenEndpoint token 端点 URI
     * @param clientId 客户端标识
     * @param clientSecret 客户端密钥（confidential client 必填）
     * @param resource 受众资源指示符（RFC 8707），可为 null
     * @param scopes 请求的 scope 集合，可为 null
     * @return 包含 access token、scopes 的响应（client_credentials 通常不返回 refresh token）
     * @throws IllegalArgumentException 当必填参数缺失时
     * @throws IllegalStateException 当 HTTP 非 2xx、响应非 JSON 或 AS 返回 OAuth error 时
     */
    public McpOAuthTokenResponse clientCredentials(
            URI tokenEndpoint,
            String clientId,
            String clientSecret,
            URI resource,
            Set<String> scopes) {
        Map<String, String> form = baseGrant("client_credentials", clientId, resource, scopes);
        return token(tokenEndpoint, clientId, clientSecret, form, resource);
    }

    /**
     * 调用 token introspection 端点（RFC 7662）查询 token 当前状态。
     *
     * <p>典型用途：资源服务器侧对 opaque access token 做活性校验；
     由于 RFC 7662 要求对 introspection 端点做客户端认证，本方法在 clientId/clientSecret
     不为空时会自动附加 HTTP Basic Authorization 头。</p>
     *
     * @param introspectionEndpoint introspection 端点 URI
     * @param clientId 客户端标识
     * @param clientSecret 客户端密钥（introspection 必须）
     * @param token 待校验的 token 字符串
     * @return RFC 7662 响应的内部模型
     * @throws IllegalArgumentException 当 token 为空时
     * @throws IllegalStateException 当 HTTP 非 2xx、响应非 JSON 或 AS 返回 OAuth error 时
     */
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

    /**
     * 执行 Dynamic Client Registration（RFC 7591）。
     *
     * <p>与 token 端点不同：registration 端点是 JSON 请求体而非 form，HTTP Basic
     认证仅在 client 已持有凭据时使用；首次匿名注册（{@code registrationRequest} 中不含
 * client_secret）时不发送 Authorization 头，由 AS 直接分配 client_id。</p>
     *
     * @param registrationEndpoint registration 端点 URI
     * @param registrationRequest 客户端元数据载荷（software_id、redirect_uris、grant_types 等）
     * @return 包含 client_id、client_secret（若有）、管理端点 URI 与管理令牌
     * @throws IllegalStateException 当 HTTP 非 2xx、响应非 JSON 或 AS 返回 OAuth error 时
     * @throws IllegalArgumentException 当响应缺少必填的 {@code client_id} 时
     */
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
