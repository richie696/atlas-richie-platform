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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可信设备列表响应DTO
 * <p>
 * 用于返回用户的可信设备列表
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
public class TrustedDeviceListResponse {

    /**
     * 可信设备列表
     * <p>
     * 包含设备ID、设备名称、信任过期时间、最后使用时间等信息
     */
    private List<TrustedDeviceVO> devices;

    /**
     * 设备总数
     * <p>
     * 当前用户已注册的可信设备总数（包括已过期的设备）
     */
    private Integer total;

    /**
     * 最大设备数
     * <p>
     * 单个用户最多可以注册的可信设备数量
     */
    private Integer maxDevices;

    /**
     * 当前请求设备是否为主管理设备
     * <p>
     * 仅主设备可撤销其他设备、设置主设备；非主设备仅可查看
     */
    private Boolean currentDeviceIsPrimary;
}
