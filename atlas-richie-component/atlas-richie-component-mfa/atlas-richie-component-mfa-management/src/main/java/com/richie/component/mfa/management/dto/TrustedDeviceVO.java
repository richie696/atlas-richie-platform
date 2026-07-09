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
package com.richie.component.mfa.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 可信设备视图对象
 * <p>
 * 用于返回可信设备信息给前端
 * <p>
 * API路径：GET /api/mfa/trusted-devices
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedDeviceVO {

    /**
     * 设备ID（设备指纹）
     * <p>
     * 由前端生成（浏览器指纹、Android ID、iOS IDFV等）
     */
    private String deviceId;

    /**
     * 设备名称（用于显示）
     * <p>
     * 示例："Chrome on Windows"、"iPhone 14 Pro"、"Samsung Galaxy S23"
     */
    private String deviceName;

    /**
     * 设备类型（可选）
     * <p>
     * 可选字段，用于标识设备类型（例如 "Browser"、"Mobile"、"Desktop"）
     */
    private String deviceType;

    /**
     * 信任过期时间
     * <p>
     * 超过此时间后，设备信任失效，需要重新进行 MFA 验证
     */
    private OffsetDateTime trustedUntil;

    /**
     * 最后使用时间
     * <p>
     * 设备最后一次用于跳过 MFA 验证的时间
     */
    private OffsetDateTime lastUsedTime;

    /**
     * 创建时间
     * <p>
     * 设备注册为可信设备的时间
     */
    private OffsetDateTime createdAt;

    /**
     * 是否为主管理设备
     * <p>
     * 主设备可移除其他可信设备、设置新的主设备；非主设备仅可查看列表
     */
    private Boolean isPrimary;
}
