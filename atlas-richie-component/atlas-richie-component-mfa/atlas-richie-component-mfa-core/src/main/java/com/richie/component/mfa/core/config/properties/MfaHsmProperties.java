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

import com.richie.component.mfa.core.constant.HsmProviderEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * HSM（硬件安全模块）配置属性。
 * <p>
 * 配置前缀：{@code platform.component.mfa.security.key-management.hsm}
 * <p>
 * 当前仅提供 provider 类型枚举，用于区分不同 HSM 厂商/接入方式。
 * <p>
 * 此配置类位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "platform.component.mfa.security.key-management.hsm")
public class MfaHsmProperties {

    /**
     * HSM 设备类型
     * <p>
     * 支持的 HSM 提供者：
     * <ul>
     *   <li>DEFAULT：默认实现（占位实现）</li>
     *   <li>THALES：Thales HSM</li>
     *   <li>SAFENET：SafeNet HSM</li>
     *   <li>其他：根据实际 HSM 设备选择</li>
     * </ul>
     * <p>
     * 默认值：DEFAULT
     */
    private HsmProviderEnum provider = HsmProviderEnum.DEFAULT;

    /**
     * 密钥标签
     * <p>
     * HSM 设备中的密钥标识，用于查找和访问密钥
     */
    private String keyLabel;

    /**
     * 密钥长度
     * <p>
     * HSM 设备中的密钥长度（位），通常为 256 位（AES-256）
     */
    private int keyLength;

    /**
     * 密钥存储位置
     * <p>
     * HSM 设备中的密钥存储位置标识
     */
    private String keyLocation;

    /**
     * 密钥存储路径
     * <p>
     * HSM 设备中的密钥存储路径
     */
    private String keyPath;

}
