/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretResolver;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.SecretCipher;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.SigningService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretOperationsTest {

    @Test
    void singleFacadeHidesValueLifecycleAndLowLevelCryptoTypes() {
        AtomicReference<byte[]> callbackBuffer = new AtomicReference<>();
        SecretOperations operations = new DefaultSecretOperations(new FixedResolver(), new PassThroughCipher());

        String value = operations.read("ai.primary.api-key", plaintext -> {
            callbackBuffer.set(plaintext);
            return new String(plaintext, StandardCharsets.UTF_8);
        });

        assertThat(value).isEqualTo("project-secret");
        assertThat(callbackBuffer.get()).containsOnly(0);
        assertThat(operations.encrypt("data-kek", new byte[]{1, 2})).isEqualTo("ciphertext");
    }

    @Test
    void callbackFailureStillClearsTemporaryPlaintext() {
        AtomicReference<byte[]> callbackBuffer = new AtomicReference<>();
        SecretOperations operations = new DefaultSecretOperations(new FixedResolver(), new PassThroughCipher());

        assertThatThrownBy(() -> operations.read("ai.primary.api-key", plaintext -> {
            callbackBuffer.set(plaintext);
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(callbackBuffer.get()).containsOnly(0);
    }

    @Test
    void facadeDelegatesSigningWithoutExposingBackendTypes() {
        SigningService signer = new SigningService() {
            @Override
            public SignatureValue sign(String logicalKey, byte[] payload) {
                return new SignatureValue(logicalKey + ":signature");
            }

            @Override
            public boolean verify(String logicalKey, byte[] payload, SignatureValue signature) {
                return signature.value().equals(logicalKey + ":signature");
            }
        };
        SecretOperations operations = new DefaultSecretOperations(
                new FixedResolver(), new PassThroughCipher(), signer);

        SignatureValue signature = operations.sign("oauth.signing", new byte[]{1});

        assertThat(signature.value()).isEqualTo("oauth.signing:signature");
        assertThat(operations.verify("oauth.signing", new byte[]{1}, signature)).isTrue();
    }

    private static final class FixedResolver implements SecretResolver {
        @Override
        public SecretValue resolve(SecretReference reference) {
            return DestroyableSecretValue.ofChars("project-secret".toCharArray());
        }

        @Override
        public SecretMetadata metadata(SecretReference reference) {
            return new SecretMetadata("v1", Instant.EPOCH, null, Map.of());
        }
    }

    private static final class PassThroughCipher implements SecretCipher {
        @Override
        public String encrypt(String logicalKey, byte[] plaintext) {
            return "ciphertext";
        }

        @Override
        public <T> T decrypt(String ciphertext, cn.richie696.component.secret.api.SecretCallback<T> callback) {
            return callback.apply(new byte[]{1});
        }

        @Override
        public String rewrap(String ciphertext, String targetLogicalKey) {
            return ciphertext;
        }
    }
}
