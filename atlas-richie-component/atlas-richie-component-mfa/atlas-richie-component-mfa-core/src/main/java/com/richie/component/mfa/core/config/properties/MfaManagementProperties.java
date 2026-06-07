package com.richie.component.mfa.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理模块配置（内部类）
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.management")
public class MfaManagementProperties {

    /**
     * 发行方名称（显示在Authenticator应用中）
     * <p>
     * 用于在二维码和 Authenticator 应用中显示发行方名称
     * <p>
     * 默认值："Atlas Richie Inc."
     */
    private String issuer = "Atlas Richie Inc.";

    /**
     * 是否启用Controller（默认启用）
     * <p>
     * 控制是否启用 MfaManagementController，提供 RESTful API
     * <p>
     * 默认值：true
     */
    private boolean controllerEnabled = true;

    /**
     * API路径前缀
     * <p>
     * MfaManagementController 的请求路径前缀
     * <p>
     * 默认值："/api/mfa"
     */
    private String apiPrefix = "/api/mfa";

    /**
     * 缓存TTL（小时）
     * <p>
     * MFA用户信息在 GlobalCache 中的过期时间（小时）
     * <p>
     * 默认值：24小时
     */
    private int ttlHours = 24;

}
