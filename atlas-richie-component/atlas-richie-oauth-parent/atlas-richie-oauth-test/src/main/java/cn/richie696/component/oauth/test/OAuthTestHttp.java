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

/**
 * OAuth 测试支撑工具（不属于生产运行时）：OAuth 标准 HTTP 参数与重定向回调解析工具。
 *
 * <p>职责链位置：处于 {@link OAuthTestFixtures} 协议夹具与 {@link OAuthTestHttpClient}
 * 等具体 HTTP 客户端之间。它把 {@code OAuthTokenRequest} 转成 application/x-www-form-urlencoded
 * 表单、把 {@code OAuthAuthorizationRequest} 拼成授权端点的 query 串，
 * 并把授权码回调的 URI 反解为参数 Map；只依赖 JDK URL 编解码，
 * 不绑定 MockMvc、WebTestClient 或任何 HTTP 测试栈。</p>
 *
 * <p>解决以下问题：不同测试客户端（MockMvc / WebTestClient / JDK HttpClient）
 * 在 OAuth 协议上的参数拼装与回调解析若各自实现会重复且容易出现协议偏差；
 * 把"协议字段 → 协议 wire 格式"的转换集中在一个工具里，使各客户端只需要关心"如何发送/接收"，
 * 而不重复实现 RFC 6749 / RFC 7636 的字段拼装规则。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
