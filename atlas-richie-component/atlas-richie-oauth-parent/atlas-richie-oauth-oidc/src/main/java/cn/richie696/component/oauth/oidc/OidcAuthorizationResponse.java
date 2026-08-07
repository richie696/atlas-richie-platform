package cn.richie696.component.oauth.oidc;

import java.util.Map;

/** OIDC 授权响应领域模型；query/form_post 的序列化由 HTTP 适配层完成。 */
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
