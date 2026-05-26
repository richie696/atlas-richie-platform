package com.richie.gateway.dto;

import lombok.Data;

/**
 * MFA 验证请求 DTO
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
public class MfaVerifyRequest {

    /**
     * MFA Token（从登录响应中获取的临时token）
     */
    private String mfaToken;

    /**
     * TOTP 验证码（6位数字）
     */
    private String mfaCode;

    /**
     * 设备ID（可选）
     */
    private String deviceId;

    /**
     * 设备指纹（可选）
     */
    private String deviceFingerprint;

    /**
     * 是否信任此设备（可选）
     */
    private Boolean trustDevice;
}
