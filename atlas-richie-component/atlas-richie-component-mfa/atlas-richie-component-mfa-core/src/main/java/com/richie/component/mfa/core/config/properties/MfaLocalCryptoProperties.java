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
package com.richie.component.mfa.core.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地加密配置属性（仅用于开发/测试环境）。
 * <p>
 * 用于本地模拟加解密算法，不代表真实 KMS 服务。
 * <p>
 * 此配置类位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security.key-management.local")
public class MfaLocalCryptoProperties {

    /**
     * 本地加密密钥
     * <p>
     * Base64 编码的加密密钥，32 字节（256 位），用于 AES-256 加密
     * <p>
     * 如果未配置，将使用默认密钥（仅用于开发/测试，不安全）
     * <p>
     * 注意：生产环境必须配置此字段，且应使用环境变量或密钥管理服务存储
     */
    private String secretKey;

    /**
     * 加密算法
     * <p>
     * 用于本地加密的算法
     * <p>
     * 默认值：AES/GCM/NoPadding
     */
    private String algorithm = "AES/GCM/NoPadding";

    /**
     * GCM IV 长度
     * <p>
     * AES-GCM 模式的初始化向量（IV）长度（字节）
     * <p>
     * 默认值：12 字节
     */
    private int gcmIvLength = 12;

    /**
     * GCM Tag 长度
     * <p>
     * AES-GCM 模式的认证标签（Tag）长度（字节）
     * <p>
     * 默认值：16 字节
     */
    private int gcmTagLength = 16;
}
