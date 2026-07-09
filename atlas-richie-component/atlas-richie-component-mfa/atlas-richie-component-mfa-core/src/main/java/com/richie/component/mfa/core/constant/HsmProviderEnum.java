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
package com.richie.component.mfa.core.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * HSM（硬件安全模块）类型枚举
 * <p>
 * 用于指定接入的 HSM 设备类型。
 * <p>
 * 此枚举位于 core 模块，供 management 和 validation 模块共同使用
 * <p>
 * 设计原则：
 * <ul>
 *   <li>DEFAULT：默认占位实现（不绑定具体厂商），用于开发/测试或未接入真实 HSM 时</li>
 *   <li>真实厂商接入时，在此枚举中新增对应常量（如 THALES、SAFENET 等），并提供对应的 {@code HsmEngine} 实现</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum HsmProviderEnum {

    /**
     * 默认 HSM 类型（占位实现）
     */
    DEFAULT("default", "默认HSM占位实现"),

    /**
     * 示例：Thales HSM（接入真实设备时再补充实现）
     */
    THALES("thales", "Thales HSM（示例，占位）"),

    /**
     * 示例：SafeNet HSM（接入真实设备时再补充实现）
     */
    SAFENET("safenet", "SafeNet HSM（示例，占位）");

    /**
     * HSM 类型编码（对应配置文件中的值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 根据编码获取枚举
     *
     * @param code HSM 类型编码（例如 "default"、"thales"、"safenet"）
     * @return 枚举对象，如果不存在则返回 null
     */
    public static HsmProviderEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据编码获取枚举（如果不存在则返回默认值 DEFAULT）
     *
     * @param code HSM 类型编码（例如 "default"、"thales"、"safenet"）
     * @return 枚举对象，如果不存在则返回 DEFAULT（默认占位实现）
     */
    public static HsmProviderEnum fromCodeOrDefault(String code) {
        return Optional.ofNullable(fromCode(code)).orElse(DEFAULT);
    }

    /**
     * 根据编码获取枚举（如果不存在则抛出异常）
     *
     * @param code HSM 类型编码（例如 "default"、"thales"、"safenet"）
     * @return 枚举对象
     * @throws IllegalArgumentException 如果编码不存在
     */
    public static HsmProviderEnum fromCodeOrThrow(String code) {
        var enumValue = fromCode(code);
        if (enumValue == null) {
            throw new IllegalArgumentException("未知的HSM类型编码: " + code);
        }
        return enumValue;
    }

    /**
     * 判断编码是否存在
     *
     * @param code HSM 类型编码（例如 "default"、"thales"、"safenet"）
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
