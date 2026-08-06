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

import static org.junit.jupiter.api.Assertions.*;

class OcrVendorTest {

    @Test
    void key_returnsLowercaseKey() {
        assertEquals("aliyun", OcrVendor.ALIYUN.key());
        assertEquals("baidu", OcrVendor.BAIDU.key());
        assertEquals("paddle", OcrVendor.PADDLE.key());
        assertEquals("tesseract", OcrVendor.TESSERACT.key());
        assertEquals("paddle-vl", OcrVendor.PADDLE_VL.key());
        assertEquals("mineru", OcrVendor.MINERU.key());
        assertEquals("tencent", OcrVendor.TENCENT.key());
        assertEquals("volcano", OcrVendor.VOLCANO.key());
    }

    @Test
    void fromKey_exactLowercaseMatch() {
        assertEquals(OcrVendor.ALIYUN, OcrVendor.fromKey("aliyun"));
        assertEquals(OcrVendor.PADDLE_VL, OcrVendor.fromKey("paddle-vl"));
        assertEquals(OcrVendor.VOLCANO, OcrVendor.fromKey("volcano"));
    }

    @Test
    void fromKey_trimAndCaseInsensitive() {
        assertEquals(OcrVendor.BAIDU, OcrVendor.fromKey("  Baidu  "));
        assertEquals(OcrVendor.TENCENT, OcrVendor.fromKey("TENCENT"));
        assertEquals(OcrVendor.MINERU, OcrVendor.fromKey(" Mineru "));
    }

    @Test
    void fromKey_nullThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> OcrVendor.fromKey(null));
        assertEquals("OCR vendor key is null", ex.getMessage());
    }

    @Test
    void fromKey_unknownThrowsWithAvailableList() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> OcrVendor.fromKey("openai"));
        assertTrue(ex.getMessage().contains("Unknown OCR vendor: 'openai'"));
        assertTrue(ex.getMessage().contains("aliyun, baidu, paddle, tesseract, paddle-vl, mineru, tencent, volcano"));
    }

    @Test
    void values_coversAllEightVendors() {
        assertEquals(8, OcrVendor.values().length);
    }
}
