package cn.richie696.component.oauth.client;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** 基于标准 Bearer Token HTTP 调用 OIDC UserInfo。 */
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
