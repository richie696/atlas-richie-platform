package com.richie.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;

/**
 * 重定向策略配置
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:49:35
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.security.redirect")
public class RedirectConfig implements Serializable {

    /**
     * 重定向URI（默认："/"）
     * <p style="color: yellow">
     *     如果 SecurityFilterConfig.getRule() 为 SecurityRuleEnum.REDIRECT，
     *     则会将请求重定向到该URI，如果该值未配置会重定向到根页面
     */
    private String securityRedirectUri = "/";

    /**
     * 默认构造函数
     */
    public RedirectConfig() {
    }
}
