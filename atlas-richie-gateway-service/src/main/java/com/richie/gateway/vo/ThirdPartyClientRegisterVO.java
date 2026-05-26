package com.richie.gateway.vo;

import com.google.common.base.MoreObjects;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 第三方客户端注册（测试用）返回 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThirdPartyClientRegisterVO {

    /**
     * 客户端ID（即 client_id）
     */
    @JsonProperty("client_id")
    private String clientId;

    /**
     * 客户端密钥（即 client_secret）
     */
    @JsonProperty("client_secret")
    private String clientSecret;
}
