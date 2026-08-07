package cn.richie696.component.oauth.oidc;

import java.util.Collections;
import java.util.Map;

/** UserInfo endpoint 的标准响应模型。 */
public record OidcUserInfo(String subject, Map<String, Object> claims) {
    public OidcUserInfo {
        claims = claims == null ? Collections.emptyMap() : Map.copyOf(claims);
    }

    public Map<String, Object> asMap() {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sub", subject);
        result.putAll(claims);
        return Map.copyOf(result);
    }
}
