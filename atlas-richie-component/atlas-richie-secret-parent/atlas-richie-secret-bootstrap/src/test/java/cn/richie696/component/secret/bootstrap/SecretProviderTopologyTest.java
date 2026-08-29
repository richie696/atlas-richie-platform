/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.provider.SecretProviderDescriptor;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretProviderTopologyTest {

    @Test
    void resolvesEachCapabilityRouteAndClosesEveryUniqueClientOnce() {
        AtomicInteger vaultCloses = new AtomicInteger();
        AtomicInteger hsmCloses = new AtomicInteger();
        SecretBootstrapClient vault = client(vaultCloses);
        SecretBootstrapClient hsm = client(hsmCloses);
        SecretProviderTopology topology = new SecretProviderTopology(
                Map.of("vault-primary", factory("vault"), "signing-hsm", factory("pkcs11")),
                Map.of("vault-primary", vault, "signing-hsm", hsm),
                Map.of(
                        SecretProviderTopology.PROPERTY_SOURCE, "vault-primary",
                        SecretProviderTopology.SECRET_READ, "vault-primary",
                        SecretProviderTopology.SIGNING, "signing-hsm"),
                null);

        assertThat(topology.routedClient(SecretProviderTopology.SECRET_READ)).isSameAs(vault);
        assertThat(topology.routedClient(SecretProviderTopology.SIGNING)).isSameAs(hsm);

        topology.close();
        topology.close();

        assertThat(vaultCloses).hasValue(1);
        assertThat(hsmCloses).hasValue(1);
    }

    @Test
    void rejectsUnknownRoutesAndUnknownProviderReferencesAtBootstrap() {
        assertThatThrownBy(() -> new SecretProviderTopology(
                Map.of("vault", factory("vault")),
                Map.of("vault", client(new AtomicInteger())),
                Map.of("unsupported", "vault"),
                null))
                .isInstanceOf(SecretBootstrapException.class)
                .extracting(exception -> ((SecretBootstrapException) exception).errorCode())
                .isEqualTo("SEC-BOOT-002");

        assertThatThrownBy(() -> new SecretProviderTopology(
                Map.of("vault", factory("vault")),
                Map.of("vault", client(new AtomicInteger())),
                Map.of(SecretProviderTopology.SIGNING, "missing"),
                null))
                .isInstanceOf(SecretBootstrapException.class)
                .extracting(exception -> ((SecretBootstrapException) exception).errorCode())
                .isEqualTo("SEC-BOOT-002");
    }

    @Test
    void rejectsCapabilityRouteMismatchAtBootstrap() {
        assertThatThrownBy(() -> new SecretProviderTopology(
                Map.of("vault", factory("vault")),
                Map.of("vault", client(new AtomicInteger())),
                Map.of(SecretProviderTopology.SIGNING, "vault"),
                null))
                .isInstanceOf(SecretBootstrapException.class)
                .extracting(exception -> ((SecretBootstrapException) exception).errorCode())
                .isEqualTo("SEC-CAP-001");
    }

    @Test
    void validatesTheRuntimeSessionCapabilityInsteadOfFactorySuperset() {
        class RuntimeSession implements SecretBootstrapClient, SecretProviderSession {
            @Override
            public cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult load(
                    cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest request) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public SecretProviderDescriptor descriptor() {
                return new SecretProviderDescriptor("pkcs11", "hsm", Set.of(SecretCapability.KEY_WRAP,
                        SecretCapability.KEY_UNWRAP));
            }

            @Override
            public java.util.Optional<cn.richie696.component.secret.api.SecretBackend> secretBackend() {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<cn.richie696.component.secret.api.crypto.KeyWrappingBackend> keyWrappingBackend() {
                return java.util.Optional.empty();
            }

            @Override
            public void close() {
            }
        }
        SecretBootstrapClient session = new RuntimeSession();

        assertThatThrownBy(() -> new SecretProviderTopology(
                Map.of("hsm", factory("pkcs11")),
                Map.of("hsm", session),
                Map.of(SecretProviderTopology.SIGNING, "hsm"),
                null))
                .isInstanceOf(SecretBootstrapException.class)
                .extracting(exception -> ((SecretBootstrapException) exception).errorCode())
                .isEqualTo("SEC-CAP-001");
    }

    private SecretBootstrapClient client(AtomicInteger closes) {
        return new SecretBootstrapClient() {
            @Override
            public cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult load(
                    cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest request) {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public void close() {
                closes.incrementAndGet();
            }
        };
    }

    private SecretBootstrapProviderFactory factory(String type) {
        return new SecretBootstrapProviderFactory() {
            @Override
            public String providerType() {
                return type;
            }

            @Override
            public Set<SecretCapability> capabilities() {
                return "pkcs11".equals(type)
                        ? Set.of(SecretCapability.SIGN, SecretCapability.VERIFY)
                        : Set.of(SecretCapability.SECRET_READ,
                                SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
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
