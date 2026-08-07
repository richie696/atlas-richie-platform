package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/** OAuth 标准 HTTP 参数构造和重定向回调解析工具，不绑定 MockMvc/WebTestClient。 */
public final class OAuthTestHttp {

    private OAuthTestHttp() {
    }

    public static Map<String, String> tokenForm(OAuthTokenRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, String> form = new LinkedHashMap<>();
        put(form, "grant_type", request.grantType());
        put(form, "client_id", request.clientId());
        put(form, "client_secret", request.clientSecret());
        put(form, "code", request.code());
        put(form, "code_verifier", request.codeVerifier());
        put(form, "redirect_uri", request.redirectUri());
        put(form, "refresh_token", request.refreshToken());
        put(form, "scope", request.scope());
        put(form, "resource", request.resource());
        return Map.copyOf(form);
    }

    public static String authorizationQuery(OAuthAuthorizationRequest request) {
        Objects.requireNonNull(request, "request");
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "client_id", request.clientId());
        put(values, "redirect_uri", request.redirectUri());
        put(values, "response_type", request.responseType());
        put(values, "scope", String.join(" ", request.scopes()));
        put(values, "state", request.state());
        put(values, "resource", request.resource());
        put(values, "code_challenge", request.codeChallenge());
        put(values, "code_challenge_method", request.codeChallengeMethod());
        StringJoiner query = new StringJoiner("&");
        values.forEach((key, value) -> query.add(encode(key) + "=" + encode(value)));
        return query.toString();
    }

    public static URI authorizationUri(URI endpoint, OAuthAuthorizationRequest request) {
        String separator = endpoint.toString().contains("?") ? "&" : "?";
        return URI.create(endpoint + separator + authorizationQuery(request));
    }

    public static Map<String, String> queryParameters(URI uri) {
        Objects.requireNonNull(uri, "uri");
        Map<String, String> result = new LinkedHashMap<>();
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            result.put(decode(key), decode(value));
        }
        return Map.copyOf(result);
    }

    public static String bearer(String accessToken) {
        return "Bearer " + Objects.requireNonNull(accessToken, "accessToken");
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
