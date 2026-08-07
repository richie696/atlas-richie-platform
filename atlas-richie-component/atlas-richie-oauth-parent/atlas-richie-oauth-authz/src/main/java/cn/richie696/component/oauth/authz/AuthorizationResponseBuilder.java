package cn.richie696.component.oauth.authz;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 授权响应 redirect URI 的安全构造器。
 * <p>
 * 把 success(带回 code/state)与 error(带回 error/error_description/state)两类回调统一收敛到
 * {@link URI} 构造:对 query 参数做 UTF-8 URL 编码、保留 base 的 authority/path/fragment,杜绝
 * 字符串拼接导致的注入与编码歧义。
 * </p>
 * <p>
 * 处于 oauth-authz 的 HTTP 边界位置:由 {@link AuthorizationEndpoint} 在用户授权确认后调用,
 * 把生成的回调 URI 通过 {@code response.sendRedirect} 返回给客户端;不依赖 Servlet API,
 * 可以在任意 HTTP 框架中复用。
 * </p>
 * <p>
 * 解决的问题:消除"拼接 redirect_uri + ?code="这种手写代码带来的 XSS / open-redirect 风险,
 * 把安全构造 URI 这件事封装为一个高复用的工具类,让所有授权端点(authorization_code、
 * 后续 hybrid/id_token)都走同一套安全规则。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
