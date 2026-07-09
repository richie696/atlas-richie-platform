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
package com.richie.component.mfa.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 密钥轮换配置属性
 * <p>
 * 用于配置主密钥的轮换策略（仅在 provider = VAULT 时生效）
 * <p>
 * 对应安全设计文档中的主密钥轮换策略
 * <p>
 * 此配置类位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security.key-management.vault.rotation")
public class MfaKeyRotationProperties {
    /**
     * 轮换间隔（天）
     * <p>
     * 建议的主密钥轮换周期（天数）
     * <p>
     * 默认值：90 天
     */
    private int rotationIntervalDays = 90;

    /**
     * 宽限期（天）
     * <p>
     * 密钥轮换后的宽限期（天数），配合 Vault 自身的最小解密版本配置使用
     * <p>
     * 在宽限期内，旧版本的密钥仍可用于解密历史数据
     * <p>
     * 默认值：7 天
     */
    private int gracePeriodDays = 7;
}
