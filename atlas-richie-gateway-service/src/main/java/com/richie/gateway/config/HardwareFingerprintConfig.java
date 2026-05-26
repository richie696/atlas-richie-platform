package com.richie.gateway.config;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 硬件指纹配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-30
 */
@Data
@Accessors(chain = true)
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "platform.gateway.hardware-fingerprint")
public class HardwareFingerprintConfig {

    /**
     * HMAC-SHA256签名密钥（用于验证硬件指纹的完整性）
     * <p>
     * 生产环境必须修改为强密钥（建议32字符以上）
     * <p>
     * 可以通过环境变量 {@code HARDWARE_FINGERPRINT_HMAC_SECRET} 配置
     */
    private String hmacSecret = "default-secret-key-change-in-production";

    /**
     * 时间戳有效期（秒），超过此时间认为请求过期
     * <p>
     * 默认300秒（5分钟），用于防止重放攻击
     */
    private long timestampValidDuration = 300L;

}
