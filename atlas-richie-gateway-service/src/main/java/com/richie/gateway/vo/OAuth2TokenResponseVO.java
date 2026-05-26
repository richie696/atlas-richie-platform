package com.richie.gateway.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2.0 Token 响应 VO（符合 RFC 6749 命名：snake_case 字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2TokenResponseVO {

    /** 访问令牌（JWT 格式） */
    @JsonProperty("access_token")
    private String accessToken;

    /** 令牌类型（固定为 \"Bearer\"） */
    @JsonProperty("token_type")
    private String tokenType;

    /** 访问令牌有效期（秒） */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /** 刷新令牌 */
    @JsonProperty("refresh_token")
    private String refreshToken;
}

