/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.starter;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SecretCipher;
import cn.richie696.component.secret.api.crypto.WrappedKey;
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
}
