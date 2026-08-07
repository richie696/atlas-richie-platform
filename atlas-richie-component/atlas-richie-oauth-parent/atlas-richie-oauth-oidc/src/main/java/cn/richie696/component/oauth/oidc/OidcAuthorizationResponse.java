package cn.richie696.component.oauth.oidc;

import java.util.Map;

/**
 * OIDC 授权响应领域模型，承载 code/id_token/access_token/state 等参数字段。
 *
 * <p>处于 OIDC 协议层与服务层之间：上游由 {@link OidcAuthorizationResponseService} 在 OAuth Service
 * 完成用户认证与同意后构造，下游由 HTTP 适配层按 {@code response_mode}（query/form_post/fragment）序列化为
 * 浏览器可消费的响应。本模型本身只描述"响应里有哪些字段"，不接触序列化细节。
 *
 * <p>解决"同一份 OIDC 响应需要支持多种 response_mode"导致的重复序列化逻辑，让协议核心与
 * HTTP 投递方式解耦，避免在协议模型中堆砌 Servlet/WebFlux 风格的视图代码。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OidcAuthorizationResponse(
        String code,
        String idToken,
        String accessToken,
        String state,
        String responseMode,
        String error,
        String errorDescription
) {

    public Map<String, String> parameters() {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        put(values, "code", code);
        put(values, "id_token", idToken);
        put(values, "access_token", accessToken);
        put(values, "state", state);
        put(values, "error", error);
        put(values, "error_description", errorDescription);
        return Map.copyOf(values);
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
