package com.richie.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OAuth2.0 Token 请求 DTO
 * <p>
 * 支持 client_credentials 和 refresh_token 两种授权模式。
 * <p>
 * 认证方式：
 * - client_id 和 client_secret 必须通过 HTTP Basic Auth 传递（{@code Authorization: Basic <base64(client_id:client_secret)>}）
 * - scope 由系统根据客户端注册的权限范围自动授权，调用方不能自定义
 * <p>
 * form-urlencoded 请求示例：
 * {@code
 * grant_type=client_credentials
 * }
 * <p>
 * HTTP Basic Auth 示例：
 * {@code
 * Authorization: Basic base64(client_id:client_secret)
 * }
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
@Data
public class OAuth2TokenRequestDTO {

    /** 授权类型：client_credentials / refresh_token */
    @JsonProperty("grant_type")
    private String grantType;

    /** 客户端 ID */
    @JsonProperty("client_id")
    private String clientId;

    /** 客户端密钥 */
    @JsonProperty("client_secret")
    private String clientSecret;

    /** 权限范围（可选），空格分隔 */
    @JsonProperty("scope")
    private String scope;

    /** 刷新令牌（refresh_token 模式必填） */
    @JsonProperty("refresh_token")
    private String refreshToken;
}

