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
package com.richie.component.desensitize.core.permission;

import com.richie.component.desensitize.core.config.DesensitizeProperties;
import com.richie.component.desensitize.core.model.MaskContext;
import com.richie.component.desensitize.core.model.MaskScene;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMaskPermissionEvaluatorTest {

    @Test
    void shouldMaskWhenPermissionDisabled() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.getPermission().setEnabled(false);
        DefaultMaskPermissionEvaluator evaluator = new DefaultMaskPermissionEvaluator(properties);

        assertThat(evaluator.shouldMask(MaskContext.of(MaskScene.LOG))).isTrue();
    }

    @Test
    void plainTextRoleSkipsMasking() {
        DesensitizeProperties properties = new DesensitizeProperties();
        properties.getPermission().setEnabled(true);
        properties.getPermission().setPlainTextRoles(Set.of("ADMIN"));
        DefaultMaskPermissionEvaluator evaluator = new DefaultMaskPermissionEvaluator(properties);

        assertThat(evaluator.shouldMask(MaskContext.of(MaskScene.LOG).withRoles(Set.of("ADMIN")))).isFalse();
        assertThat(evaluator.shouldMask(MaskContext.of(MaskScene.LOG).withRoles(Set.of("USER")))).isTrue();
    }
}
