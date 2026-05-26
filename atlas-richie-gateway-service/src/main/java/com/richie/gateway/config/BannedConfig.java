package com.richie.gateway.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * 封禁策略配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:48:53
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.security.banned")
public class BannedConfig implements Serializable {

    /**
     * 安全过滤器的封禁时间单位（默认：小时）
     */
    private TimeUnit securityBlockTimeUnit = TimeUnit.HOURS;

    /**
     * 安全过滤器的封禁时间值（默认：1）
     */
    private Integer securityBlockTime = 1;

    /**
     * 是否永久封禁（默认：false）
     */
    private Boolean permanent = false;

    /**
     * IP黑名单的缓存路径
     */
    private String permanentPath = "platform:gateway:security:permanent";

    /**
     * 获取IP黑名单的缓存过期时间毫秒数的方法
     *
     * @return 返回缓存过期时间毫秒数
     */
    @JsonIgnore
    public long getSecurityBlockTimeMillis() {
        return securityBlockTimeUnit.toMillis(securityBlockTime);
    }

}
