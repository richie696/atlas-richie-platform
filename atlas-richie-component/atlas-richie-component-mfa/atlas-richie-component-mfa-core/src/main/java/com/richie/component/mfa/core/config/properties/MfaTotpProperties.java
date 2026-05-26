package com.richie.component.mfa.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TOTP 配置属性。
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.totp")
public class MfaTotpProperties {

    /**
     * 时间窗口（秒）
     * <p>
     * TOTP 验证码的有效时间窗口，通常为 30 秒
     */
    private int timeWindow = 30;

    /**
     * 窗口大小（容错窗口，±N 表示允许前后 N 个时间窗口）
     * <p>
     * 用于处理服务器与设备间的时钟偏差，默认 5 表示允许 ±150 秒（period=30 时）
     */
    private int windowSize = 5;

    /**
     * 验证码长度
     * <p>
     * TOTP 验证码的位数，通常为 6 位
     */
    private int codeLength = 6;

    /**
     * HMAC算法
     * <p>
     * 用于生成 TOTP 验证码的 HMAC 算法
     * <p>
     * 可选值：SHA1、SHA256、SHA512
     * <p>
     * 默认值：SHA1
     * <ul>
     *   <li>Microsoft Authenticator：仅支持SHA1</li>
     *   <li>Google Authenticator：支持SHA1(默认)、SHA256、SHA512 (PS:后两种算法支持度有限，非必要情况不建议修改该参数。)</li>
     *
     */
    private String algorithm = "SHA1";

    /**
     * 验证码位数（与 codeLength 相同，保留用于兼容）
     * <p>
     * TOTP 验证码的位数，通常为 6 位
     * <p>
     * 默认值：6
     */
    private int digits = 6;

    /**
     * 时间窗口周期（秒，与 timeWindow 相同，保留用于兼容）
     * <p>
     * TOTP 验证码的有效时间窗口，通常为 30 秒
     * <p>
     * 默认值：30
     */
    private int period = 30;
}

