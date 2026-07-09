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
package com.richie.component.desensitize.jackson;

import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.core.permission.DefaultMaskPermissionEvaluator;
import com.richie.component.desensitize.core.registry.MaskRuleRegistry;
import com.richie.component.desensitize.core.registry.SensitiveKeyRegistry;
import com.richie.component.desensitize.core.service.DefaultMaskingService;
import com.richie.component.desensitize.core.service.MaskingService;
import com.richie.component.desensitize.core.strategy.AddressMaskingStrategy;
import com.richie.component.desensitize.core.strategy.BankCardMaskingStrategy;
import com.richie.component.desensitize.core.strategy.EmailMaskingStrategy;
import com.richie.component.desensitize.core.strategy.IdCardMaskingStrategy;
import com.richie.component.desensitize.core.strategy.MaskingStrategyRegistry;
import com.richie.component.desensitize.core.strategy.NameMaskingStrategy;
import com.richie.component.desensitize.core.strategy.PasswordMaskingStrategy;
import com.richie.component.desensitize.core.strategy.PhoneMaskingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * JacksonDesensitizeIntegrationTest 测试类。
 *
 * @author @richie696
 * @since 1.0.0
 * @version 1.0
 */
class JacksonDesensitizeTest {

    /**
     * 依赖组件。
     */
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.getSensitiveKeys().put("phone", MaskType.PHONE);
        MaskingService maskingService = new DefaultMaskingService(
                properties,
                new MaskingStrategyRegistry(List.of(
                        new PhoneMaskingStrategy(),
                        new IdCardMaskingStrategy(),
                        new BankCardMaskingStrategy(),
                        new EmailMaskingStrategy(),
                        new NameMaskingStrategy(),
                        new PasswordMaskingStrategy(),
                        new AddressMaskingStrategy())),
                new MaskRuleRegistry(properties),
                new SensitiveKeyRegistry(properties),
                new DefaultMaskPermissionEvaluator(properties));
        SensitiveKeyRegistry keyRegistry = new SensitiveKeyRegistry(properties);
        objectMapper = JsonMapper.builder()
                .addModule(new DesensitizeJacksonModule(maskingService, keyRegistry))
                .build();
    }

    @Test
    void voSensitiveFieldMasked() throws Exception {
        UserApiVo vo = new UserApiVo();
        vo.phone = "13812348000";
        vo.orderId = "O-100";
        String json = objectMapper.writeValueAsString(vo);
        assertTrue(json.contains("138****8000"));
        assertFalse(json.contains("13812348000"));
        assertTrue(json.contains("O-100"));
    }

    @Test
    void rootMapMaskedBySensitiveKeys() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone", "13812348000");
        row.put("orderId", "13812348000");
        String json = objectMapper.writeValueAsString(row);
        assertTrue(json.contains("138****8000"));
        assertTrue(json.contains("\"orderId\":\"13812348000\"") || json.contains("\"orderId\": \"13812348000\""));
    }

    @Test
    void voWithMapFieldMasked() throws Exception {
        UserWithExtraVo vo = new UserWithExtraVo();
        vo.extra = Map.of("phone", "13812348000", "code", "X1");
        String json = objectMapper.writeValueAsString(vo);
        assertTrue(json.contains("138****8000"));
        assertTrue(json.contains("X1"));
    }

    @Test
    void multipleSensitiveFieldsMasked() throws Exception {
        MultiFieldVo vo = new MultiFieldVo();
        vo.email = "user@example.com";
        vo.name = "张三丰";
        String json = objectMapper.writeValueAsString(vo);
        assertTrue(json.contains("u***@example.com"));
        assertTrue(json.contains("张**"));
    }

    @Test
    void logSceneOnlyFieldStillUsesApiResponseModifier() throws Exception {
        LogOnlyVo vo = new LogOnlyVo();
        vo.phone = "13812348000";
        String json = objectMapper.writeValueAsString(vo);
        assertTrue(json.contains("138****8000"));
    }

    static class MultiFieldVo {
        @Sensitive(type = MaskType.EMAIL, scenes = MaskScene.API_RESPONSE)
        public String email;
        @Sensitive(type = MaskType.NAME, scenes = MaskScene.API_RESPONSE)
        public String name;
    }

    static class LogOnlyVo {
        @Sensitive(type = MaskType.PHONE, scenes = MaskScene.LOG)
        public String phone;
    }

    @Test
    void mapSerializerHandlesNullValues() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone", null);
        row.put("code", "X1");
        String json = objectMapper.writeValueAsString(row);
        assertTrue(json.contains("\"phone\":null"));
        assertTrue(json.contains("X1"));
    }

    static class UserApiVo {
        @Sensitive(type = MaskType.PHONE, scenes = MaskScene.API_RESPONSE)
        public String phone;
        public String orderId;
    }

    static class UserWithExtraVo {
        public Map<String, Object> extra;
    }
}
