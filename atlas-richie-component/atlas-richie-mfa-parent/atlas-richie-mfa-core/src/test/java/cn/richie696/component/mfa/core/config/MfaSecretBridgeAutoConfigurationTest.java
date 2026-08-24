/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.mfa.core.config;

import cn.richie696.component.mfa.core.crypto.KeyManagementProvider;
import cn.richie696.component.mfa.core.crypto.MfaKeyManagementBridge;
import cn.richie696.component.secret.api.SecretCallback;
import cn.richie696.component.secret.api.SecretOperations;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MfaSecretBridgeAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MfaSecretBridgeAutoConfiguration.class))
            .withBean(SecretOperations.class, NoOpSecretOperations::new);

    @Test
    void disabledDoesNotCreateBridge() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(KeyManagementProvider.class));
    }

    @Test
    void enabledCreatesOnlyTheMfaCompatibilityFacade() {
        contextRunner
                .withPropertyValues("platform.component.secret.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(KeyManagementProvider.class);
                    assertThat(context.getBean(KeyManagementProvider.class))
                            .isInstanceOf(MfaKeyManagementBridge.class);
                });
    }

    private static final class NoOpSecretOperations implements SecretOperations {
        @Override
        public <T> T read(String logicalName, SecretCallback<T> callback) {
            return null;
        }

        @Override
        public String encrypt(String logicalKey, byte[] plaintext) {
            return "arse:v1:test";
        }

        @Override
        public <T> T decrypt(String ciphertext, SecretCallback<T> callback) {
            return null;
        }
    }
}
