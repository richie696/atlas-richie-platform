package cn.richie696.component.oauth.contract.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token endpoint 的标准响应 record, 含 access_token、token_type、expires_in、refresh_token、scope。
 * <p>
 * 处于契约层 token 端点出参一环, 由 token endpoint 服务生成, HTTP 适配层按 RFC 6749 §5.1 直接序列化返回, Resource Server 客户端按 token_type 反序列化后进入验证流程。
 * 解决"OAuth 响应字段在不同 grant 下形态不统一, 客户端难以按 RFC 解析"的问题, 用最少集合保证 wire 兼容性, 任何非标字段 (如 id_token、DPoP 证明) 都由扩展层而非本 record 承担。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OAuthTokenResponse(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("expires_in")
        long expiresIn,
        @JsonProperty("refresh_token")
        String refreshToken,
        String scope
) {
}
