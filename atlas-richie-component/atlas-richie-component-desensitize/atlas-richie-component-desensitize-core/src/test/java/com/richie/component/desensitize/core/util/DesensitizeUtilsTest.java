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
package com.richie.component.desensitize.core.util;

import com.richie.component.desensitize.core.DesensitizeTestSupport;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.core.service.ObjectMaskingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesensitizeUtilsTest {

    @BeforeEach
    void setUp() {
        DesensitizeTestSupport.bindDefaults();
    }

    @AfterEach
    void tearDown() {
        DesensitizeUtils.clear();
    }

    @Test
    void maskAndMaskMapDelegateToServices() {
        assertThat(DesensitizeUtils.mask("13812348000", MaskType.PHONE)).isEqualTo("138****8000");
        assertThat(DesensitizeUtils.mask("13812348000", MaskType.PHONE, MaskScene.LOG))
                .isEqualTo("138****8000");
        Map<String, Object> masked = DesensitizeUtils.maskMap(DesensitizeTestSupport.sampleRow());
        assertThat(masked.get("phone")).isEqualTo("138****8000");
        assertThat(DesensitizeUtils.maskMap(DesensitizeTestSupport.sampleRow(), MaskScene.LOG).get("phone"))
                .isEqualTo("138****8000");
    }

    @Test
    void toSafeJsonAndToSafeStringDelegateToObjectMasking() {
        Map<String, Object> row = DesensitizeTestSupport.sampleRow();
        assertThat(DesensitizeUtils.toSafeString(row)).contains("138****8000");
        assertThat(DesensitizeUtils.toSafeJson(row)).contains("138****8000");
    }

    @Test
    void bindRejectsNullServices() {
        MaskingService masking = DesensitizeTestSupport.defaultMaskingService();
        ObjectMaskingService objectMasking = DesensitizeTestSupport.defaultObjectMaskingService(
                masking, DesensitizeTestSupport.defaultProperties());

        assertThatThrownBy(() -> DesensitizeUtils.bind(null, objectMasking))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DesensitizeUtils.bind(masking, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void requireServicesThrowsWhenNotInitialized() {
        DesensitizeUtils.clear();

        assertThatThrownBy(() -> DesensitizeUtils.mask("x", MaskType.PHONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialized");
        assertThatThrownBy(() -> DesensitizeUtils.toSafeJson(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not initialized");
    }
}
