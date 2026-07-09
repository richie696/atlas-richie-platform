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
package com.richie.component.desensitize.jackson.serializer;

import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import com.richie.component.desensitize.jackson.DesensitizeJacksonModule;
import com.richie.component.desensitize.core.config.DesensitizeProperties;
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

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveMapSerializerTest {

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
        objectMapper = JsonMapper.builder()
                .addModule(new DesensitizeJacksonModule(maskingService, new SensitiveKeyRegistry(properties)))
                .build();
    }

    @Test
    void nestedMapWithNonStringValueUsesPojoSerializer() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("phone", "13812348000");
        nested.put("count", 2);
        Map<String, Object> root = Map.of("payload", nested);

        String json = objectMapper.writeValueAsString(root);

        assertThat(json).contains("138****8000");
        assertThat(json).contains("\"count\":2");
    }

    @Test
    void sensitiveAnnotatedNullFieldWritesNull() throws Exception {
        NullFieldVo vo = new NullFieldVo();
        vo.orderId = "O-1";

        String json = objectMapper.writeValueAsString(vo);

        assertThat(json).contains("\"phone\":null");
        assertThat(json).contains("O-1");
    }

    static class NullFieldVo {
        @Sensitive(type = MaskType.PHONE, scenes = MaskScene.API_RESPONSE)
        public String phone;
        public String orderId;
    }
}
