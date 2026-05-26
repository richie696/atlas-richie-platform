package com.richie.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 第三方客户端注册（测试用）请求 DTO。
 * 仅用于本地/测试环境快速注册 clientId/clientSecret，便于联调第三方网关。
 */
@Data
public class ThirdPartyClientRegisterDTO {

    /**
     * 客户端名称
     */
    @JsonProperty("client_name")
    private String clientName;
}
