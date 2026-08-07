package cn.richie696.component.oauth.contract.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/**
 * RFC 7662 Token Introspection 的响应 record, 含 active、clientId、token_type、scope、iss / aud / exp / iat / jti 等标准字段, 额外承载未类型化的 claims map。
 * <p>
 * 处于契约层 introspection 端点出参一环, 由 introspection 服务生成, Resource Server 的 introspection 客户端反序列化后用于构建 {@link OAuthPrincipal}; 仅 active=false 的情况下无需提供其他字段。
 * 解决"不同 issuer 对 introspection 响应字段差异大、Opaque Token 验证缺乏统一表达"的问题, 用 active 这个唯一布尔位把"无效 token"与"token 详情"统一在同一 record 中, 让 Resource Server 客户端逻辑保持简单。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OAuthIntrospectionResponse(
        boolean active,
        @JsonProperty("client_id")
        String clientId,
        @JsonProperty("token_type")
        String tokenType,
        String scope,
        @JsonProperty("sub")
        String subject,
        @JsonProperty("iss")
        String issuer,
        @JsonProperty("aud")
        String audience,
        @JsonProperty("exp")
        long expiresAt,
        @JsonProperty("iat")
        long issuedAt,
        @JsonProperty("jti")
        String tokenId,
        Map<String, Object> claims
) {
    public OAuthIntrospectionResponse {
        claims = claims == null ? Collections.emptyMap() : Map.copyOf(claims);
    }

    public static OAuthIntrospectionResponse inactive() {
        return new OAuthIntrospectionResponse(false, null, null, null, null, null,
                null, 0, 0, null, Collections.emptyMap());
    }
}
