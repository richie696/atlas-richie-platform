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
package com.richie.component.desensitize.core.support;

import com.richie.component.desensitize.core.model.MaskType;

/**
 * 日志参数包装：显式标明敏感类型（可选，P2 Logback 过滤器识别）。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
public record SensitiveLogArg(String value, MaskType type) {

    /**
     * 构建通用敏感日志参数。
     *
     * @param value 原始值
     * @param type 脱敏类型
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg of(String value, MaskType type) {
        return new SensitiveLogArg(value, type);
    }

    /**
     * 构建手机号类型日志参数。
     *
     * @param value 原始手机号
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg phone(String value) {
        return of(value, MaskType.PHONE);
    }

    /**
     * 构建身份证类型日志参数。
     *
     * @param value 原始身份证号
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg idCard(String value) {
        return of(value, MaskType.ID_CARD);
    }

    /**
     * 构建邮箱类型日志参数。
     *
     * @param value 原始邮箱
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg email(String value) {
        return of(value, MaskType.EMAIL);
    }

    /**
     * 构建银行卡类型日志参数。
     *
     * @param value 原始银行卡号
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg bankCard(String value) {
        return of(value, MaskType.BANK_CARD);
    }

    /**
     * 构建姓名类型日志参数。
     *
     * @param value 原始姓名
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg name(String value) {
        return of(value, MaskType.NAME);
    }

    /**
     * 构建地址类型日志参数。
     *
     * @param value 原始地址
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg address(String value) {
        return of(value, MaskType.ADDRESS);
    }

    /**
     * 构建密码类型日志参数。
     *
     * @param value 原始密码
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg password(String value) {
        return of(value, MaskType.PASSWORD);
    }

    /**
     * 构建自定义类型日志参数。
     *
     * @param value 原始值
     * @return 日志参数包装对象
     */
    public static SensitiveLogArg custom(String value) {
        return of(value, MaskType.CUSTOM);
    }

    /**
     * 返回原始值，交由上层过滤器做脱敏处理。
     *
     * @return 原始字符串
     */
    @Override
    public String toString() {
        return value;
    }
}
