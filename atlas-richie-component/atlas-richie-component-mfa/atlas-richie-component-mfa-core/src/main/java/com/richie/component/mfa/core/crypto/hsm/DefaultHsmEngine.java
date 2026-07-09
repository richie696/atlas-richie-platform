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
package com.richie.component.mfa.core.crypto.hsm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认 HSM 引擎占位实现。
 * <p>
 * 仅用于预留扩展能力，当前不会做真正的硬件加解密：
 * <ul>
 *   <li>加密：直接返回原文</li>
 *   <li>解密：直接返回原文</li>
 *   <li>可用性检查：返回 {@code false}</li>
 * </ul>
 * <p>
 * 生产环境接入真实 HSM 时，应提供自定义的 {@link HsmEngine} Bean，
 * 并通过 Spring 条件装配覆盖本实现。
 * <p>
 * 此实现位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "platform.component.mfa.security.key-management",
    name = "provider",
    havingValue = "hsm"
)
@ConditionalOnMissingBean(HsmEngine.class)
public class DefaultHsmEngine implements HsmEngine {

    /**
     * 加密数据（占位实现）
     * <p>
     * 注意：当前为占位实现，直接返回原文，未进行真实加密
     * <p>
     * 生产环境必须提供真实的 HSM 实现，通过自定义 {@link HsmEngine} Bean 覆盖此实现
     *
     * @param plaintext 明文数据（必填）
     * @return 原文（未加密，仅用于占位）
     */
    @Override
    public String encrypt(String plaintext) {
        log.warn("调用默认 HSM 引擎占位实现，未进行真实加密（仅用于预留功能），请在生产环境集成真实 HSM 实现");
        return plaintext;
    }

    /**
     * 解密数据（占位实现）
     * <p>
     * 注意：当前为占位实现，直接返回原文，未进行真实解密
     * <p>
     * 生产环境必须提供真实的 HSM 实现，通过自定义 {@link HsmEngine} Bean 覆盖此实现
     *
     * @param ciphertext 密文（必填）
     * @return 原文（未解密，仅用于占位）
     */
    @Override
    public String decrypt(String ciphertext) {
        log.warn("调用默认 HSM 引擎占位实现，未进行真实解密（仅用于预留功能），请在生产环境集成真实 HSM 实现");
        return ciphertext;
    }

    /**
     * 检查 HSM 服务是否可用（占位实现）
     * <p>
     * 注意：当前为占位实现，始终返回 false
     * <p>
     * 生产环境必须提供真实的 HSM 实现，通过自定义 {@link HsmEngine} Bean 覆盖此实现
     *
     * @return 始终返回 false（占位实现不可用）
     */
    @Override
    public boolean isAvailable() {
        log.warn("默认 HSM 引擎占位实现始终返回不可用，请在生产环境集成真实 HSM 实现");
        return false;
    }
}
