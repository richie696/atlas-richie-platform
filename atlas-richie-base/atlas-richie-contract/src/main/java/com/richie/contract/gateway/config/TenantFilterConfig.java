package com.richie.contract.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 租户过滤器配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:52:24
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.tenant")
public class TenantFilterConfig {

    /** 默认构造函数，供配置绑定使用。 */
    public TenantFilterConfig() {
    }

    /**
     * 是否启用租户过滤器（默认：false）
     */
    private boolean enable = false;

}
