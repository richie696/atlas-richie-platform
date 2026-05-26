package com.richie.component.mfa.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全策略配置属性
 * <p>
 * 配置前缀：platform.component.mfa.management.security.*
 * <p>
 * 包含密钥管理、备份码、可信设备等安全相关配置
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security")
public class MfaSecurityProperties {

    /**
     * 密钥管理配置
     * <p>
     * 控制使用哪种后端（Vault / 云 KMS / HSM / 本地）以及各自的具体配置
     */
    private MfaKeyManagementProperties keyManagement = new MfaKeyManagementProperties();

    /**
     * 备份码配置
     * <p>
     * 控制备份码的生成数量、长度、哈希算法等
     */
    private MfaBackupCodeProperties backupCode = new MfaBackupCodeProperties();

    /**
     * 可信设备配置
     * <p>
     * 控制是否启用可信设备、最大设备数量、默认信任天数等
     */
    private MfaTrustedDeviceProperties trustedDevice = new MfaTrustedDeviceProperties();

    /**
     * 防重放攻击TTL倍数（TTL = 时间窗口 * replayPreventionTtlMultiplier）
     * <p>
     * 用于计算防重放攻击缓存的过期时间，确保验证码在时间窗口过期后仍能被正确识别为已使用
     * <p>
     * 默认值：3
     */
    private int replayPreventionTtlMultiplier = 3;

    /**
     * 最大尝试次数
     * <p>
     * 用户验证码验证失败的最大尝试次数，超过此次数后账户将被锁定
     * <p>
     * 默认值：5
     */
    private int maxAttempts = 5;

    /**
     * 账户锁定时长（秒）
     * <p>
     * 当用户验证失败次数达到 {@code maxAttempts} 时，账户将被锁定此时长
     * <p>
     * 默认值：300（5分钟）
     */
    private int lockDurationSeconds = 300;
}

