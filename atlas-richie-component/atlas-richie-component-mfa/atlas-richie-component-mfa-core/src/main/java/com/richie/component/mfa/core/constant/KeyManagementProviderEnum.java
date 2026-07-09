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
package com.richie.component.mfa.core.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 密钥管理提供者枚举
 * <p>
 * 用于指定MFA密钥管理使用的后端服务类型
 * <p>
 * 此枚举位于 core 模块，供 management 和 validation 模块共同使用
 *
 * @author richie696
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum KeyManagementProviderEnum {
    /**
     * HashiCorp Vault Transit引擎
     */
    VAULT("vault", "HashiCorp Vault的Transit引擎"),

    /**
     * 云KMS服务（AWS KMS、阿里云KMS等）
     */
    KMS("kms", "云KMS服务（AWS KMS、阿里云KMS等）"),

    /**
     * 硬件安全模块
     */
    HSM("hsm", "硬件安全模块"),

    /**
     * 本地加密（仅用于开发/测试）
     */
    LOCAL("local", "本地加密（仅用于开发/测试）");

    /**
     * 提供者编码值（对应配置文件中的值）
     */
    private final String code;

    /**
     * 提供者描述
     */
    private final String desc;

    /**
     * 根据编码获取枚举
     *
     * @param code 提供者编码（例如 "vault"、"kms"、"hsm"、"local"）
     * @return 枚举对象，如果不存在则返回 null
     */
    public static KeyManagementProviderEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据编码获取枚举（如果不存在则返回默认值LOCAL）
     *
     * @param code 提供者编码（例如 "vault"、"kms"、"hsm"、"local"）
     * @return 枚举对象，如果不存在则返回 LOCAL（本地加密，仅用于开发/测试）
     */
    public static KeyManagementProviderEnum fromCodeOrDefault(String code) {
        return Optional.ofNullable(fromCode(code)).orElse(LOCAL);
    }

    /**
     * 根据编码获取枚举（如果不存在则抛出异常）
     *
     * @param code 提供者编码（例如 "vault"、"kms"、"hsm"、"local"）
     * @return 枚举对象
     * @throws IllegalArgumentException 如果编码不存在
     */
    public static KeyManagementProviderEnum fromCodeOrThrow(String code) {
        var enumValue = fromCode(code);
        if (enumValue == null) {
            throw new IllegalArgumentException("未知的密钥管理提供者编码: " + code);
        }
        return enumValue;
    }

    /**
     * 判断编码是否存在
     *
     * @param code 提供者编码（例如 "vault"、"kms"、"hsm"、"local"）
     * @return 编码是否存在
     * <ul>
     *   <li>{@code true}：编码存在</li>
     *   <li>{@code false}：编码不存在</li>
     * </ul>
     */
    public static boolean exists(String code) {
        return fromCode(code) != null;
    }
}
