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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.json.JsonMapper;

import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveValueSerializerTest {

    @Mock
    private BeanProperty property;

    @Mock
    private AnnotatedMember member;

    private MaskingService maskingService;

    @BeforeEach
    void setUp() {
        DesensitizeProperties properties = new DesensitizeProperties();
        maskingService = new DefaultMaskingService(
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
    }

    @Test
    void serializeMasksValue() throws Exception {
        SensitiveValueSerializer serializer = new SensitiveValueSerializer(
                maskingService, MaskType.PHONE, MaskScene.API_RESPONSE, "phone", ApiVo.class);

        assertThat(write(serializer, "13812348000")).isEqualTo("\"138****8000\"");
        assertThat(write(serializer, null)).isEqualTo("null");
    }

    @Test
    void createContextualReturnsSelfWhenPropertyMissing() {
        SensitiveValueSerializer serializer = new SensitiveValueSerializer(
                maskingService, MaskType.PHONE, MaskScene.API_RESPONSE, "phone", ApiVo.class);

        assertThat(serializer.createContextual(null, null)).isSameAs(serializer);
    }

    @Test
    void createContextualReturnsSelfWhenAnnotationMissing() {
        SensitiveValueSerializer serializer = new SensitiveValueSerializer(
                maskingService, MaskType.PHONE, MaskScene.API_RESPONSE, "phone", ApiVo.class);
        when(property.getAnnotation(Sensitive.class)).thenReturn(null);

        assertThat(serializer.createContextual(null, property)).isSameAs(serializer);
    }

    @Test
    void createContextualBuildsPropertySpecificSerializer() throws Exception {
        SensitiveValueSerializer serializer = new SensitiveValueSerializer(
                maskingService, MaskType.PHONE, MaskScene.API_RESPONSE, "phone", ApiVo.class);
        Sensitive annotation = mock(Sensitive.class);
        when(annotation.type()).thenReturn(MaskType.EMAIL);
        when(annotation.scenes()).thenReturn(new MaskScene[]{MaskScene.LOG});
        when(property.getAnnotation(Sensitive.class)).thenReturn(annotation);
        when(property.getName()).thenReturn("email");
        when(property.getMember()).thenReturn(member);
        doReturn(ApiVo.class).when(member).getDeclaringClass();

        ValueSerializer<?> contextual = serializer.createContextual(null, property);

        assertThat(contextual).isNotSameAs(serializer);
        assertThat(write((SensitiveValueSerializer) contextual, "user@example.com")).contains("u***@example.com");
    }

    static class ApiVo {
        @Sensitive(type = MaskType.PHONE, scenes = MaskScene.API_RESPONSE)
        String phone;
    }

    private static String write(SensitiveValueSerializer serializer, String value) throws Exception {
        StringWriter writer = new StringWriter();
        JsonGenerator gen = JsonMapper.builder().build().createGenerator(writer);
        serializer.serialize(value, gen, null);
        gen.flush();
        return writer.toString();
    }
}
