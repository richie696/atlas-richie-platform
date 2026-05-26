package com.richie.component.mfa.validation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA 验证结果。
 * <p>
 * 用于封装 MFA 验证相关的所有状态和结果信息，供网关层使用。
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaValidationResult {

    /**
     * 验证是否成功
     */
    private boolean success;

    /**
     * 是否需要 MFA 验证
     * <p>
     * true：用户已启用 MFA，需要进行验证
     * false：用户未启用 MFA 或已通过可信设备跳过
     */
    private boolean mfaRequired;

    /**
     * 是否已绑定 MFA 设备
     */
    private boolean mfaBound;

    /**
     * 是否为可信设备（如果为 true，可跳过 MFA 验证）
     */
    private boolean trustedDevice;

    /**
     * 可信设备是否已过期
     */
    private boolean trustedDeviceExpired;

    /**
     * 账户是否已锁定
     */
    private boolean accountLocked;

    /**
     * 锁定到期时间（如果 accountLocked = true）
     */
    private Long lockedUntil;

    /**
     * 验证失败原因代码
     * <p>
     * 可能的值：
     * <ul>
     *   <li>{@code MFA_CODE_INVALID}：验证码错误</li>
     *   <li>{@code MFA_CODE_USED}：验证码已被使用（重放攻击）</li>
     *   <li>{@code MFA_CODE_EXPIRED}：验证码已过期</li>
     *   <li>{@code ACCOUNT_LOCKED}：账户已锁定</li>
     *   <li>{@code MFA_NOT_BOUND}：用户未绑定 MFA 设备</li>
     *   <li>{@code MFA_NOT_ENABLED}：MFA 未启用</li>
     * </ul>
     */
    private String errorCode;

    /**
     * 验证失败原因描述（用于返回给前端）
     */
    private String errorMessage;

    /**
     * 失败次数（用于前端提示）
     */
    private Integer failureCount;

    /**
     * 最大允许失败次数
     */
    private Integer maxAttempts;

    /**
     * 是否支持可信设备功能
     */
    private boolean trustedDeviceSupported;

    /**
     * 当前用户已注册的可信设备数量
     */
    private Integer trustedDeviceCount;

    /**
     * 最大允许的可信设备数量
     */
    private Integer maxTrustedDevices;

    /**
     * 默认信任天数
     * <p>
     * 用于前端提示用户"信任此设备后，将在 X 天内免验证"
     */
    private Integer defaultTrustDays;

    /**
     * 创建成功结果（无需 MFA）
     * <p>
     * 用于表示用户未绑定 MFA 设备或 MFA 未启用，可以直接登录
     *
     * @return MFA 验证结果
     * <ul>
     *   <li>{@code success = true}</li>
     *   <li>{@code mfaRequired = false}</li>
     *   <li>{@code mfaBound = false}</li>
     * </ul>
     */
    public static MfaValidationResult successWithoutMfa() {
        return MfaValidationResult.builder()
            .success(true)
            .mfaRequired(false)
            .mfaBound(false)
            .build();
    }

    /**
     * 创建成功结果（已通过可信设备）
     * <p>
     * 用于表示用户已通过可信设备验证，可以跳过 MFA 验证直接登录
     *
     * @return MFA 验证结果
     * <ul>
     *   <li>{@code success = true}</li>
     *   <li>{@code mfaRequired = false}</li>
     *   <li>{@code trustedDevice = true}</li>
     * </ul>
     */
    public static MfaValidationResult successWithTrustedDevice() {
        return MfaValidationResult.builder()
            .success(true)
            .mfaRequired(false)
            .trustedDevice(true)
            .build();
    }

    /**
     * 创建成功结果（MFA 验证通过）
     * <p>
     * 用于表示用户已通过 MFA 验证码验证，可以签发正式访问 Token
     *
     * @return MFA 验证结果
     * <ul>
     *   <li>{@code success = true}</li>
     *   <li>{@code mfaRequired = true}</li>
     *   <li>{@code mfaBound = true}</li>
     * </ul>
     */
    public static MfaValidationResult successWithMfa() {
        return MfaValidationResult.builder()
            .success(true)
            .mfaRequired(true)
            .mfaBound(true)
            .build();
    }

    /**
     * 创建失败结果
     * <p>
     * 用于表示 MFA 验证失败（验证码错误、账户锁定等）
     *
     * @param errorCode    错误代码（例如 "MFA_CODE_INVALID"、"ACCOUNT_LOCKED" 等）
     * @param errorMessage 错误描述（用于返回给前端）
     * @return MFA 验证结果
     * <ul>
     *   <li>{@code success = false}</li>
     *   <li>{@code errorCode = errorCode}</li>
     *   <li>{@code errorMessage = errorMessage}</li>
     * </ul>
     */
    public static MfaValidationResult failure(String errorCode, String errorMessage) {
        return MfaValidationResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .build();
    }

    /**
     * 创建需要 MFA 挑战的结果
     * <p>
     * 用于表示用户需要输入 MFA 验证码，前端应弹出验证码输入界面
     *
     * @param trustedDeviceSupported 是否支持可信设备功能（用于前端判断是否显示"信任此设备"选项）
     * @param trustedDeviceCount     当前用户已注册的可信设备数量（用于前端显示）
     * @param maxTrustedDevices      最大允许的可信设备数量（用于前端显示）
     * @param defaultTrustDays       默认信任天数（用于前端提示用户"信任此设备后，将在 X 天内免验证"）
     * @return MFA 验证结果
     * <ul>
     *   <li>{@code success = false}</li>
     *   <li>{@code mfaRequired = true}</li>
     *   <li>{@code mfaBound = true}</li>
     *   <li>{@code trustedDeviceSupported = trustedDeviceSupported}</li>
     *   <li>{@code trustedDeviceCount = trustedDeviceCount}</li>
     *   <li>{@code maxTrustedDevices = maxTrustedDevices}</li>
     *   <li>{@code defaultTrustDays = defaultTrustDays}</li>
     * </ul>
     */
    public static MfaValidationResult mfaRequired(boolean trustedDeviceSupported, Integer trustedDeviceCount, 
                                                   Integer maxTrustedDevices, Integer defaultTrustDays) {
        return MfaValidationResult.builder()
            .success(false)
            .mfaRequired(true)
            .mfaBound(true)
            .trustedDeviceSupported(trustedDeviceSupported)
            .trustedDeviceCount(trustedDeviceCount)
            .maxTrustedDevices(maxTrustedDevices)
            .defaultTrustDays(defaultTrustDays)
            .build();
    }
}
