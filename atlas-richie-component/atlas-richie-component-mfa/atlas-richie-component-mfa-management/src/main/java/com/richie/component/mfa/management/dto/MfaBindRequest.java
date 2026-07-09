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

import lombok.Data;

/**
 * MFA绑定请求DTO
 * <p>
 * 用于接收前端提交的 MFA 设备绑定请求
 * <p>
 * API路径：POST /api/mfa/bind
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class MfaBindRequest {

    /**
     * 用户ID（业务系统User表的主键ID）
     * <p>
     * 注意：只需要传入业务系统User表的主键ID即可，MFA组件不关心User表的其他信息。
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
     * 设备类型
     * <p>
     * 可选值：TOTP、HOTP、SMS、EMAIL 等
     * <p>
     * 必填字段
     */
    private String deviceType;
}
