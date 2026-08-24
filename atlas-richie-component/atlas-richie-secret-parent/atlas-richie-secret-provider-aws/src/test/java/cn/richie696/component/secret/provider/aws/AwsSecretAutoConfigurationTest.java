/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.aws;

import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.starter.SecretRuntimeAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AwsSecretAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AwsSecretAutoConfiguration.class));

    @Test
    void defaultDisabledProviderIsCompletelyTransparent() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(AwsSecretClient.class));
    }

    @Test
    void enabledProviderCreatesTheSingleProjectFacadeWithoutCallingAwsEagerly() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AwsSecretAutoConfiguration.class,
                        SecretRuntimeAutoConfiguration.class))
                .withPropertyValues(
                        "platform.component.secret.enabled=true",
                        "platform.component.secret.property-source.application=orders",
                        "platform.component.secret.property-source.environment=test",
                        "platform.component.secret.aws.region=ap-southeast-1")
                .run(context -> {
                    assertThat(context).hasSingleBean(AwsSecretClient.class);
                    assertThat(context).hasSingleBean(SecretOperations.class);
                });
    }
}
