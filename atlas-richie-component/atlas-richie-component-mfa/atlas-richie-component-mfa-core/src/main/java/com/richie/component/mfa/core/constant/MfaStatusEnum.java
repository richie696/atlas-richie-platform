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

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * MFA状态枚举
 * <p>
 * 用于表示MFA设备的激活状态
 * <p>
 * 注意：NOT_BOUND 仅用于响应场景，不会存储到数据库（用户未绑定时没有记录）
 *
 * @author richie696
 * @since 1.0.0
 */
@Getter
@RequiredArgsConstructor
public enum MfaStatusEnum {
    /**
     * 未绑定（仅用于响应，不会存储到数据库）
     */
    NOT_BOUND(-1, "未绑定"),

    /**
     * 未激活
     */
    NOT_ACTIVATED(0, "未激活"),

    /**
     * 已启用
     */
    ENABLED(1, "已启用"),

    /**
     * 已禁用
     */
    DISABLED(2, "已禁用");

    /**
     * 状态编码值（对应数据库字段的值）
     * 使用 @EnumValue 注解标记，MyBatis-Plus 会将此值存储到数据库
     */
    @EnumValue
    private final int code;

    /**
     * 状态描述（用于管理后台显示、日志等）
     */
    private final String desc;

    /**
     * 根据编码获取枚举
     *
     * @param code 状态编码
     * @return 枚举对象，如果不存在则返回null
     */
    public static MfaStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(e -> e.code == code)
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据编码获取枚举（如果不存在则返回默认值NOT_ACTIVATED）
     *
     * @param code 状态编码
     * @return 枚举对象，如果不存在则返回NOT_ACTIVATED
     */
    public static MfaStatusEnum fromCodeOrDefault(Integer code) {
        return Optional.ofNullable(fromCode(code)).orElse(NOT_ACTIVATED);
    }

    /**
     * 根据编码获取枚举（如果不存在则抛出异常）
     *
     * @param code 状态编码
     * @return 枚举对象
     * @throws IllegalArgumentException 如果编码不存在
     */
    public static MfaStatusEnum fromCodeOrThrow(Integer code) {
        var enumValue = fromCode(code);
        if (enumValue == null) {
            throw new IllegalArgumentException("未知的MFA状态编码: " + code);
        }
        return enumValue;
    }

    /**
     * 判断编码是否存在
     *
     * @param code 状态编码
     * @return true-存在，false-不存在
     */
    public static boolean exists(Integer code) {
        return fromCode(code) != null;
    }
}
