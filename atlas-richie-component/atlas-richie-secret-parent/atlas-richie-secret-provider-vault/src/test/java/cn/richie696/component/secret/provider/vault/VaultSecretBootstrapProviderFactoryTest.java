/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class VaultSecretBootstrapProviderFactoryTest {

    @Test
    void serviceLoaderDiscoversOnlyTruthfullyImplementedCapabilities() {
        SecretBootstrapProviderFactory factory = ServiceLoader
                .load(SecretBootstrapProviderFactory.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> "vault".equals(candidate.providerType()))
                .findFirst()
                .orElseThrow();

        assertThat(factory.providerType()).isEqualTo("vault");
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

    @Test
    void createsClientFromBootstrapContextWithoutConnectingToVault() {
        VaultSecretBootstrapProviderFactory factory = new VaultSecretBootstrapProviderFactory();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", "http://127.0.0.1:8200")
                .withProperty("platform.component.secret.vault.authentication.type", "token")
                .withProperty("platform.component.secret.vault.authentication.token", "development-only");
        SecretBootstrapContext context = new SecretBootstrapContext(environment, getClass().getClassLoader());

        SecretBootstrapClient client = factory.create(new BootstrapSecretProperties(), context);

        assertThat(client).isInstanceOf(VaultSecretClient.class);
        client.close();
    }
}
