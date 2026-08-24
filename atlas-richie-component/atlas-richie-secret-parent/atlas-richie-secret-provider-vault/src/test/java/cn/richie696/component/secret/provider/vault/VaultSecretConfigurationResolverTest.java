/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VaultSecretConfigurationResolverTest {

    @Test
    void resolvesSingleProviderConfiguration() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("platform.component.secret.vault.transit.key-bindings.default-envelope", "orders-key");

        var resolved = new VaultSecretConfigurationResolver()
                .resolve(environment, new BootstrapSecretProperties());

        assertThat(resolved.providerId()).isEqualTo("vault");
        assertThat(resolved.properties().getEndpoint().toString()).isEqualTo("http://127.0.0.1:8200");
        assertThat(resolved.properties().getTransit().getKeyBindings())
                .containsEntry("default-envelope", "orders-key");
        assertThat(resolved.configurationHash()).hasSize(64);
    }

    @Test
    void resolvesNamedProviderWithoutLeakingProviderDetailsIntoApi() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.providers.vault-primary.endpoint", "https://vault.internal")
                .withProperty("platform.component.secret.providers.vault-primary.authentication.type", "token-file")
                .withProperty("platform.component.secret.providers.vault-primary.authentication.token-file", "/run/vault/token");
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.setActiveProvider("vault-primary");
        BootstrapSecretProperties.Provider provider = new BootstrapSecretProperties.Provider();
        provider.setType("vault");
        bootstrap.setProviders(Map.of("vault-primary", provider));

        var resolved = new VaultSecretConfigurationResolver().resolve(environment, bootstrap);

        assertThat(resolved.providerId()).isEqualTo("vault-primary");
        assertThat(resolved.properties().getAuthentication().getType())
                .isEqualTo(VaultSecretProperties.AuthenticationType.TOKEN_FILE);
    }

    @Test
    void rejectsPlainHttpForNonLoopbackVault() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", "http://vault.internal:8200")
                .withProperty("platform.component.secret.vault.authentication.type", "token")
                .withProperty("platform.component.secret.vault.authentication.token", "development-only");

        assertThatThrownBy(() -> new VaultSecretConfigurationResolver()
                .resolve(environment, new BootstrapSecretProperties()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsMissingLogicalToPhysicalTransitBindingValue() {
        MockEnvironment environment = baseEnvironment()
                .withProperty("platform.component.secret.vault.transit.key-bindings.default-envelope", "../physical");

        assertThatThrownBy(() -> new VaultSecretConfigurationResolver()
                .resolve(environment, new BootstrapSecretProperties()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("Transit key binding");
    }

    @Test
    void resolvesJwtAuthenticationWithoutExposingJwtValue() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", "https://vault.internal")
                .withProperty("platform.component.secret.vault.authentication.type", "jwt")
                .withProperty("platform.component.secret.vault.authentication.jwt-role", "orders")
                .withProperty("platform.component.secret.vault.authentication.jwt-file", "/run/secrets/orders.jwt");

        var resolved = new VaultSecretConfigurationResolver()
                .resolve(environment, new BootstrapSecretProperties());

        assertThat(resolved.properties().getAuthentication().getType())
                .isEqualTo(VaultSecretProperties.AuthenticationType.JWT);
        assertThat(resolved.properties().getAuthentication().getJwtFile())
                .isEqualTo("/run/secrets/orders.jwt");
    }

    @Test
    void configurationHashIsStableForEquivalentMapOrderings() {
        MockEnvironment first = baseEnvironment()
                .withProperty("platform.component.secret.vault.transit.key-bindings.a", "key-a")
                .withProperty("platform.component.secret.vault.transit.key-bindings.b", "key-b")
                .withProperty("platform.component.secret.vault.secrets.orders.path", "orders")
                .withProperty("platform.component.secret.vault.secrets.orders.field", "password");
        MockEnvironment second = baseEnvironment()
                .withProperty("platform.component.secret.vault.transit.key-bindings.b", "key-b")
                .withProperty("platform.component.secret.vault.transit.key-bindings.a", "key-a")
                .withProperty("platform.component.secret.vault.secrets.orders.field", "password")
                .withProperty("platform.component.secret.vault.secrets.orders.path", "orders");

        assertThat(new VaultSecretConfigurationResolver().resolve(first, new BootstrapSecretProperties())
                .configurationHash())
                .isEqualTo(new VaultSecretConfigurationResolver().resolve(second, new BootstrapSecretProperties())
                        .configurationHash());
    }

    private MockEnvironment baseEnvironment() {
        return new MockEnvironment()
                .withProperty("platform.component.secret.vault.endpoint", "http://127.0.0.1:8200")
                .withProperty("platform.component.secret.vault.authentication.type", "token")
                .withProperty("platform.component.secret.vault.authentication.token", "development-only");
    }
}
