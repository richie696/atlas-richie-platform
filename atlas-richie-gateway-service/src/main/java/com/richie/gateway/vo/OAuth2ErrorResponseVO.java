package com.richie.gateway.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OAuth2.0 错误响应 VO（符合 RFC 6749）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2ErrorResponseVO {

    /**
     * 错误码，例如：invalid_request / invalid_client / invalid_grant ...
     */
    @JsonProperty("error")
    private String error;

    /**
     * 错误描述（可读文本）
     */
    @JsonProperty("error_description")
    private String errorDescription;

    /**
     * 错误说明文档链接（可选）
     */
    @JsonProperty("error_uri")
    private String errorUri;
}
