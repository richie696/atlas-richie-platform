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
package com.richie.component.mfa.management.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 云KMS服务商枚举
 * <p>
 * 用于指定使用的云KMS服务商类型
 *
 * @author richie696
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum CloudKmsProviderEnum {
    /**
     * AWS KMS
     */
    AWS("aws", "AWS KMS"),

    /**
     * 阿里云KMS
     */
    ALIYUN("aliyun", "阿里云KMS"),

    /**
     * 腾讯云KMS
     */
    TENCENT("tencent", "腾讯云KMS"),

    /**
     * 火山引擎KMS
     */
    VOLCENGINE("volcengine", "火山引擎KMS"),

    /**
     * 华为云KMS
     */
    HUAWEI("huawei", "华为云KMS");

    /**
     * 服务商编码值（对应配置文件中的值）
     */
    private final String code;

    /**
     * 服务商描述
     */
    private final String desc;

    /**
     * 根据编码获取枚举
     *
     * @param code 服务商编码（例如 "aws"、"aliyun"、"tencent"、"volcengine"、"huawei"）
     * @return 枚举对象，如果不存在则返回 null
     */
    public static CloudKmsProviderEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据编码获取枚举（如果不存在则返回默认值AWS）
     *
     * @param code 服务商编码（例如 "aws"、"aliyun"、"tencent"、"volcengine"、"huawei"）
     * @return 枚举对象，如果不存在则返回 AWS
     */
    public static CloudKmsProviderEnum fromCodeOrDefault(String code) {
        return Optional.ofNullable(fromCode(code)).orElse(AWS);
    }

    /**
     * 根据编码获取枚举（如果不存在则抛出异常）
     *
     * @param code 服务商编码（例如 "aws"、"aliyun"、"tencent"、"volcengine"、"huawei"）
     * @return 枚举对象
     * @throws IllegalArgumentException 如果编码不存在
     */
    public static CloudKmsProviderEnum fromCodeOrThrow(String code) {
        var enumValue = fromCode(code);
        if (enumValue == null) {
            throw new IllegalArgumentException("未知的云KMS服务商编码: " + code);
        }
        return enumValue;
    }

    /**
     * 判断编码是否存在
     *
     * @param code 服务商编码（例如 "aws"、"aliyun"、"tencent"、"volcengine"、"huawei"）
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
