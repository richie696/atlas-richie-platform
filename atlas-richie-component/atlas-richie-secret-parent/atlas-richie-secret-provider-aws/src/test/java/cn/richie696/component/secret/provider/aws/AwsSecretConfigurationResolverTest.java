/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.aws;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsSecretConfigurationResolverTest {

    @Test
    void resolvesSingleProviderWithDefaultCredentialChain() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("platform.component.secret.aws.kms.key-bindings.default-envelope", "arn:aws:kms:cn-north-1:1:key/test");

        var resolved = new AwsSecretConfigurationResolver()
                .resolve(environment, new BootstrapSecretProperties());

        assertThat(resolved.providerId()).isEqualTo("aws");
        assertThat(resolved.properties().getRegion()).isEqualTo("cn-north-1");
        assertThat(resolved.properties().getAuthentication().getType())
                .isEqualTo(AwsSecretProperties.AuthenticationType.DEFAULT_CHAIN);
        assertThat(resolved.configurationHash()).hasSize(64);
    }

    @Test
    void resolvesNamedProviderAndKeepsProfileInsideProviderConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.providers.aws-primary.region", "ap-southeast-1")
                .withProperty("platform.component.secret.providers.aws-primary.authentication.type", "profile")
                .withProperty("platform.component.secret.providers.aws-primary.authentication.profile-name", "orders-prod");
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.setActiveProvider("aws-primary");
        BootstrapSecretProperties.Provider provider = new BootstrapSecretProperties.Provider();
        provider.setType("aws");
        bootstrap.setProviders(Map.of("aws-primary", provider));

        var resolved = new AwsSecretConfigurationResolver().resolve(environment, bootstrap);

        assertThat(resolved.providerId()).isEqualTo("aws-primary");
        assertThat(resolved.properties().getAuthentication().getType())
                .isEqualTo(AwsSecretProperties.AuthenticationType.PROFILE);
        assertThat(resolved.properties().getAuthentication().getProfileName()).isEqualTo("orders-prod");
    }

    @Test
    void rejectsPlainHttpForExternalEndpointOverride() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("platform.component.secret.aws.endpoints.secrets-manager", "http://localstack.internal:4566");

        assertThatThrownBy(() -> new AwsSecretConfigurationResolver()
                .resolve(environment, new BootstrapSecretProperties()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void allowsLoopbackHttpOnlyForLocalEmulator() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("platform.component.secret.aws.endpoints.secrets-manager", "http://127.0.0.1:4566")
                .withProperty("platform.component.secret.aws.endpoints.kms", "http://localhost:4566");

        var resolved = new AwsSecretConfigurationResolver()
                .resolve(environment, new BootstrapSecretProperties());

        assertThat(resolved.properties().getEndpoints().getKms().getHost()).isEqualTo("localhost");
    }

    private MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("platform.component.secret.aws.region", "cn-north-1");
    }
}
