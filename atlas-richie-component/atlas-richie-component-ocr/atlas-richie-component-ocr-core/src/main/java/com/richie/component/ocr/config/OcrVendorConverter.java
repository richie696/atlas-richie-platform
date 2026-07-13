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
package com.richie.component.ocr.config;

import jakarta.annotation.Nonnull;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

/**
 * Spring Boot 配置绑定时把 yaml 字符串转 {@link OcrVendor}。
 *
 * <p>默认的 {@code StringToEnumConverterFactory} 用 {@code Enum.valueOf} (区分大小写),
 * 没法处理 {@code vendor: aliyun} 这种小写配置; 显式注册一个大小写不敏感的
 * converter, yml 配小写、Java 代码用大写枚举名, 桥接。
 *
 * <p>由 {@code @ConfigurationPropertiesBinding} 标记, Spring Boot 在
 * {@code ConversionService} 启动时自动注册。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2026-07-10
 */
@ConfigurationPropertiesBinding
public class OcrVendorConverter implements Converter<String, OcrVendor> {

    /**
     * 把 yaml 中读到的字符串转换为 {@link OcrVendor} 枚举常量。
     *
     * <p>委托给 {@link OcrVendor#fromKey(String)}, 确保在 Spring Boot 配置绑定阶段
     * 与其他调用入口 (例如业务代码直接调用 {@code OcrVendor.fromKey}) 走完全相同的
     * 大小写不敏感解析与 fail-fast 异常行为。
     *
     * @param source 从 yaml 读取的原始字符串 (如 {@code vendor: aliyun})
     * @return 解析得到的 {@link OcrVendor} 常量
     * @throws IllegalArgumentException 当 {@code source} 为 {@code null} 或不匹配任何一个已知 vendor 时抛出
     */
    @Override
    public OcrVendor convert(@Nonnull String source) {
        return OcrVendor.fromKey(source);
    }
}
