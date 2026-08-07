package cn.richie696.component.oauth.oidc;

import java.util.Collections;
import java.util.Map;

/**
 * OIDC UserInfo endpoint 的标准响应模型，承载必有的 {@code sub} 字段与其它 Claims。
 *
 * <p>处于 {@link OidcUserInfoService} 与 OAuth Service 的 UserInfo Controller 之间：
 * 上游经过 scope 过滤后的安全视图传入本 record，下游通过 {@link #asMap()} 直接交给
 * JSON 序列化器。模型本身只关心字段结构与不可变性，不接触任何用户表或身份源。
 *
 * <p>解决"OIDC UserInfo 响应是裸 Map、客户端解析字段名易拼错"的一致性问题，
 * 把标准字段（sub）作为 record 组件固定下来，其它 Claims 通过不可变 Map 携带，
 * 避免下游误改导致返回内容意外漂移。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
