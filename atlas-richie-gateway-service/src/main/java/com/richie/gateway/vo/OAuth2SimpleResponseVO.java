package com.richie.gateway.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简单结果 VO（用于撤销等简单成功/失败响应）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2SimpleResponseVO {

    @JsonProperty("code")
    private String code;

    @JsonProperty("msg")
    private String message;
}
