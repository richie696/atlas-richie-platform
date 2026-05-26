package com.richie.gateway.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 安全规则枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:26:07
 */
@Getter
@RequiredArgsConstructor
public enum SecurityRuleEnum {

    /**
     * 封禁IP
     */
    BANNED_IP("bannedPolicy"),

    /**
     * 自定义HTTP状态码
     */
    CUSTOM_HTTP_STATUS("customHttpStatusPolicy"),

    /**
     * 重定向
     */
    REDIRECT("redirectPolicy");

    private final String policyName;
}
