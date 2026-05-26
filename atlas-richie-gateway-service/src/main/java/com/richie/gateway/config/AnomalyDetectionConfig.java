package com.richie.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 通用异常检测配置
 * <p>
 * 适用于所有公网接口的异常行为检测（不限于 OAuth2.0）
 *
 * @author richie696
 * @version 1.0
 * @since 2025-12-18
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.security.anomaly-detection")
public class AnomalyDetectionConfig {

    /**
     * 是否启用异常检测（默认：true）
     */
    private boolean enabled = true;

    /**
     * 暴力破解检测配置
     */
    private BruteForceConfig bruteForce = new BruteForceConfig();

    /**
     * 异常 IP 访问检测配置
     */
    private AbnormalIpConfig abnormalIp = new AbnormalIpConfig();

    /**
     * 限流配置（基于用户/客户端 ID）
     */
    private RateLimitConfig rateLimit = new RateLimitConfig();

    /**
     * 暴力破解检测配置
     */
    @Data
    public static class BruteForceConfig {
        /**
         * 5 分钟内同一用户/客户端 ID 最大失败次数（默认：10）
         */
        private int maxFailuresPerUser = 10;

        /**
         * 5 分钟内同一 IP 最大失败次数（默认：20）
         */
        private int maxFailuresPerIp = 20;

        /**
         * 时间窗口（秒，默认：300，即 5 分钟）
         */
        private int timeWindowSeconds = 300;

        /**
         * 封禁时长（秒，默认：900，即 15 分钟）
         */
        private int banDurationSeconds = 900;
    }

    /**
     * 异常 IP 访问检测配置
     */
    @Data
    public static class AbnormalIpConfig {
        /**
         * 10 分钟内最大 IP 变化次数（默认：5）
         */
        private int maxIpChangesPer10Min = 5;

        /**
         * 时间窗口（秒，默认：600，即 10 分钟）
         */
        private int timeWindowSeconds = 600;
    }

    /**
     * 限流配置（基于用户/客户端 ID）
     */
    @Data
    public static class RateLimitConfig {
        /**
         * 是否启用基于用户/客户端 ID 的限流（默认：true）
         */
        private boolean enabled = true;

        /**
         * 默认限流（请求/小时，默认：1000）
         * 如果业务配置中有 rateLimit，则使用业务配置的值
         */
        private int defaultLimit = 1000;

        /**
         * 限流时间窗口（秒，默认：3600，即 1 小时）
         */
        private int timeWindowSeconds = 3600;
    }
}
