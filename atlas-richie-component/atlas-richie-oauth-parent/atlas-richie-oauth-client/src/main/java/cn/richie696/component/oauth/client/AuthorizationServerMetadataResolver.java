package cn.richie696.component.oauth.client;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * RFC 8414 Authorization Server Metadata 的 HTTP 发现客户端，按 OAuth 标准端点解析
 * 颁发端各 endpoint 与能力声明。
 *
 * <p>处于 OAuth Service / 业务侧 Relying Party 与外部 Authorization Server 之间：
 * 上游给定 {@code /.well-known/oauth-authorization-server} 之类的 metadata URI，下游
 * 直接发起一次 GET 请求并把 JSON 反序列化为 {@link AuthorizationServerMetadata} record，
 * 后续 token / introspection 调用都以此为入口。本类不持有任何状态、不缓存解析结果，
 * 也不绑定 OkHttp / Spring RestClient，使用 JDK 内置的 {@code java.net.http.HttpClient}。
 *
 * <p>解决"业务系统要对接自建 AS、PaaS IdP 或第三方 OAuth 服务，每次都要重写一遍
 * metadata 解析 + form 拼接"的接入门槛问题，把 RFC 8414 规定的字段统一抽象为 record，
 * 并强制按 RFC 文档的 snake_case 名取 JSON 字段，避免不同 AS 实现拼写漂移造成的字段
 * 读取失败。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class AuthorizationServerMetadataResolver {

    private final java.net.http.HttpClient httpClient;
    private final tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    public AuthorizationServerMetadataResolver(Duration timeout) {
        this(java.net.http.HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    AuthorizationServerMetadataResolver(java.net.http.HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public AuthorizationServerMetadata resolve(URI metadataUri) {
        try {
            var request = java.net.http.HttpRequest.newBuilder(metadataUri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET().build();
            var response = httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new OAuthClientException("Metadata endpoint 返回 HTTP " + response.statusCode(), response.statusCode(), null);
            }
            Map<String, Object> json = objectMapper.readValue(response.body(), Map.class);
            return new AuthorizationServerMetadata(
                    text(json, "issuer"),
                    uri(json, "authorization_endpoint"),
                    uri(json, "token_endpoint"),
                    uri(json, "device_authorization_endpoint"),
                    uri(json, "introspection_endpoint"),
                    uri(json, "revocation_endpoint"),
                    uri(json, "jwks_uri"),
                    strings(json, "response_types_supported"),
                    strings(json, "grant_types_supported"),
                    strings(json, "code_challenge_methods_supported"),
                    strings(json, "scopes_supported"));
        } catch (OAuthClientException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthClientException("读取 Authorization Server Metadata 失败", e);
        }
    }

    private String text(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : value.toString();
    }

    private URI uri(Map<String, Object> json, String key) {
        String value = text(json, key);
        return value == null || value.isBlank() ? null : URI.create(value);
    }

    private List<String> strings(Map<String, Object> json, String key) {
        Object value = json.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(Object::toString).toList();
    }

    public record AuthorizationServerMetadata(
            String issuer,
            URI authorizationEndpoint,
            URI tokenEndpoint,
            URI deviceAuthorizationEndpoint,
            URI introspectionEndpoint,
            URI revocationEndpoint,
            URI jwksUri,
            List<String> responseTypesSupported,
            List<String> grantTypesSupported,
            List<String> codeChallengeMethodsSupported,
            List<String> scopesSupported
    ) {
        public AuthorizationServerMetadata(String issuer, URI authorizationEndpoint, URI tokenEndpoint,
                                           URI introspectionEndpoint, URI revocationEndpoint, URI jwksUri,
                                           List<String> responseTypesSupported, List<String> grantTypesSupported,
                                           List<String> codeChallengeMethodsSupported, List<String> scopesSupported) {
            this(issuer, authorizationEndpoint, tokenEndpoint, null, introspectionEndpoint,
                    revocationEndpoint, jwksUri, responseTypesSupported, grantTypesSupported,
                    codeChallengeMethodsSupported, scopesSupported);
        }
    }
}
