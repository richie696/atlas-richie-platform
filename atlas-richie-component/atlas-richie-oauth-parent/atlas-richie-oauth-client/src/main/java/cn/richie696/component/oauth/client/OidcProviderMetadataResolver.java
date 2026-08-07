package cn.richie696.component.oauth.client;

import cn.richie696.component.oauth.oidc.OidcProviderMetadata;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OIDC Discovery 客户端，按 {@code /.well-known/openid-configuration} 解析上游 OIDC
 * Provider 的端点与能力声明。
 *
 * <p>处于 OAuth Service 与上游 OIDC Provider（Microsoft Entra ID / Okta / Auth0 / Keycloak
 * 等 PaaS IdP）之间：上游给定 discovery URI，下游发起一次 GET 请求并把响应解析为
 * {@link OidcProviderMetadata}，后续 ID Token 验证、UserInfo 调用、RP-Initiated Logout
 * 都以此为入口。本类不持有任何状态、不缓存解析结果，使用 JDK 内置
 * {@code java.net.http.HttpClient} 完成调用，对 Spring / Servlet 零依赖。
 *
 * <p>解决"OAuth Service 要从本地 IdP 模式切换到对接外部 OIDC Provider 时，需要重新
 * 解析一套 Discovery 字段并改写下游调用"的集成痛点，把所有 OIDC Discovery 字段（含
 * front/backchannel logout 能力）一次性反序列化为不可变 record，让下游只需决定
 * "用哪个端点"，不必再处理 HTTP 与 JSON。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OidcProviderMetadataResolver {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OidcProviderMetadataResolver(Duration timeout) {
        this(HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    OidcProviderMetadataResolver(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public OidcProviderMetadata resolve(URI discoveryUri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(discoveryUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = response.body().isBlank()
                    ? Map.of() : objectMapper.readValue(response.body(), Map.class);
            if (response.statusCode() / 100 != 2) {
                throw new OAuthClientException("OIDC Discovery 返回 HTTP " + response.statusCode(),
                        response.statusCode(), text(json, "error"));
            }
            return new OidcProviderMetadata(
                    text(json, "issuer"),
                    text(json, "authorization_endpoint"),
                    text(json, "token_endpoint"),
                    text(json, "device_authorization_endpoint"),
                    text(json, "userinfo_endpoint"),
                    text(json, "jwks_uri"),
                    text(json, "end_session_endpoint"),
                    strings(json, "response_types_supported"),
                    strings(json, "grant_types_supported"),
                    strings(json, "subject_types_supported"),
                    strings(json, "scopes_supported"),
                    strings(json, "claims_supported"),
                    strings(json, "token_endpoint_auth_methods_supported"),
                    strings(json, "code_challenge_methods_supported"),
                    strings(json, "id_token_signing_alg_values_supported"),
                    strings(json, "response_modes_supported"),
                    bool(json, "frontchannel_logout_supported"),
                    bool(json, "frontchannel_logout_session_supported"),
                    bool(json, "backchannel_logout_supported"),
                    bool(json, "backchannel_logout_session_supported"));
        } catch (OAuthClientException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthClientException("读取 OIDC Discovery Metadata 失败", e);
        }
    }

    private String text(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : value.toString();
    }

    private List<String> strings(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(Object::toString).toList();
    }

    private boolean bool(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value instanceof Boolean booleanValue && booleanValue;
    }
}
