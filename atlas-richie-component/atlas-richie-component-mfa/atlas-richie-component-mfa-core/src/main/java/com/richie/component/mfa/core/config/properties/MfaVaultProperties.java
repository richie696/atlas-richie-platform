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
 * MFA 组件使用的 Vault 业务配置属性（仅引擎路径与密钥名等，不包含连接信息）
 * <p>
 * 配置前缀：{@code platform.component.mfa.security.key-management.vault}
 * <p>
 * Vault 连接（uri、token、ssl）由 Spring Cloud Vault 统一管理，请配置 {@code spring.cloud.vault.*}。
 * 本类仅配置在 Vault 内的业务路径与密钥名称。
 * <p>
 * 此配置类位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security.key-management.vault")
public class MfaVaultProperties {

    /**
     * Transit 引擎路径
     * <p>
     * Vault Transit 引擎的挂载路径
     * <p>
     * 默认值：transit
     */
    private String transitPath = "transit";

    /**
     * 加密密钥名称
     * <p>
     * 在 Vault Transit 引擎中创建的加密密钥名称
     * <p>
     * 默认值：mfa-secret-key
     */
    private String keyName = "mfa-secret-key";

    /**
     * KV 引擎路径（用于存储用户密钥）
     * <p>
     * Vault KV 引擎的挂载路径，用于存储 MFA 用户密钥
     * <p>
     * 默认值：kv
     * <p>
     * 密钥存储路径格式：
     * <ul>
     *   <li>有租户：{@code {kvPath}/mfa/{tenantId}/{userId}}</li>
     *   <li>无租户：{@code {kvPath}/mfa/{userId}}</li>
     * </ul>
     */
    private String kvPath = "kv";

    /**
     * 密钥轮换配置（仅在 provider = vault 时生效）。
     * <p>
     * 对应安全设计文档中的主密钥轮换策略：
     * <ul>
     *   <li>{@code enabled}：是否启用轮换能力</li>
     *   <li>{@code rotationIntervalDays}：建议的轮换周期（天）</li>
     *   <li>{@code gracePeriodDays}：宽限期（天），配合 Vault 自身的最小解密版本配置使用</li>
     * </ul>
     */
    private MfaKeyRotationProperties rotation = new MfaKeyRotationProperties();
}
