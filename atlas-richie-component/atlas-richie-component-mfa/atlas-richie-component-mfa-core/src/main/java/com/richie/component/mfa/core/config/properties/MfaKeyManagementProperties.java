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
package com.richie.component.mfa.core.config.properties;

import com.richie.component.mfa.core.constant.KeyManagementProviderEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 密钥管理配置属性。
 * <p>
 * 配置前缀：{@code platform.component.mfa.security.key-management}
 * <p>
 * 控制使用哪种后端（Vault / 云 KMS / HSM / 本地）以及各自的具体配置。
 * <p>
 * 此配置类位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security.key-management")
public class MfaKeyManagementProperties {

    /**
     * 密钥管理提供者
     * <p>
     * 控制使用哪种后端进行密钥加密/解密：
     * <ul>
     *   <li>VAULT：使用 HashiCorp Vault 的 Transit 引擎（推荐用于生产环境）</li>
     *   <li>KMS：使用云 KMS 服务（AWS KMS、阿里云 KMS、腾讯云 KMS 等）</li>
     *   <li>HSM：使用硬件安全模块（最高安全级别）</li>
     *   <li>LOCAL：本地加密（仅用于开发/测试，不安全）</li>
     * </ul>
     * <p>
     * 默认值：LOCAL（仅用于开发/测试）
     */
    private KeyManagementProviderEnum provider = KeyManagementProviderEnum.LOCAL;

    /**
     * Vault 配置
     * <p>
     * 当 provider = VAULT 时使用，包含 Vault 连接信息、Transit 引擎路径、密钥轮换配置等
     */
    private MfaVaultProperties vault = new MfaVaultProperties();

    /**
     * 云 KMS 配置
     * <p>
     * 当 provider = KMS 时使用，包含云 KMS 服务商选择（AWS、阿里云、腾讯云等）和连接信息
     */
    private MfaCloudKmsProperties kms = new MfaCloudKmsProperties();

    /**
     * HSM 配置
     * <p>
     * 当 provider = HSM 时使用，包含 HSM 提供者选择（Thales、SafeNet 等）和连接信息
     */
    private MfaHsmProperties hsm = new MfaHsmProperties();

    /**
     * 本地加密配置
     * <p>
     * 当 provider = LOCAL 时使用，仅用于开发/测试环境，不安全
     */
    private MfaLocalCryptoProperties local = new MfaLocalCryptoProperties();

}
