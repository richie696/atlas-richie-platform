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
package com.richie.component.mfa.management.dto;

import lombok.Data;

/**
 * 设为主管理设备请求
 * <p>
 * 仅当前主设备可将另一台设备设为主设备；主设备可移除其他可信设备，非主设备仅可查看。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class SetPrimaryTrustedDeviceRequest {

    /**
     * 用户ID（必填）
     */
    private String userId;

    /**
     * 当前请求设备ID（须为主管理设备，必填）
     */
    private String currentDeviceId;

    /**
     * 租户ID（可选，未启用租户时为 null）
     */
    private String tenantId;
}
