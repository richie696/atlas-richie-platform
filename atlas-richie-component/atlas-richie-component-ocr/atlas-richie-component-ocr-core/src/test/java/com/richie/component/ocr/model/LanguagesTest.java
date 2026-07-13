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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguagesTest {

    @Test
    void allRequiredConstantsExist() {
        Languages[] all = Languages.values();
        assertEquals(16, all.length, "AUTO + 8 原有 + 7 新增 = 16");
        assertTrue(containsName(all, "AUTO"));
        assertTrue(containsName(all, "CHINESE_SIMPLIFIED_AND_ENGLISH"));
        assertTrue(containsName(all, "CHINESE_SIMPLIFIED"));
        assertTrue(containsName(all, "CHINESE_TRADITIONAL"));
        assertTrue(containsName(all, "ENGLISH"));
        assertTrue(containsName(all, "JAPANESE"));
        assertTrue(containsName(all, "KOREAN"));
        assertTrue(containsName(all, "DIGITS_ONLY"));
        assertTrue(containsName(all, "LATIN"));
        assertTrue(containsName(all, "ARABIC"));
        assertTrue(containsName(all, "RUSSIAN"));
        assertTrue(containsName(all, "HINDI"));
        assertTrue(containsName(all, "THAI"));
        assertTrue(containsName(all, "VIETNAMESE"));
        assertTrue(containsName(all, "GREEK"));
        assertTrue(containsName(all, "TURKISH"));
    }

    @Test
    void auto_isSentinelWithEmptyTags() {
        assertTrue(Languages.AUTO.isSentinel());
        assertEquals(0, Languages.AUTO.tags().length);
    }

    @Test
    void nonAuto_languages_haveNonEmptyTags() {
        for (Languages l : Languages.values()) {
            if (l == Languages.AUTO) continue;
            assertFalse(l.isSentinel(), l + " should not be a sentinel");
            assertTrue(l.tags().length > 0, l + " should have at least one BCP-47 tag");
        }
    }

    @Test
    void primaryTag_returnsFirstTag() {
        assertEquals("zh-CN", Languages.CHINESE_SIMPLIFIED.primaryTag());
        assertEquals("en", Languages.ENGLISH.primaryTag());
        assertEquals("ar", Languages.ARABIC.primaryTag());
    }

    @Test
    void chineseSimplifiedAndEnglish_hasMultipleTags() {
        String[] tags = Languages.CHINESE_SIMPLIFIED_AND_ENGLISH.tags();
        assertEquals(2, tags.length);
        assertEquals("zh-CN", tags[0]);
        assertEquals("en", tags[1]);
    }

    @Test
    void latin_hasFiveEuropeanTags() {
        assertEquals(5, Languages.LATIN.tags().length);
    }

    @Test
    void fallbackDefault_isChineseSimplifiedAndEnglish() {
        assertEquals(1, Languages.fallbackDefault().size());
        assertTrue(Languages.fallbackDefault().contains(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH));
    }

    private static boolean containsName(Languages[] all, String name) {
        for (Languages l : all) {
            if (l.name().equals(name)) return true;
        }
        return false;
    }
}