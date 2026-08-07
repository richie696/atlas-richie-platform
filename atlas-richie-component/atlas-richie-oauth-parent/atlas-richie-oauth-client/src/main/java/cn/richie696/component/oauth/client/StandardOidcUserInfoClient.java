package cn.richie696.component.oauth.client;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 基于 RFC 6750 Bearer Token HTTP 调用 OIDC UserInfo endpoint 的默认实现。
 *
 * <p>处于业务侧 RP / OAuth Service 与外部 OIDC Provider 之间：上游传入 access token，
 * 下游本实现按 OIDC Core 1.0 §5.3 把 token 作为 {@code Authorization: Bearer ...} 头
 * 投递到 UserInfo endpoint，并把响应 JSON 原样以 Map 形式返回。它使用 JDK 内置
 * {@code java.net.http.HttpClient}，不持有任何状态、不缓存响应，调用语义与
 * {@link StandardOAuthTokenClient} 保持一致。
 *
 * <p>解决"业务系统要对接不同 OIDC Provider 的 UserInfo 时必须自己拼接 Authorization
 * 头、处理 401/403、按 OIDC 协议 error 字段抛出异常"的标准协议面问题，把 Bearer Token
 * + JSON 响应 + OAuth error 解析收敛到一处，让 RP 只关心 Claims 内容、不被 HTTP
 * 细节绑架。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class StandardOidcUserInfoClient implements OidcUserInfoClient {

    private final URI userInfoEndpoint;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StandardOidcUserInfoClient(URI userInfoEndpoint, Duration timeout) {
        this(userInfoEndpoint, HttpClient.newBuilder().connectTimeout(timeout).build());
    }

    StandardOidcUserInfoClient(URI userInfoEndpoint, HttpClient httpClient) {
        this.userInfoEndpoint = userInfoEndpoint;
        this.httpClient = httpClient;
    }

    @Override
    public Map<String, Object> load(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder(userInfoEndpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> json = response.body().isBlank()
                    ? Map.of() : objectMapper.readValue(response.body(), Map.class);
            if (response.statusCode() / 100 != 2) {
                throw new OAuthClientException("OIDC UserInfo 返回 HTTP " + response.statusCode(),
                        response.statusCode(), text(json, "error"));
            }
            return Map.copyOf(json);
        } catch (OAuthClientException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthClientException("调用 OIDC UserInfo 失败", e);
        }
    }

    private String text(Map<String, Object> json, String key) {
        Object value = json.get(key);
        return value == null ? null : value.toString();
    }
}
