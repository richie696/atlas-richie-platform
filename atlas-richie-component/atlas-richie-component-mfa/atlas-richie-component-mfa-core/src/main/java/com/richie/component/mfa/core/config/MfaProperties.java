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
package com.richie.component.mfa.core.config;

import com.richie.component.mfa.core.config.properties.MfaManagementProperties;
import com.richie.component.mfa.core.config.properties.MfaSecurityProperties;
import com.richie.component.mfa.core.config.properties.MfaTotpProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MFA统一配置属性
 * <p>
 * 配置前缀：{@code platform.component.mfa}
 * <p>
 * 此配置类统一管理 MFA 管理模块和验证模块的所有配置，避免配置重复和分散。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa")
public class MfaProperties {

    /**
     * 是否启用MFA管理功能
     * <p>
     * 控制是否启用 MFA 管理模块的自动配置
     * <p>
     * 默认值：true
     */
    private boolean enabled = true;

    /**
     * 管理模块配置
     * <p>
     * 用于业务服务（richie-general-service），控制 MFA 管理功能的启用和配置
     */
    private MfaManagementProperties management = new MfaManagementProperties();

    /**
     * TOTP配置
     * <p>
     * 管理模块和验证模块共用，包含时间窗口、窗口大小、验证码长度等配置
     */
    private MfaTotpProperties totp = new MfaTotpProperties();

    /**
     * 安全策略配置
     * <p>
     * 包含密钥管理、备份码、可信设备、防重放攻击、最大尝试次数、账户锁定等配置
     */
    private MfaSecurityProperties security = new MfaSecurityProperties();

}
