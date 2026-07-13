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
package com.richie.component.ocr.model;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OcrImageTest {

    @Test
    void bytes_equalsByContent() {
        OcrImage a = new OcrImage.Bytes(new byte[]{1, 2, 3}, MimeType.PNG);
        OcrImage b = new OcrImage.Bytes(new byte[]{1, 2, 3}, MimeType.PNG);
        OcrImage c = new OcrImage.Bytes(new byte[]{4, 5, 6}, MimeType.PNG);
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void url_equalsByUrlAndAuth() {
        OcrImage a = new OcrImage.Url("https://x", HttpAuth.bearer("t1"));
        OcrImage b = new OcrImage.Url("https://x", HttpAuth.bearer("t1"));
        OcrImage c = new OcrImage.Url("https://x", HttpAuth.bearer("t2"));
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void stream_equalsByIdentityOnly() {
        // Stream 是有状态 InputStream, equals 必须走 identity (不能 value-equality)
        ByteArrayInputStream in1 = new ByteArrayInputStream(new byte[]{1, 2});
        ByteArrayInputStream in2 = new ByteArrayInputStream(new byte[]{1, 2});
        OcrImage s1 = new OcrImage.Stream(in1, MimeType.PNG);
        OcrImage s2 = new OcrImage.Stream(in2, MimeType.PNG);
        OcrImage s3 = s1;
        assertEquals(s1, s3);
        assertNotEquals(s1, s2);
        // hashCode 也必须遵守 identity 契约
        assertEquals(s1.hashCode(), s3.hashCode());
    }

    @Test
    void sealed_exhaustivePatternMatching() {
        OcrImage img = new OcrImage.Bytes(new byte[]{1}, MimeType.JPEG);
        String kind = switch (img) {
            case OcrImage.Bytes b -> "bytes:" + b.data().length;
            case OcrImage.Url u -> "url:" + u.url();
            case OcrImage.Stream s -> "stream";
        };
        assertEquals("bytes:1", kind);
    }

    @Test
    void bytes_nullData_throws() {
        assertThrows(NullPointerException.class,
                () -> new OcrImage.Bytes(null, MimeType.PNG));
    }
}