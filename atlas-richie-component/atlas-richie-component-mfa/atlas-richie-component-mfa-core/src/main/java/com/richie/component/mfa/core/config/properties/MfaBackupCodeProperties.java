package com.richie.component.mfa.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 备份码配置属性
 * <p>
 * 配置前缀：platform.component.mfa.management.security.backup-code.*
 * <p>
 * 控制备份码的生成和存储策略
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security.backup-code")
public class MfaBackupCodeProperties {

    /**
     * 备份码数量
     * <p>
     * 每个用户生成的备份码数量
     * <p>
     * 默认值：10
     */
    private int count = 10;

    /**
     * 备份码长度
     * <p>
     * 每个备份码的位数（数字）
     * <p>
     * 默认值：8
     */
    private int length = 8;

    /**
     * 哈希算法
     * <p>
     * 用于哈希备份码的算法，用于安全存储
     * <p>
     * 可选值：BCrypt、SHA256 等
     * <p>
     * 默认值：BCrypt
     */
    private String hashAlgorithm = "BCrypt";
}

