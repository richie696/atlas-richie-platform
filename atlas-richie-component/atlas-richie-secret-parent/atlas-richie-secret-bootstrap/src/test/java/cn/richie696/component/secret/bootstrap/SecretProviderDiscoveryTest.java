/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretProviderDiscoveryTest {
    private final SecretProviderDiscovery discovery = new SecretProviderDiscovery();

    @Test
    void singleProviderDoesNotRequireDuplicateTypeConfiguration() {
        SecretBootstrapProviderFactory vault = factory("vault");

        assertThat(discovery.select(List.of(vault), new BootstrapSecretProperties())).isSameAs(vault);
    }

    @Test
    void multipleProvidersRequireExplicitSelection() {
        assertThatThrownBy(() -> discovery.select(
                List.of(factory("vault"), factory("aws")),
                new BootstrapSecretProperties()))
                .isInstanceOf(SecretBootstrapException.class)
                .extracting(exception -> ((SecretBootstrapException) exception).errorCode())
                .isEqualTo("SEC-BOOT-002");
    }

    @Test
    void activeProviderSelectsMatchingType() {
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setActiveProvider("aws");
        SecretBootstrapProviderFactory aws = factory("aws");

        assertThat(discovery.select(List.of(factory("vault"), aws), properties)).isSameAs(aws);
    }

    @Test
    void explicitProviderMustMatchEvenWhenOnlyOneIsInstalled() {
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setActiveProvider("aws");

        assertThatThrownBy(() -> discovery.select(List.of(factory("vault")), properties))
                .isInstanceOf(SecretBootstrapException.class)
                .extracting(exception -> ((SecretBootstrapException) exception).errorCode())
                .isEqualTo("SEC-BOOT-002");
    }

    @Test
    void namedProvidersResolveProviderIdsIndependentlyFromProviderTypes() {
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        BootstrapSecretProperties.Provider vault = new BootstrapSecretProperties.Provider();
        vault.setType("vault");
        BootstrapSecretProperties.Provider hsm = new BootstrapSecretProperties.Provider();
        hsm.setType("pkcs11");
        Map<String, BootstrapSecretProperties.Provider> providers = new LinkedHashMap<>();
        providers.put("vault-primary", vault);
        providers.put("signing-hsm", hsm);
        properties.setProviders(providers);

        SecretBootstrapProviderFactory vaultFactory = factory("vault");
        SecretBootstrapProviderFactory hsmFactory = factory("pkcs11");

        assertThat(discovery.selectAll(List.of(vaultFactory, hsmFactory), properties))
                .containsEntry("vault-primary", vaultFactory)
                .containsEntry("signing-hsm", hsmFactory)
                .hasSize(2);
    }

    @Test
    void routingCanSelectMultipleProviderTypesWithoutAnActiveProvider() {
        BootstrapSecretProperties properties = new BootstrapSecretProperties();
        properties.setRouting(Map.of(
                SecretProviderTopology.PROPERTY_SOURCE, "vault",
                SecretProviderTopology.SIGNING, "pkcs11"));

        assertThat(discovery.selectAll(
                List.of(factory("vault"), factory("pkcs11")), properties))
                .containsOnlyKeys("vault", "pkcs11");
    }

    private SecretBootstrapProviderFactory factory(String type) {
        return new SecretBootstrapProviderFactory() {
            @Override
            public String providerType() {
                return type;
            }

            @Override
            public Set<SecretCapability> capabilities() {
                return Set.of(SecretCapability.SECRET_READ);
            }

            @Override
            public SecretBootstrapClient create(
                    BootstrapSecretProperties properties,
                    SecretBootstrapContext context) {
                throw new UnsupportedOperationException("not needed");
            }
        };
    }
}
