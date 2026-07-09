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
package com.richie.component.mfa.core.crypto.provider;

import com.richie.component.mfa.core.config.properties.MfaCloudKmsProperties;
import com.richie.component.mfa.core.config.properties.MfaKeyManagementProperties;
import com.richie.component.mfa.core.constant.CloudKmsProviderEnum;
import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import com.richie.component.mfa.core.crypto.kms.CloudKmsEngine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 云KMS提供方
 * <p>
 * 支持AWS KMS、阿里云KMS、腾讯云KMS、火山引擎KMS、华为云KMS等云服务商的密钥管理服务
 * <p>
 * 根据配置自动注入对应的云服务商引擎Bean（通过 @ConditionalOnProperty 自动选择）
 * <p>
 * 此实现位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "platform.component.mfa.security.key-management",
    name = "provider",
    havingValue = "kms"
)
public class CloudKmsProvider implements KeyManagementProvider {

    /**
     * 密钥管理配置属性（用于获取云KMS配置）
     */
    private final MfaKeyManagementProperties keyManagementProperties;

    /**
     * 云KMS引擎（根据配置自动注入对应的实现：AWS、阿里云、腾讯云等）
     */
    private final CloudKmsEngine cloudKmsEngine;

    /**
     * 初始化：验证配置
     * <p>
     * 检查云 KMS 配置是否完整（服务商、区域、密钥ID等）
     *
     * @throws IllegalStateException 如果配置不完整
     */
    @PostConstruct
    public void init() {
        MfaCloudKmsProperties cloudKmsConfig = keyManagementProperties.getKms();

        if (cloudKmsConfig == null) {
            throw new IllegalStateException("云KMS配置未找到，请检查配置项 platform.component.mfa.security.key-management.kms");
        }

        CloudKmsProviderEnum provider = cloudKmsConfig.getProvider();
        if (provider == null) {
            throw new IllegalStateException("云KMS服务商未配置，请设置 platform.component.mfa.security.key-management.kms.provider");
        }

        String keyId = cloudKmsConfig.getKeyId();
        if (keyId == null || keyId.trim().isEmpty()) {
            throw new IllegalStateException("云KMS密钥ID未配置，请设置 platform.component.mfa.security.key-management.kms.key-id");
        }

        log.info("云KMS提供方初始化成功，服务商: {}, 区域: {}, 密钥ID: {}",
            provider.getDesc(), cloudKmsConfig.getRegion(), keyId);
    }

    /**
     * 加密数据
     * <p>
     * 委托给具体的云 KMS 引擎（CloudKmsEngine）进行加密
     *
     * @param plaintext 明文数据（必填）
     * @return 加密后的密文（服务商特定格式）
     * @throws RuntimeException 如果加密失败
     */
    @Override
    public String encrypt(String plaintext) {
        try {
            return cloudKmsEngine.encrypt(plaintext);
        } catch (Exception e) {
            CloudKmsProviderEnum provider = keyManagementProperties.getKms().getProvider();
            log.error("云KMS加密失败，服务商: {}", provider != null ? provider.getDesc() : "未知", e);
            throw new RuntimeException("云KMS加密失败: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 解密数据
     * <p>
     * 委托给具体的云 KMS 引擎（CloudKmsEngine）进行解密
     *
     * @param ciphertext 密文（服务商特定格式，必填）
     * @return 解密后的明文
     * @throws RuntimeException 如果解密失败
     */
    @Override
    public String decrypt(String ciphertext) {
        try {
            return cloudKmsEngine.decrypt(ciphertext);
        } catch (Exception e) {
            CloudKmsProviderEnum provider = keyManagementProperties.getKms().getProvider();
            log.error("云KMS解密失败，服务商: {}", provider != null ? provider.getDesc() : "未知", e);
            throw new RuntimeException("云KMS解密失败: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 检查KMS服务是否可用
     * <p>
     * 委托给具体的云 KMS 引擎（CloudKmsEngine）检查服务可用性
     *
     * @return KMS服务是否可用
     * <ul>
     *   <li>{@code true}：服务可用</li>
     *   <li>{@code false}：服务不可用</li>
     * </ul>
     */
    @Override
    public boolean isAvailable() {
        return cloudKmsEngine.isAvailable();
    }
}
