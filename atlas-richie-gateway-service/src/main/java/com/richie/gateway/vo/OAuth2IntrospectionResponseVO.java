package com.richie.gateway.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2.0 Token Introspection 响应 VO（简化版）
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2IntrospectionResponseVO {

    /**
     * 令牌是否有效
     */
    @JsonProperty("active")
    private boolean active;

    /**
     * 客户端 ID（可选）
     */
    @JsonProperty("client_id")
    private String clientId;

    /**
     * 令牌类型（如：Bearer）
     */
    @JsonProperty("token_type")
    private String tokenType;

    /**
     * 权限范围（空格分隔），可选
     */
    @JsonProperty("scope")
    private String scope;
}
