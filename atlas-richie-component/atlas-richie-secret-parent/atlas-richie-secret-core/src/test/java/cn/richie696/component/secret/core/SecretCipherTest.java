/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SecretCipher;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.core.crypto.ArseEnvelopeCodec;
import cn.richie696.component.secret.core.crypto.DefaultEnvelopeCrypto;
import cn.richie696.component.secret.core.crypto.DefaultSecretCipher;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SecretCipherTest {

    @Test
    void projectFacadeNeedsNoProviderOrEnvelopeKnowledge() {
        SecretCipher cipher = new DefaultSecretCipher(
                new DefaultEnvelopeCrypto(new LogicalWrappingBackend()),
                new ArseEnvelopeCodec(),
                () -> context("record:42"));

        String encrypted = cipher.encrypt(
                "mfa-data-kek",
                "totp-secret".getBytes(StandardCharsets.UTF_8));
        String rewrapped = cipher.rewrap(encrypted, "mfa-data-kek-next");
        AtomicReference<byte[]> callbackBuffer = new AtomicReference<>();

        assertThat(encrypted).startsWith("arse:v1:").doesNotContain("totp-secret");
        String decrypted = cipher.decrypt(rewrapped, plaintext -> {
            callbackBuffer.set(plaintext);
            return new String(plaintext, StandardCharsets.UTF_8);
        });
        assertThat(decrypted).isEqualTo("totp-secret");
        assertThat(callbackBuffer.get()).containsOnly(0);
    }

    private CryptoContext context(String value) {
        return new CryptoContext(value.getBytes(StandardCharsets.UTF_8), java.util.Map.of());
    }

    private static final class LogicalWrappingBackend implements KeyWrappingBackend {
        @Override
        public WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context) {
            byte[] wrapped = plaintextKey.clone();
            byte mask = (byte) keyReference.logicalKey().hashCode();
            for (int index = 0; index < wrapped.length; index++) {
                wrapped[index] ^= mask;
            }
            return new WrappedKey(wrapped, "TEST_LOGICAL_WRAP");
        }

        @Override
        public byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context) {
            return wrap(keyReference, wrappedKey.value(), context).value();
        }
    }
}
