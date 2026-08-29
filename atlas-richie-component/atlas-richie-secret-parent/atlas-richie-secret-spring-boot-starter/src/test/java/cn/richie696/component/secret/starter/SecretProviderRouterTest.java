/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.SigningBackend;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.provider.SecretProviderDescriptor;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.bootstrap.SecretProviderTopology;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapProviderFactory;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import cn.richie696.component.secret.core.DestroyableSecretValue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretProviderRouterTest {

    @Test
    void routesReadWrapAndSignToDifferentProviderSessions() {
        TestSession vault = new TestSession("vault-primary", true, false, false);
        TestSession kms = new TestSession("kms-primary", false, true, false);
        TestSession hsm = new TestSession("signing-hsm", false, false, true);
        SecretProviderRouter router = router(
                Map.of("vault-primary", vault, "kms-primary", kms, "signing-hsm", hsm),
                Map.of(
                        SecretProviderTopology.SECRET_READ, "vault-primary",
                        SecretProviderTopology.ENVELOPE_CRYPTO, "kms-primary",
                        SecretProviderTopology.SIGNING, "signing-hsm"));

        try (SecretValue value = router.read(SecretReference.latest("app/password"))) {
            assertThat(value.copyChars()).containsExactly('v', 'a', 'u', 'l', 't');
        }
        WrappedKey wrapped = router.wrap(
                KeyReference.envelopeEncryption("oauth"),
                new byte[]{1, 2},
                CryptoContext.empty());
        SignatureValue signature = router.sign(
                new KeyReference("oauth-signing", "current", KeyPurpose.SIGNING),
                new byte[]{3},
                CryptoContext.empty());

        assertThat(wrapped.algorithm()).isEqualTo("TEST-KMS");
        assertThat(signature.value()).isEqualTo("test-signature");
        assertThat(vault.readCalls).isEqualTo(1);
        assertThat(kms.wrapCalls).isEqualTo(1);
        assertThat(hsm.signCalls).isEqualTo(1);
    }

    @Test
    void failsClosedAtBootstrapWhenTheRoutedProviderDoesNotExposeTheCapability() {
        TestSession readOnly = new TestSession("read-only", true, false, false);
        assertThatThrownBy(() -> router(
                Map.of("read-only", readOnly),
                Map.of(SecretProviderTopology.SIGNING, "read-only")))
                .isInstanceOf(SecretBootstrapException.class)
                .extracting(exception -> ((SecretBootstrapException) exception).errorCode())
                .isEqualTo("SEC-CAP-001");
    }

    private SecretProviderRouter router(
            Map<String, TestSession> sessions,
            Map<String, String> routes) {
        Map<String, SecretBootstrapClient> clients = Map.copyOf(sessions);
        Map<String, SecretBootstrapProviderFactory> factories = sessions.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> factory(entry.getValue())));
        SecretProviderTopology topology = new SecretProviderTopology(factories, clients, routes, null);
        SecretBootstrapState state = new SecretBootstrapState(
                new BootstrapSecretProperties(), null, null, null, null, null, null, topology);
        return new SecretProviderRouter(state);
    }

    private SecretBootstrapProviderFactory factory(TestSession session) {
        return new SecretBootstrapProviderFactory() {
            @Override
            public String providerType() {
                return session.id;
            }

            @Override
            public Set<SecretCapability> capabilities() {
                java.util.EnumSet<SecretCapability> capabilities = java.util.EnumSet.noneOf(SecretCapability.class);
                if (session.readable) capabilities.add(SecretCapability.SECRET_READ);
                if (session.wrappable) {
                    capabilities.add(SecretCapability.KEY_WRAP);
                    capabilities.add(SecretCapability.KEY_UNWRAP);
                }
                if (session.signable) {
                    capabilities.add(SecretCapability.SIGN);
                    capabilities.add(SecretCapability.VERIFY);
                }
                return Set.copyOf(capabilities);
            }

            @Override
            public SecretBootstrapClient create(
                    BootstrapSecretProperties properties,
                    SecretBootstrapContext context) {
                throw new UnsupportedOperationException("not needed");
            }
        };
    }

    private static final class TestSession implements SecretBootstrapClient, SecretProviderSession {
        private final String id;
        private final boolean readable;
        private final boolean wrappable;
        private final boolean signable;
        private int readCalls;
        private int wrapCalls;
        private int signCalls;

        private TestSession(String id, boolean readable, boolean wrappable, boolean signable) {
            this.id = id;
            this.readable = readable;
            this.wrappable = wrappable;
            this.signable = signable;
        }

        @Override
        public SecretBootstrapResult load(SecretBootstrapRequest request) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public SecretProviderDescriptor descriptor() {
            java.util.EnumSet<SecretCapability> capabilities = java.util.EnumSet.noneOf(SecretCapability.class);
            if (readable) capabilities.add(SecretCapability.SECRET_READ);
            if (wrappable) {
                capabilities.add(SecretCapability.KEY_WRAP);
                capabilities.add(SecretCapability.KEY_UNWRAP);
            }
            if (signable) {
                capabilities.add(SecretCapability.SIGN);
                capabilities.add(SecretCapability.VERIFY);
            }
            return new SecretProviderDescriptor(id, id, Set.copyOf(capabilities));
        }

        @Override
        public Optional<SecretBackend> secretBackend() {
            if (!readable) return Optional.empty();
            return Optional.of(new SecretBackend() {
                @Override
                public SecretValue read(SecretReference reference) {
                    readCalls++;
                    return DestroyableSecretValue.ofChars("vault".toCharArray());
                }

                @Override
                public SecretMetadata metadata(SecretReference reference) {
                    return new SecretMetadata("v1", Instant.EPOCH, null, Map.of());
                }
            });
        }

        @Override
        public Optional<KeyWrappingBackend> keyWrappingBackend() {
            if (!wrappable) return Optional.empty();
            return Optional.of(new KeyWrappingBackend() {
                @Override
                public WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context) {
                    wrapCalls++;
                    return new WrappedKey(plaintextKey, "TEST-KMS");
                }

                @Override
                public byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context) {
                    return wrappedKey.value();
                }
            });
        }

        @Override
        public Optional<SigningBackend> signingBackend() {
            if (!signable) return Optional.empty();
            return Optional.of(new SigningBackend() {
                @Override
                public SignatureValue sign(KeyReference keyReference, byte[] payload, CryptoContext context) {
                    signCalls++;
                    return new SignatureValue("test-signature");
                }

                @Override
                public boolean verify(
                        KeyReference keyReference,
                        byte[] payload,
                        SignatureValue signature,
                        CryptoContext context) {
                    return "test-signature".equals(signature.value());
                }
            });
        }

        @Override
        public void close() {
        }
    }
}
