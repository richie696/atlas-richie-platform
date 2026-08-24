/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecretActuatorSanitizingAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecretActuatorSanitizingAutoConfiguration.class));

    @Test
    void disabledDoesNotCreateSanitizer() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean("atlasSecretSanitizingFunction"));
    }

    @Test
    void enabledSanitizesCatalogPropertiesOnly() {
        contextRunner
                .withPropertyValues("platform.component.secret.enabled=true")
                .run(context -> {
                    SanitizingFunction function = context.getBean(
                            "atlasSecretSanitizingFunction", SanitizingFunction.class);
                    MapPropertySource source = new MapPropertySource("test", Map.of());

                    SanitizableData secret = function.applyUnlessFiltered(new SanitizableData(
                            source,
                            "platform.component.test.api-key",
                            "never-expose"));
                    SanitizableData ordinary = function.applyUnlessFiltered(new SanitizableData(
                            source,
                            "platform.component.test.timeout",
                            "5s"));
                    SanitizableData dynamicSecret = function.applyUnlessFiltered(new SanitizableData(
                            source,
                            "platform.component.ai.chat.openai-4.api-keys[0]",
                            "never-expose-either"));
                    SanitizableData dynamicNearMiss = function.applyUnlessFiltered(new SanitizableData(
                            source,
                            "platform.component.ai.chat.openai.extra.api-keys[0]",
                            "ordinary"));

                    assertThat(secret.getValue()).isEqualTo(SanitizableData.SANITIZED_VALUE);
                    assertThat(dynamicSecret.getValue()).isEqualTo(SanitizableData.SANITIZED_VALUE);
                    assertThat(dynamicNearMiss.getValue()).isEqualTo("ordinary");
                    assertThat(ordinary.getValue()).isEqualTo("5s");
                });
    }
}
