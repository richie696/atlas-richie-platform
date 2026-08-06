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
package cn.richie696.component.ocr.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link OcrVendorConverter} 单元测试 — Spring Boot 配置绑定时把小写 yaml 值
 * 转换为 {@link OcrVendor} 枚举，委托 {@link OcrVendor#fromKey}。
 */
class OcrVendorConverterTest {

    private final OcrVendorConverter converter = new OcrVendorConverter();

    @Test
    void convert_lowercaseYamlValue_mapsToEnum() {
        assertEquals(OcrVendor.ALIYUN, converter.convert("aliyun"));
        assertEquals(OcrVendor.PADDLE_VL, converter.convert("paddle-vl"));
    }

    @Test
    void convert_mixedCaseAndWhitespace_trimmedCaseInsensitive() {
        assertEquals(OcrVendor.BAIDU, converter.convert("  Baidu  "));
    }

    @Test
    void convert_null_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert(null));
    }

    @Test
    void convert_unknownValue_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("openai"));
    }
}
