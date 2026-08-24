/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.starter.SecretRuntimeAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunSecretAutoConfigurationTest {
    @Test
    void defaultDisabledProviderIsCompletelyTransparent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AliyunSecretAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(AliyunSecretClient.class));
    }

    @Test
    void enabledProviderCreatesOnlyTheProviderIndependentFacadeForBusinessCode() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AliyunSecretAutoConfiguration.class, SecretRuntimeAutoConfiguration.class))
                .withPropertyValues(
                        "platform.component.secret.enabled=true",
                        "platform.component.secret.property-source.application=orders",
                        "platform.component.secret.property-source.environment=test",
                        "platform.component.secret.aliyun.region=cn-hangzhou")
                .run(context -> {
                    assertThat(context).hasSingleBean(AliyunSecretClient.class);
                    assertThat(context).hasSingleBean(SecretOperations.class);
                });
    }
}
