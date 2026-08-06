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

import cn.richie696.component.ocr.model.Languages;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OcrPropertiesTest {

    @Test
    void prefix_constantMatchesBindingPrefix() {
        assertEquals("platform.component.ocr", OcrProperties.PREFIX);
    }

    @Test
    void defaults_enabledVendorAndLanguage() {
        OcrProperties props = new OcrProperties();
        assertTrue(props.isEnabled());
        assertNull(props.getVendor());
        assertEquals(Set.of(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH), props.getDefaultLanguages());
    }

    @Test
    void setDefaultLanguages_nullFallsBackToDefault() {
        OcrProperties props = new OcrProperties();
        props.setDefaultLanguages(null);
        assertEquals(Set.of(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH), props.getDefaultLanguages());
    }

    @Test
    void setDefaultLanguages_emptyFallsBackToDefault() {
        OcrProperties props = new OcrProperties();
        props.setDefaultLanguages(List.of());
        assertEquals(Set.of(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH), props.getDefaultLanguages());
    }

    @Test
    void setDefaultLanguages_validEntriesResolved() {
        OcrProperties props = new OcrProperties();
        props.setDefaultLanguages(List.of("japanese", "ENGLISH", "korean"));
        assertEquals(Set.of(Languages.JAPANESE, Languages.ENGLISH, Languages.KOREAN), props.getDefaultLanguages());
    }

    @Test
    void setDefaultLanguages_blankAndNullEntriesSkipped() {
        OcrProperties props = new OcrProperties();
        props.setDefaultLanguages(java.util.Arrays.asList("", "   ", null, "arabic"));
        assertEquals(Set.of(Languages.ARABIC), props.getDefaultLanguages());
    }

    @Test
    void setDefaultLanguages_allInvalidFallsBackToDefault() {
        OcrProperties props = new OcrProperties();
        props.setDefaultLanguages(List.of("not-a-language", "!!!" , ""));
        assertEquals(Set.of(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH), props.getDefaultLanguages());
    }

    @Test
    void setVendor_roundTrip() {
        OcrProperties props = new OcrProperties();
        props.setVendor(OcrVendor.ALIYUN);
        assertEquals(OcrVendor.ALIYUN, props.getVendor());
    }

    @Test
    void setEnabled_roundTrip() {
        OcrProperties props = new OcrProperties();
        props.setEnabled(false);
        assertFalse(props.isEnabled());
    }
}
