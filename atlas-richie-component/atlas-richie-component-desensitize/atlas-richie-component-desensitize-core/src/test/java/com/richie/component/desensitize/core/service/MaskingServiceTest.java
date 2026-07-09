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
package com.richie.component.desensitize.core.service;

import com.richie.component.desensitize.core.DesensitizeTestSupport;
import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskContext;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
/**
 * MaskingServiceTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class MaskingServiceTest {

    /**
     * 核心依赖组件。
     */
    private MaskingService maskingService;

    @BeforeEach
    void setUp() {
        maskingService = DesensitizeTestSupport.defaultMaskingService();
    }

    @AfterEach
    void tearDown() {
        DesensitizeTestSupport.clearUtils();
    }

    @Test
    void maskPhone() {
        assertEquals("138****8000", maskingService.mask("13812348000", MaskType.PHONE));
    }

    @Test
    void maskMapBySensitiveKeys() {
        Map<String, Object> masked = maskingService.maskMap(DesensitizeTestSupport.sampleRow());
        assertEquals("138****8000", masked.get("phone"));
        assertEquals("13812348000", masked.get("orderId"));
    }

    @Test
    void maskMapNested() {
        Map<String, Object> source = Map.of(
                "outer", Map.of("phone", "13812348000")
        );
        Map<String, Object> masked = maskingService.maskMap(source);
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) masked.get("outer");
        assertEquals("138****8000", inner.get("phone"));
    }

    @Test
    void disabledReturnsPlainText() {
        DesensitizeProperties properties = DesensitizeTestSupport.defaultProperties();
        properties.setEnabled(false);
        MaskingService disabled = DesensitizeTestSupport.maskingService(properties);
        assertEquals("13812348000", disabled.mask("13812348000", MaskType.PHONE));
    }

    @Test
    void sceneDisabledReturnsPlainText() {
        DesensitizeProperties properties = DesensitizeTestSupport.defaultProperties();
        properties.getScenes().put("log", false);
        MaskingService service = DesensitizeTestSupport.maskingService(properties);
        assertEquals("13812348000", service.mask("13812348000", MaskType.PHONE, MaskScene.LOG));
    }

    @Test
    void permissionPlainTextRole() {
        DesensitizeProperties properties = DesensitizeTestSupport.defaultProperties();
        properties.getPermission().setEnabled(true);
        properties.getPermission().setPlainTextRoles(Set.of("ADMIN"));
        MaskingService service = DesensitizeTestSupport.maskingService(properties);
        String plain = service.mask(
                "13812348000",
                MaskContext.of(MaskScene.LOG).withRoles(Set.of("ADMIN")),
                MaskType.PHONE);
        assertEquals("13812348000", plain);
    }

    @Test
    void desensitizeUtilsMask() {
        DesensitizeTestSupport.bindDefaults();
        assertEquals("138****8000", com.richie.component.desensitize.core.util.DesensitizeUtils.mask("13812348000", MaskType.PHONE));
    }

    @Test
    void maskMapDoesNotMutateSource() {
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("phone", "13812348000");
        Map<String, Object> masked = maskingService.maskMap(source);
        assertFalse(source == masked);
        assertEquals("13812348000", source.get("phone"));
        assertEquals("138****8000", masked.get("phone"));
    }
}
