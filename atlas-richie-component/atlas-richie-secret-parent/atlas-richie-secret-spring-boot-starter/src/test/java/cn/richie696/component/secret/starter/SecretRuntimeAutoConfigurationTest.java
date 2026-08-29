/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.EnvelopeCrypto;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SecretCipher;
import cn.richie696.component.secret.api.crypto.SigningBackend;
import cn.richie696.component.secret.api.crypto.SigningService;
import cn.richie696.component.secret.api.crypto.WrappedKey;
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
import cn.richie696.component.secret.core.SecretSnapshotManager;
import cn.richie696.component.secret.core.crypto.ArseEnvelopeCodec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRuntimeAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecretRuntimeAutoConfiguration.class));

    @Test
    void disabledCreatesNoSecretBeans() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(SecretSnapshotManager.class);
            assertThat(context).doesNotHaveBean(ArseEnvelopeCodec.class);
        });
    }

    @Test
    void enabledCreatesCoreBeansAndOptionalFacades() {
        contextRunner
                .withPropertyValues("platform.component.secret.enabled=true")
                .withUserConfiguration(BackendConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SecretSnapshotManager.class);
                    assertThat(context).hasSingleBean(ArseEnvelopeCodec.class);
                    assertThat(context).hasBean("secretResolver");
                    assertThat(context).hasBean("envelopeCrypto");
                    assertThat(context).hasSingleBean(SecretCipher.class);
                    assertThat(context).hasSingleBean(SecretOperations.class);
                });
    }

    @Test
    void runtimeCapabilitiesControlWhichFacadesArePublished() {
        contextRunner
                .withPropertyValues("platform.component.secret.enabled=true")
                .withUserConfiguration(KmsOnlyTopologyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(KeyWrappingBackend.class);
                    assertThat(context).hasSingleBean(EnvelopeCrypto.class);
                    assertThat(context).hasSingleBean(SecretCipher.class);
                    assertThat(context).doesNotHaveBean(SecretBackend.class);
                    assertThat(context).doesNotHaveBean(SigningBackend.class);
                    assertThat(context).doesNotHaveBean(SigningService.class);
                    assertThat(context).doesNotHaveBean(SecretOperations.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class BackendConfiguration {
        @Bean
        SecretBackend secretBackend() {
            return new SecretBackend() {
                @Override
                public SecretValue read(SecretReference reference) {
                    return DestroyableSecretValue.ofChars("test".toCharArray());
                }

                @Override
                public SecretMetadata metadata(SecretReference reference) {
                    return new SecretMetadata("v1", Instant.EPOCH, null, Map.of());
                }
            };
        }

        @Bean
        KeyWrappingBackend keyWrappingBackend() {
            return new KeyWrappingBackend() {
                @Override
                public WrappedKey wrap(
                        KeyReference keyReference,
                        byte[] plaintextKey,
                        CryptoContext context) {
                    return new WrappedKey(plaintextKey, "TEST");
                }

                @Override
                public byte[] unwrap(
                        KeyReference keyReference,
                        WrappedKey wrappedKey,
                        CryptoContext context) {
                    return wrappedKey.value();
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class KmsOnlyTopologyConfiguration {
        @Bean
        SecretBootstrapState secretBootstrapState() {
            BootstrapSecretProperties properties = new BootstrapSecretProperties();
            properties.setEnabled(true);
            KmsOnlySession session = new KmsOnlySession();
            SecretBootstrapProviderFactory factory = new SecretBootstrapProviderFactory() {
                @Override
                public String providerType() {
                    return "test";
                }

                @Override
                public Set<SecretCapability> capabilities() {
                    return Set.of(
                            SecretCapability.KEY_WRAP,
                            SecretCapability.KEY_UNWRAP,
                            SecretCapability.SIGN,
                            SecretCapability.VERIFY);
                }

                @Override
                public SecretBootstrapClient create(
                        BootstrapSecretProperties ignored,
                        SecretBootstrapContext context) {
                    return session;
                }
            };
            SecretProviderTopology topology = new SecretProviderTopology(
                    Map.of("kms", factory),
                    Map.of("kms", session),
                    Map.of(SecretProviderTopology.ENVELOPE_CRYPTO, "kms"),
                    "kms");
            SecretBootstrapResult result = new SecretBootstrapResult(
                    "kms", "v1", "test", Instant.EPOCH, Map.of(), null);
            return new SecretBootstrapState(
                    properties, null, null, result, null, null, null, topology);
        }
    }

    static final class KmsOnlySession implements SecretBootstrapClient, SecretProviderSession {
        private final KeyWrappingBackend backend = new KeyWrappingBackend() {
            @Override
            public WrappedKey wrap(
                    KeyReference keyReference,
                    byte[] plaintextKey,
                    CryptoContext context) {
                return new WrappedKey(plaintextKey, "TEST");
            }

            @Override
            public byte[] unwrap(
                    KeyReference keyReference,
                    WrappedKey wrappedKey,
                    CryptoContext context) {
                return wrappedKey.value();
            }
        };

        @Override
        public SecretProviderDescriptor descriptor() {
            return new SecretProviderDescriptor(
                    "test",
                    "kms",
                    Set.of(SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP));
        }

        @Override
        public Optional<SecretBackend> secretBackend() {
            return Optional.empty();
        }

        @Override
        public Optional<KeyWrappingBackend> keyWrappingBackend() {
            return Optional.of(backend);
        }

        @Override
        public SecretBootstrapResult load(SecretBootstrapRequest request) {
            return new SecretBootstrapResult("kms", "v1", "test", Instant.EPOCH, Map.of(), null);
        }

        @Override
        public void close() {
        }
    }
}
