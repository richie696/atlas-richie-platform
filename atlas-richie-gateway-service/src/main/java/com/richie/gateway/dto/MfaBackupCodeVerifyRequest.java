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
package com.richie.gateway.dto;

import lombok.Data;

/**
 * 备份码验证请求 DTO
 * <p>
 * 字段设计与 {@code com.richie.component.mfa.management.dto.MfaBackupCodeVerifyRequest}
 * 保持同名同结构，便于通过 JSON 直接映射。
 *
 * @author richie-platform
 * @since 1.0.0
 */
@Data
public class MfaBackupCodeVerifyRequest {

    private String userId;
    private String tenantId;
    private String code;
}
