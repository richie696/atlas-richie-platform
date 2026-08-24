/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.starter.SecretRuntimeAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class VaultSecretAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VaultSecretAutoConfiguration.class));

    @Test
    void defaultDisabledProviderIsCompletelyTransparent() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(VaultSecretClient.class));
    }

    @Test
    void enabledProviderCreatesTheSingleProjectFacadeWithoutConnectingEagerly() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        VaultSecretAutoConfiguration.class,
                        SecretRuntimeAutoConfiguration.class))
                .withPropertyValues(
                        "platform.component.secret.enabled=true",
                        "platform.component.secret.property-source.application=orders",
                        "platform.component.secret.property-source.environment=test",
                        "platform.component.secret.vault.endpoint=http://127.0.0.1:18200",
                        "platform.component.secret.vault.authentication.type=token",
                        "platform.component.secret.vault.authentication.token=not-used")
                .run(context -> {
                    assertThat(context).hasSingleBean(VaultSecretClient.class);
                    assertThat(context).hasSingleBean(SecretOperations.class);
                });
    }
}
