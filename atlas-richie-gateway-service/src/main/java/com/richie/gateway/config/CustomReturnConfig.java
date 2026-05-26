package com.richie.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

/**
 * 自定义返回配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:48:53
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.security.custom-return")
public class CustomReturnConfig implements Serializable {

    /**
     * 安全过滤器的封禁时间单位（默认：小时）
     */
    private HttpStatus status = HttpStatus.FORBIDDEN;

    /**
     * IP黑名单的缓存路径
     */
    private String errorMessage = "请求过于频繁，请稍后再试。";

}
