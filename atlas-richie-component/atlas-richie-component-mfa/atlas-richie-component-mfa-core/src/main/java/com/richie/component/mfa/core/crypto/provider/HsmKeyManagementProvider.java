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
package com.richie.component.mfa.core.crypto.provider;

import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import com.richie.component.mfa.core.crypto.hsm.HsmEngine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * HSM（硬件安全模块）KMS 提供方。
 * <p>
 * 通过注入 {@link HsmEngine} 将具体 HSM 实现与上层密钥管理解耦，
 * 当前默认使用 {@code DefaultHsmEngine} 作为占位实现，后续可替换为实际厂商实现。
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
    havingValue = "hsm"
)
public class HsmKeyManagementProvider implements KeyManagementProvider {

    /**
     * HSM引擎（根据配置自动注入对应的实现：DefaultHsmEngine 或自定义实现）
     */
    private final HsmEngine hsmEngine;

    /**
     * 初始化HSM KMS提供方
     * <p>
     * 当前仅作为预留功能的占位实现，真正接入 HSM 时可在此处增加配置校验和连接检查
     */
    @PostConstruct
    public void init() {
        // 当前仅作为预留功能的占位实现，真正接入 HSM 时可在此处增加配置校验和连接检查
        log.info("HSM KMS 提供方初始化完成，当前使用占位 HSM 引擎实现（可通过自定义 HsmEngine Bean 替换）");
    }

    /**
     * 加密数据
     * <p>
     * 委托给注入的 HsmEngine 进行加密
     *
     * @param plaintext 明文数据（必填）
     * @return 加密后的密文
     * @throws RuntimeException 如果加密失败
     */
    @Override
    public String encrypt(String plaintext) {
        return hsmEngine.encrypt(plaintext);
    }

    /**
     * 解密数据
     * <p>
     * 委托给注入的 HsmEngine 进行解密
     *
     * @param ciphertext 密文（必填）
     * @return 解密后的明文
     * @throws RuntimeException 如果解密失败
     */
    @Override
    public String decrypt(String ciphertext) {
        return hsmEngine.decrypt(ciphertext);
    }

    /**
     * 检查KMS服务是否可用
     * <p>
     * 委托给注入的 HsmEngine 检查服务可用性
     *
     * @return KMS服务是否可用
     * <ul>
     *   <li>{@code true}：HSM设备可用</li>
     *   <li>{@code false}：HSM设备不可用</li>
     * </ul>
     */
    @Override
    public boolean isAvailable() {
        return hsmEngine.isAvailable();
    }
}
