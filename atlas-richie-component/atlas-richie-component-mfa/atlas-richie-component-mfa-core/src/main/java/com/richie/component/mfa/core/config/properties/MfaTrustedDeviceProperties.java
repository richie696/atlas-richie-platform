package com.richie.component.mfa.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可信设备配置属性
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security.trusted-device")
public class MfaTrustedDeviceProperties {

    /**
     * 是否启用/支持可信设备功能
     * <p>
     * 用于控制是否启用可信设备功能：
     * <ul>
     *   <li>management 模块：控制是否允许注册可信设备</li>
     *   <li>validation 模块：控制是否支持可信设备验证（用于前端判断是否显示"信任此设备"选项）</li>
     * </ul>
     */
    private boolean enabled = true;

    /**
     * 最大允许的可信设备数量
     * <p>
     * 单个用户最多可以注册的可信设备数量，超过此数量后需要先删除旧设备才能添加新设备
     */
    private int maxDevices = 10;

    /**
     * 默认信任天数
     * <p>
     * 新注册的可信设备的默认信任有效期（天数）。
     * <p>
     * 使用场景：
     * <ul>
     *   <li>management 模块：用于计算 {@code trustedUntil} 时间：{@code LocalDateTime.now().plusDays(defaultTrustDays)}</li>
     *   <li>validation 模块：用于前端提示用户"信任此设备后，将在 X 天内免验证"</li>
     * </ul>
     */
    private int defaultTrustDays = 30;

}

