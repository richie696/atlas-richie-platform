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
package cn.richie696.component.ocr.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OcrOptionsTest {

    @Test
    void builder_defaultsMatchSpec() {
        OcrOptions opts = OcrOptions.builder().build();
        assertEquals(300, opts.dpi());
        assertEquals(0.6f, opts.confidenceThreshold());
        assertEquals(true, opts.detectOrientation());
        assertEquals(false, opts.tableRecognition());
        assertEquals(false, opts.handwriting());
        assertEquals(true, opts.outputBoundingBoxes());
        Set<Languages> langs = opts.languages();
        assertEquals(1, langs.size());
        assertTrue(langs.contains(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH));
    }

    @Test
    void languages_varargs_singleLanguage() {
        OcrOptions opts = OcrOptions.builder().languages(Languages.JAPANESE).build();
        assertEquals(1, opts.languages().size());
        assertTrue(opts.languages().contains(Languages.JAPANESE));
        assertEquals(Languages.JAPANESE, opts.firstLanguage());
    }

    @Test
    void languages_varargs_multiLanguage() {
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.JAPANESE, Languages.ENGLISH, Languages.KOREAN)
                .build();
        assertEquals(3, opts.languages().size());
        assertTrue(opts.languages().contains(Languages.JAPANESE));
        assertTrue(opts.languages().contains(Languages.ENGLISH));
        assertTrue(opts.languages().contains(Languages.KOREAN));
    }

    @Test
    void languages_set_overload() {
        OcrOptions opts = OcrOptions.builder()
                .languages(Set.of(Languages.ARABIC, Languages.RUSSIAN))
                .build();
        assertEquals(2, opts.languages().size());
        assertTrue(opts.languages().contains(Languages.ARABIC));
        assertTrue(opts.languages().contains(Languages.RUSSIAN));
    }

    @Test
    void languages_emptyVarargs_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> OcrOptions.builder().languages());
    }

    @Test
    void languages_emptySet_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> OcrOptions.builder().languages(Set.of()));
    }

    @Test
    void addLanguage_incremental() {
        OcrOptions opts = OcrOptions.builder()
                .addLanguage(Languages.JAPANESE)
                .addLanguage(Languages.KOREAN)
                .build();
        assertEquals(3, opts.languages().size(), "default + 2 added");
        assertTrue(opts.languages().contains(Languages.CHINESE_SIMPLIFIED_AND_ENGLISH));
        assertTrue(opts.languages().contains(Languages.JAPANESE));
        assertTrue(opts.languages().contains(Languages.KOREAN));
    }

    @Test
    void languages_auto_isAllowed_butEngineResolvesLater() {
        OcrOptions opts = OcrOptions.builder().languages(Languages.AUTO).build();
        assertEquals(1, opts.languages().size());
        assertTrue(opts.languages().contains(Languages.AUTO));
    }

    @Test
    void dpi_atLowerBound_ok() {
        OcrOptions.builder().dpi(72).build();
    }

    @Test
    void dpi_atUpperBound_ok() {
        OcrOptions.builder().dpi(1200).build();
    }

    @Test
    void dpi_belowLowerBound_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> OcrOptions.builder().dpi(71).build());
    }

    @Test
    void dpi_aboveUpperBound_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> OcrOptions.builder().dpi(1201).build());
    }

    @Test
    void confidence_inRange_ok() {
        OcrOptions.builder().confidenceThreshold(0f).build();
        OcrOptions.builder().confidenceThreshold(1f).build();
        OcrOptions.builder().confidenceThreshold(0.5f).build();
    }

    @Test
    void confidence_outOfRange_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> OcrOptions.builder().confidenceThreshold(-0.1f).build());
        assertThrows(IllegalArgumentException.class,
                () -> OcrOptions.builder().confidenceThreshold(1.1f).build());
    }

    @Test
    void preset_defaultOptions_usesBuilderDefaults() {
        OcrOptions options = OcrOptions.defaultOptions();
        assertEquals(0.6f, options.confidenceThreshold());
        assertTrue(options.detectOrientation());
        assertFalse(options.tableRecognition());
        assertFalse(options.handwriting());
        assertTrue(options.outputBoundingBoxes());
    }

    @Test
    void preset_scanDocument_enablesOrientationTableAndBoxes() {
        OcrOptions opts = OcrOptions.scanDocument();
        assertTrue(opts.detectOrientation());
        assertTrue(opts.tableRecognition());
        assertTrue(opts.outputBoundingBoxes());
        assertFalse(opts.handwriting());
    }

    @Test
    void preset_mobilePhoto_enablesOrientationAndBoxes() {
        OcrOptions opts = OcrOptions.mobilePhoto();
        assertTrue(opts.detectOrientation());
        assertFalse(opts.tableRecognition());
        assertTrue(opts.outputBoundingBoxes());
    }

    @Test
    void preset_handwrittenForm_enablesHandwritingAndBoxes() {
        OcrOptions opts = OcrOptions.handwrittenForm();
        assertTrue(opts.handwriting());
        assertTrue(opts.outputBoundingBoxes());
        assertFalse(opts.tableRecognition());
    }

    @Test
    void builder_isFreshInstance() {
        OcrOptions first = OcrOptions.builder().dpi(600).build();
        assertEquals(600, first.dpi());
        OcrOptions second = OcrOptions.builder().build();
        assertEquals(300, second.dpi(), "each builder() call must start from defaults");
    }

    @Test
    void equals_hashCode_compareAllFields() {
        OcrOptions base = OcrOptions.builder()
                .languages(Languages.JAPANESE)
                .dpi(200)
                .confidenceThreshold(0.7f)
                .build();
        OcrOptions same = OcrOptions.builder()
                .languages(Languages.JAPANESE)
                .dpi(200)
                .confidenceThreshold(0.7f)
                .build();
        OcrOptions differentDpi = OcrOptions.builder()
                .languages(Languages.JAPANESE)
                .dpi(201)
                .confidenceThreshold(0.7f)
                .build();
        assertEquals(base, same);
        assertEquals(base.hashCode(), same.hashCode());
        assertNotEquals(base, differentDpi);
        assertNotEquals(base, null);
        assertNotEquals(base, "not-an-options");
    }

    @Test
    void toString_containsKeyFields() {
        String s = OcrOptions.scanDocument().toString();
        assertTrue(s.contains("OcrOptions"));
        assertTrue(s.contains("tableRecognition"));
        assertTrue(s.contains("dpi"));
    }

    @Test
    void firstLanguage_returnsFirstOfSet() {
        OcrOptions opts = OcrOptions.builder()
                .languages(Languages.ENGLISH, Languages.JAPANESE)
                .build();
        assertEquals(Languages.ENGLISH, opts.firstLanguage());
    }
}