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
package com.richie.component.storage.config;

import java.util.Objects;

/**
 * 配置参数校验工具
 * <p>
 * 各引擎 {@link com.richie.component.storage.core.StorageEngineProvider} 实现
 * {@code validate(StorageProperties)} 时重复检查"字段非空"。
 * 本工具统一这些样板代码，错误信息保持原格式（含字段名）以兼容现有测试断言。
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * @Override
 * public void validate(StorageProperties properties) {
 *     ObjectConfig c = properties.getObject();
 *     ConfigValidation.requireNonNull(c, "对象存储配置 (object)");
 *     ConfigValidation.requireNonBlank(c.getEndpoint(), "endpoint");
 *     ConfigValidation.requireNonBlank(c.getAccessKeyId(), "accessKeyId");
 *     ConfigValidation.requireNonBlank(c.getAccessKeySecret(), "accessKeySecret");
 *     ConfigValidation.requireNonBlank(c.getBucketName(), "bucketName");
 * }
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2024-01-01
 */
public final class ConfigValidation {

    private ConfigValidation() {
        // 工具类，禁止实例化
    }

    /**
     * 要求对象非 null，否则抛 {@link IllegalArgumentException}
     *
     * @param value 待校验的值
     * @param name  字段名（拼入异常消息）
     */
    public static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    /**
     * 要求字符串非 null 且非空白（{@code null} / {@code ""} / 全部空白字符 均视为空）
     *
     * @param value 待校验的字符串
     * @param name  字段名（拼入异常消息）
     */
    public static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}