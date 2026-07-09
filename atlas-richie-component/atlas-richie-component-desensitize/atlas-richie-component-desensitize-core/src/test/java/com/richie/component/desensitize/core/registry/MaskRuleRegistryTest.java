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
package com.richie.component.desensitize.core.registry;

import com.richie.component.desensitize.core.annotation.Sensitive;
import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskRule;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaskRuleRegistryTest {

    @Test
    void resolveFieldTypeFromYamlConfig() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.getFields().put(Profile.class.getName(), Map.of("secret", MaskType.PASSWORD));
        MaskRuleRegistry registry = new MaskRuleRegistry(properties);

        assertThat(registry.resolveFieldType(Profile.class, "secret", MaskScene.LOG))
                .contains(MaskType.PASSWORD);
    }

    @Test
    void resolveFieldTypeFromSensitiveAnnotation() {
        MaskRuleRegistry registry = new MaskRuleRegistry(new DesensitizeProperties());

        assertThat(registry.resolveFieldType(AnnotatedVo.class, "phone", MaskScene.API_RESPONSE))
                .contains(MaskType.PHONE);
        assertThat(registry.resolveFieldType(AnnotatedVo.class, "phone", MaskScene.LOG))
                .isEmpty();
    }

    @Test
    void resolveFieldTypeFromSuperclass() {
        MaskRuleRegistry registry = new MaskRuleRegistry(new DesensitizeProperties());

        assertThat(registry.resolveFieldType(ChildVo.class, "email", MaskScene.API_RESPONSE))
                .contains(MaskType.EMAIL);
    }

    @Test
    void resolveFieldTypeReturnsEmptyForUnknownField() {
        MaskRuleRegistry registry = new MaskRuleRegistry(new DesensitizeProperties());

        assertThat(registry.resolveFieldType(AnnotatedVo.class, "missing", MaskScene.API_RESPONSE))
                .isEmpty();
        assertThat(registry.resolveFieldType(null, "phone", MaskScene.LOG)).isEmpty();
        assertThat(registry.resolveFieldType(AnnotatedVo.class, null, MaskScene.LOG)).isEmpty();
    }

    @Test
    void toRuleUsesCustomTypeRuleAndDefaultMaskChar() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.setDefaultMaskChar('#');
        DesensitizeProperties.TypeRule typeRule = new DesensitizeProperties.TypeRule();
        typeRule.setKeepLeft(2);
        typeRule.setKeepRight(2);
        typeRule.setMaskChar('x');
        properties.getTypeRules().put(MaskType.PHONE, typeRule);
        MaskRuleRegistry registry = new MaskRuleRegistry(properties);

        MaskRule rule = registry.toRule(MaskType.PHONE);

        assertThat(rule.keepLeft()).isEqualTo(2);
        assertThat(rule.keepRight()).isEqualTo(2);
        assertThat(rule.maskChar()).isEqualTo('x');
    }

    @Test
    void toRuleFallsBackToDefaultsWhenTypeRulePartial() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.getTypeRules().put(MaskType.EMAIL, new DesensitizeProperties.TypeRule());
        MaskRuleRegistry registry = new MaskRuleRegistry(properties);

        MaskRule rule = registry.toRule(MaskType.EMAIL);

        assertThat(rule.keepLeft()).isEqualTo(MaskRule.defaultKeepLeft(MaskType.EMAIL));
        assertThat(rule.keepRight()).isEqualTo(MaskRule.defaultKeepRight(MaskType.EMAIL));
        assertThat(rule.maskChar()).isEqualTo(properties.getDefaultMaskChar());
    }

    static class Profile {
        String secret;
    }

    static class AnnotatedVo {
        @Sensitive(type = MaskType.PHONE, scenes = MaskScene.API_RESPONSE)
        String phone;
    }

    static class ParentVo {
        @Sensitive(type = MaskType.EMAIL, scenes = MaskScene.API_RESPONSE)
        String email;
    }

    static class ChildVo extends ParentVo {
        String nickname;
    }
}
