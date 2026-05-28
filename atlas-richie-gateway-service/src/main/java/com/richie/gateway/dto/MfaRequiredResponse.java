package com.richie.gateway.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * MFA 挑战响应 DTO
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@Builder
public class MfaRequiredResponse {

    /**
     * 响应码
     */
    private String code;

    /**
     * 响应消息
     */
    private String msg;

    /**
     * 响应数据
     */
    private MfaRequiredData data;

    @Data
    @Builder
    public static class MfaRequiredData {
        /**
         * MFA Token（临时token，用于后续验证）
         */
        private String mfaToken;

        /**
         * MFA 方法列表（如 ["TOTP"]）
         */
        private List<String> mfaMethods;

        /**
         * 是否支持可信设备功能
         */
        private Boolean trustedDeviceSupported;

        /**
         * 当前用户已注册的可信设备数量
         */
        private Integer trustedDeviceCount;

        /**
         * 最大允许的可信设备数量
         */
        private Integer maxTrustedDevices;

        /**
         * 默认信任天数（用于前端提示）
         */
        private Integer defaultTrustDays;
    }
}
