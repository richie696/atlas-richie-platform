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
