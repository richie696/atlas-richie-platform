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
 * 备份码验证请求 DTO
 * <p>
 * 用于接收用户提交的备份码（8 位数字），验证通过后该备份码将被消耗（一次性使用）。
 * <p>
 * API 路径：POST /api/mfa/backup-codes/verify
 *
 * @author richie-platform
 * @since 1.0.0
 */
@Data
public class MfaBackupCodeVerifyRequest {

    /**
     * 用户 ID（业务系统 User 表的主键 ID）
     * <p>
     * 必填字段
     */
    private String userId;

    /**
     * 租户 ID
     * <p>
     * 可选字段，如果系统未启用租户，此字段可以为 null 或空字符串
     */
    private String tenantId;

    /**
     * 用户输入的备份码（8 位数字，与绑定时的备份码一致）
     * <p>
     * 必填字段，验证通过后该码将被消耗
     */
    private String code;
}
