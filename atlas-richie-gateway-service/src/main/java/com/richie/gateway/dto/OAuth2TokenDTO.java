package com.richie.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OAuth2.0 令牌操作请求 DTO（用于校验 / 撤销令牌）
 * <p>
 * 对外参数为 snake_case（token, token_type_hint），内部使用 camelCase 字段，
 * 通过 Jackson 的 {@link JsonProperty} 做字段名映射。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-16
 */
@Data
public class OAuth2TokenDTO {

    /** 待处理的令牌（access_token 或 refresh_token） */
    @JsonProperty("token")
    private String token;

    /** 令牌类型提示（可选）：access_token 或 refresh_token */
    @JsonProperty("token_type_hint")
    private String tokenTypeHint;
}

