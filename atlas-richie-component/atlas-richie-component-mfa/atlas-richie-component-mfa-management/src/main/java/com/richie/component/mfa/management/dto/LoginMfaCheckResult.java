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

import com.richie.component.mfa.management.manager.MfaBindManager;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录时 MFA 校验结果
 * <p>
 * 供业务登录接口（如 sample-mfa）调用 {@link MfaBindManager#checkLoginMfa}
 * 后根据返回值判断是否需要 MFA 验证、是否已绑定 MFA 等。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginMfaCheckResult {

    /**
     * 本次登录是否需要 MFA 验证
     * <p>
     * true：需要 MFA 验证（用户已绑定 MFA 且当前设备非可信）；false：不需要（可信设备或未绑定 MFA）
     */
    private boolean mfaRequired;

    /**
     * 用户是否已绑定 MFA 设备
     * <p>
     * 用于响应中设置 mfaBound、以及判断用户提供 MFA 验证码时是否允许校验。
     */
    private boolean mfaBound;
}
