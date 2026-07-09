/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
