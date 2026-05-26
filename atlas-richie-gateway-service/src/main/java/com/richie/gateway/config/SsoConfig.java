package com.richie.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * SSO配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2024-04-28 16:04:21
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.sso")
public class SsoConfig {

    /** 默认构造函数，供配置绑定使用。 */
    public SsoConfig() {
    }

    /**
     * 是否启用SSO过滤器（默认：false）
     */
    private boolean enable = false;

    /**
     * SSO登录页地址（默认：<a href="http://localhost:8080/login">http://localhost:8080/login</a>）
     */
    private String ssoLoginUrl = "http://localhost:8080/login";

    /**
     * 已登陆token的储存路径（非必填，默认：platform:gateway:last-online-token:）
     */
    private String onlineTokenPath = "platform:gateway:last-online-token:";

    /**
     * 是否启用门户系统的SSO对接
     */
    private SsoPortalConfig portal = new SsoPortalConfig();

    /**
     * SSO门户配置类
     *
     * @author richie696
     * @version 1.0
     * @since 2024-08-05 16:00:32
     */
    @Data
    public static class SsoPortalConfig {

        /** 默认构造函数，供配置绑定使用。 */
        public SsoPortalConfig() {
        }

        /**
         * 是否启用SSO过滤器（默认：false）
         */
        private boolean enable = false;

        /**
         * 门户服务主机检查令牌有效性检测地址
         */
        private String checkTokenUrl = "http://localhost:8080/sign/authz/oauth/v20/check_token";

    }
}
