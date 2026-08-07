package cn.richie696.component.oauth.client;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用 JDK HttpClient + {@code application/x-www-form-urlencoded} 调自建或 PaaS AS 的
 * OAuth 协议客户端默认实现。
 *
 * <p>处于业务系统 / OAuth Service 与任意 Authorization Server 之间：上游按 RFC 6749 /
 * RFC 7662 填好 {@link OAuthTokenRequest} 或传入 token 字符串，下游本实现完成 client
 * 认证（{@code client_secret_basic} 或 {@code client_secret_post}）、form 编码、
 * HTTP 调用与 JSON 反序列化，并把结果封装成 {@link OAuthTokenResponse} /
 * {@link OAuthIntrospectionResponse}。类本身不持有 token 缓存、不感知重试与熔断，
 * 这些横切关注点交给外层装饰器。
 *
 * <p>解决"业务系统对接不同 OAuth AS 时必须重新拼装 form 参数、适配 client 认证方式、
 * 处理 OAuth 协议 error 字段"的接入门槛，把 RFC 6749 / RFC 7662 规定的标准 form 编码
 * 与错误响应处理收敛到一个组件，让业务侧只需按语义调用即可，不必关心 HTTP 协议细节。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class StandardOAuthTokenClient implements OAuthTokenClient {

    private final URI tokenEndpoint;
    private final URI introspectionEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final String clientAuthenticationMethod;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StandardOAuthTokenClient(URI tokenEndpoint, URI introspectionEndpoint,
                                    String clientId, String clientSecret, Duration timeout) {
        this(tokenEndpoint, introspectionEndpoint, clientId, clientSecret, timeout,
                "client_secret_basic", HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    public StandardOAuthTokenClient(URI tokenEndpoint, URI introspectionEndpoint,
                                    String clientId, String clientSecret, Duration timeout,
                                    String clientAuthenticationMethod) {
        this(tokenEndpoint, introspectionEndpoint, clientId, clientSecret, timeout,
                clientAuthenticationMethod, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    StandardOAuthTokenClient(URI tokenEndpoint, URI introspectionEndpoint,
                             String clientId, String clientSecret, Duration timeout,
                             HttpClient httpClient) {
        this(tokenEndpoint, introspectionEndpoint, clientId, clientSecret, timeout,
                "client_secret_basic", httpClient);
    }

    StandardOAuthTokenClient(URI tokenEndpoint, URI introspectionEndpoint,
                             String clientId, String clientSecret, Duration timeout,
                             String clientAuthenticationMethod, HttpClient httpClient) {
        this.tokenEndpoint = tokenEndpoint;
        this.introspectionEndpoint = introspectionEndpoint;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clientAuthenticationMethod = clientAuthenticationMethod == null
                ? "client_secret_basic" : clientAuthenticationMethod;
        this.httpClient = httpClient;
    }

    @Override
    public OAuthTokenResponse requestToken(OAuthTokenRequest request) {
        Map<String, String> params = new HashMap<>();
        put(params, "grant_type", request.grantType());
        put(params, "client_id", request.clientId() == null ? clientId : request.clientId());
        put(params, "client_secret", request.clientSecret() == null ? clientSecret : request.clientSecret());
        put(params, "code", request.code());
        put(params, "code_verifier", request.codeVerifier());
        put(params, "redirect_uri", request.redirectUri());
        put(params, "refresh_token", request.refreshToken());
        put(params, "scope", request.scope());
        put(params, "resource", request.resource());
        put(params, "device_code", request.deviceCode());
        Map<String, Object> json = post(tokenEndpoint, params);
        return new OAuthTokenResponse(text(json, "access_token"), text(json, "token_type"),
                number(json, "expires_in"), text(json, "refresh_token"), text(json, "scope"));
    }

    @Override
    public OAuthIntrospectionResponse introspect(String token) {
        Map<String, String> params = Map.of("token", token, "token_type_hint", "access_token");
        Map<String, Object> json = post(introspectionEndpoint, params);
        Map<String, Object> claims = new HashMap<>(json);
        return new OAuthIntrospectionResponse(Boolean.TRUE.equals(json.get("active")),
                text(json, "client_id"), text(json, "token_type"), text(json, "scope"),
                text(json, "sub"), text(json, "iss"), text(json, "aud"),
                number(json, "exp"), number(json, "iat"), text(json, "jti"), claims);
    }

    private Map<String, Object> post(URI endpoint, Map<String, String> params) {
        if (endpoint == null) {
            throw new OAuthClientException("OAuth endpoint 未配置", 0, null);
        }
        try {
            Map<String, String> formParams = new HashMap<>(params);
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form(formParams)));
            if ("client_secret_basic".equalsIgnoreCase(clientAuthenticationMethod)
                    && clientId != null && clientSecret != null) {
                formParams.remove("client_id");
                formParams.remove("client_secret");
                builder.method("POST", HttpRequest.BodyPublishers.ofString(form(formParams)));
                String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret)
                        .getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + basic);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = response.body().isBlank()
                    ? Map.of() : objectMapper.readValue(response.body(), Map.class);
            if (response.statusCode() / 100 != 2) {
                throw new OAuthClientException("OAuth endpoint 返回 HTTP " + response.statusCode(),
                        response.statusCode(), text(json, "error"));
            }
            return json;
        } catch (OAuthClientException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthClientException("调用 OAuth endpoint 失败", e);
        }
    }

    private String form(Map<String, String> params) {
        List<String> values = new ArrayList<>();
        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                values.add(encode(key) + "=" + encode(value));
            }
        });
        return String.join("&", values);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void put(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    private String text(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : value.toString();
    }

    private long number(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0 : Long.parseLong(value.toString());
    }
}
