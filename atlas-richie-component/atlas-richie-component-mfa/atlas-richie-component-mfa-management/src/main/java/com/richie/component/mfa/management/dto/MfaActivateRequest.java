/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mfa.management.dto;

import lombok.Data;

/**
 * MFA激活请求DTO
 * <p>
 * 用于接收前端提交的 MFA 设备激活请求
 * <p>
 * API路径：POST /api/mfa/activate
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class MfaActivateRequest {

    /**
     * 用户ID（业务系统User表的主键ID）
     * <p>
     * 必填字段
     */
    private String userId;

    /**
     * 租户ID
     * <p>
     * 可选字段，如果系统未启用租户，此字段可以为 null 或空字符串
     */
    private String tenantId;

    /**
     * TOTP验证码
     * <p>
     * 用户从 MFA 应用中获取的6位数字验证码
     * <p>
     * 必填字段
     */
    private String code;

    /**
     * 设备ID（设备指纹，用于设备信任）
     * <p>
     * 可选字段，如果用户选择信任此设备，需要提供此字段
     * <p>
     * 由前端生成（浏览器指纹、Android ID、iOS IDFV等）
     */
    private String deviceId;

    /**
     * 设备名称（用于显示，可选）
     * <p>
     * 可选字段，用于在管理界面显示设备信息
     * <p>
     * 示例："Chrome on Windows"、"iPhone 14 Pro"、"Samsung Galaxy S23"
     */
    private String deviceName;

    /**
     * 设备指纹（原始指纹的哈希，可选，用于审计）
     * <p>
     * 可选字段，用于记录设备指纹的原始特征（哈希后），便于审计和异常检测
     */
    private String deviceFingerprint;

    /**
     * 是否信任此设备
     * <p>
     * 可选字段，如果为 true，会将此设备注册为可信设备，后续登录时可跳过 MFA 验证
     * <p>
     * 注意：只有提供了 deviceId 时，此字段才会生效
     */
    private Boolean trustDevice;
}
