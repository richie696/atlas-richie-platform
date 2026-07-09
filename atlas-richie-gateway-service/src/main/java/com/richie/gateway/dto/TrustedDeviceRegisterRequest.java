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
 * 注册可信设备请求 DTO
 * <p>
 * 供网关在用户完成 MFA 验证后，调用后端 MFA 管理服务注册当前设备为可信设备使用。
 * <p>
 * 字段设计与 {@code com.richie.component.mfa.management.dto.TrustedDeviceRegisterRequest}
 * 保持同名同结构，便于通过 JSON 直接映射，无需在网关侧依赖 management 模块。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class TrustedDeviceRegisterRequest {

    /**
     * 租户ID
     * <p>
     * 多租户系统中用于区分不同租户。
     * 未启用租户功能时，可为 {@code null} 或空字符串。
     */
    private String tenantId;

    /**
     * 用户ID
     * <p>
     * 业务系统 User 表的主键 ID，用于唯一标识用户。
     */
    private String userId;

    /**
     * 设备ID
     * <p>
     * 设备的唯一标识（通常为设备指纹、设备 UUID 等），用于标识可信设备。
     */
    private String deviceId;

    /**
     * 设备名称
     * <p>
     * 用于在前端展示给用户的设备名称，例如：
     * <ul>
     *     <li>「Chrome on MacBook Pro」</li>
     *     <li>「iPhone 15 Pro」</li>
     * </ul>
     */
    private String deviceName;

    /**
     * 设备指纹
     * <p>
     * 原始设备指纹或其哈希值，用于安全审计或辅助识别设备（可选）。
     */
    private String deviceFingerprint;
}

