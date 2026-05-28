package com.richie.gateway.dto;

import lombok.Builder;
import lombok.Data;

/**
 * MFA 验证响应 DTO
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@Builder
public class MfaVerifyResponse {

    /**
     * 正式访问 Token
     */
    private String accessToken;

    /**
     * 是否已注册可信设备
     */
    private Boolean trustedDeviceRegistered;
}
