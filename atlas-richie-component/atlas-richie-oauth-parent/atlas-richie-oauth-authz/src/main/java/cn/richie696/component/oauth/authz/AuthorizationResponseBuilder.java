package cn.richie696.component.oauth.authz;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/** 安全构造授权结果 URI，保证 code/state/error 不直接拼接进 URL。 */
public final class AuthorizationResponseBuilder {

    public URI success(String redirectUri, String code, String state) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("code", code);
        if (state != null) parameters.put("state", state);
        return append(redirectUri, parameters);
    }

    public URI error(String redirectUri, String error, String description, String state) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("error", error);
        if (description != null) parameters.put("error_description", description);
        if (state != null) parameters.put("state", state);
        return append(redirectUri, parameters);
    }

    public URI append(String redirectUri, Map<String, String> parameters) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri 不能为空");
        }
        URI base = URI.create(redirectUri);
        String query = parameters.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String existing = base.getQuery();
        String merged = existing == null || existing.isBlank() ? query : existing + "&" + query;
        try {
            return new URI(base.getScheme(), base.getRawAuthority(), base.getPath(), merged, base.getRawFragment());
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("redirectUri 无效", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
