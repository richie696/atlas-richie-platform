/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.dto;

import lombok.Data;

/**
 * MFA 验证请求 DTO
 *
 * @author richie696
 * @since 1.0.0
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
