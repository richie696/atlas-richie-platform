/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.aws;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class AwsSecretBootstrapProviderFactoryTest {

    @Test
    void serviceLoaderDiscoversOnlyTruthfullyImplementedCapabilities() {
        SecretBootstrapProviderFactory factory = ServiceLoader
                .load(SecretBootstrapProviderFactory.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> "aws".equals(candidate.providerType()))
                .findFirst()
                .orElseThrow();

        assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                SecretCapability.SECRET_READ,
                SecretCapability.SECRET_VERSIONING,
                SecretCapability.KEY_WRAP,
                SecretCapability.KEY_UNWRAP,
                SecretCapability.SIGN,
                SecretCapability.VERIFY);
        assertThat(factory.capabilities())
                .doesNotContain(SecretCapability.DIRECT_ENCRYPT, SecretCapability.REWRAP);
    }
}
