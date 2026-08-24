/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.CipherEnvelope;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretIntegrityException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.core.crypto.ArseEnvelopeCodec;
import cn.richie696.component.secret.core.crypto.DefaultEnvelopeCrypto;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeCryptoTest {
    private static final KeyReference SOURCE_KEY = new KeyReference(
            "source-key",
            "v1",
            KeyPurpose.ENVELOPE_ENCRYPTION);
    private static final KeyReference TARGET_KEY = new KeyReference(
            "target-key",
            "v2",
            KeyPurpose.ENVELOPE_ENCRYPTION);

    private final DefaultEnvelopeCrypto crypto = new DefaultEnvelopeCrypto(new ReversibleTestWrappingBackend());
    private final ArseEnvelopeCodec codec = new ArseEnvelopeCodec();

    @Test
    void encryptsSerializesAndDecryptsWithoutExposingPlaintext() {
        CryptoContext context = context("tenant:42");
        CipherEnvelope envelope;
        try (SecretValue plaintext = DestroyableSecretValue.ofChars("high-value-secret".toCharArray())) {
            envelope = crypto.encrypt(SOURCE_KEY, plaintext, context);
        }

        String encoded = codec.encodeToString(envelope);
        assertThat(encoded).startsWith("arse:v1:").doesNotContain("high-value-secret");

        CipherEnvelope decoded = codec.decode(encoded);
        try (SecretValue decrypted = crypto.decrypt(decoded, context)) {
            assertThat(decrypted.copyChars()).containsExactly("high-value-secret".toCharArray());
        }
    }

    @Test
    void rejectsWrongAssociatedDataAndTamperedCiphertext() {
        CipherEnvelope envelope;
        try (SecretValue plaintext = DestroyableSecretValue.ofChars("secret".toCharArray())) {
            envelope = crypto.encrypt(SOURCE_KEY, plaintext, context("tenant:1"));
        }

        assertThatThrownBy(() -> crypto.decrypt(envelope, context("tenant:2")))
                .isInstanceOf(SecretIntegrityException.class);

        byte[] tampered = envelope.ciphertext();
        tampered[0] ^= 1;
        CipherEnvelope corrupted = new CipherEnvelope(
                envelope.version(),
                envelope.algorithm(),
                envelope.keyReference(),
                envelope.wrappedKey(),
                envelope.nonce(),
                tampered);
        assertThatThrownBy(() -> crypto.decrypt(corrupted, context("tenant:1")))
                .isInstanceOf(SecretIntegrityException.class);
    }

    @Test
    void rejectsLogicalKeyReplacementWhenPhysicalWrappingKeyIsShared() {
        DefaultEnvelopeCrypto sharedKeyCrypto = new DefaultEnvelopeCrypto(new SharedWrappingBackend());
        CipherEnvelope envelope;
        try (SecretValue plaintext = DestroyableSecretValue.ofChars("secret".toCharArray())) {
            envelope = sharedKeyCrypto.encrypt(SOURCE_KEY, plaintext, context("tenant:1"));
        }

        CipherEnvelope replaced = new CipherEnvelope(
                envelope.version(), envelope.algorithm(), TARGET_KEY,
                envelope.wrappedKey(), envelope.nonce(), envelope.ciphertext());
        assertThatThrownBy(() -> sharedKeyCrypto.decrypt(replaced, context("tenant:1")))
                .isInstanceOf(SecretIntegrityException.class);
    }

    @Test
    void rewrapsDataKeyAndRebindsAuthenticatedEnvelopeIdentity() {
        CryptoContext context = context("record:99");
        CipherEnvelope envelope;
        try (SecretValue plaintext = DestroyableSecretValue.ofChars("payload".toCharArray())) {
            envelope = crypto.encrypt(SOURCE_KEY, plaintext, context);
        }

        CipherEnvelope rewrapped = crypto.rewrap(envelope, TARGET_KEY, context);

        assertThat(rewrapped.ciphertext()).isNotEqualTo(envelope.ciphertext());
        assertThat(rewrapped.nonce()).isNotEqualTo(envelope.nonce());
        assertThat(rewrapped.keyReference()).isEqualTo(TARGET_KEY);
        try (SecretValue decrypted = crypto.decrypt(rewrapped, context)) {
            assertThat(decrypted.copyChars()).containsExactly("payload".toCharArray());
        }
    }

    @Test
    void rejectsMalformedEnvelopeBeforeAllocation() {
        assertThatThrownBy(() -> codec.decode("arse:v1:AAAA"))
                .isInstanceOf(SecretIntegrityException.class);

        CipherEnvelope envelope;
        try (SecretValue plaintext = DestroyableSecretValue.ofChars("secret".toCharArray())) {
            envelope = crypto.encrypt(SOURCE_KEY, plaintext, CryptoContext.empty());
        }
        byte[] unsupportedVersion = codec.encode(envelope);
        unsupportedVersion[4] = 3;
        assertThatThrownBy(() -> codec.decode(unsupportedVersion))
                .isInstanceOf(SecretIntegrityException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsNullDataKeyReturnedByProviderWithoutMaskingTheError() {
        CipherEnvelope envelope;
        try (SecretValue plaintext = DestroyableSecretValue.ofChars("secret".toCharArray())) {
            envelope = crypto.encrypt(SOURCE_KEY, plaintext, CryptoContext.empty());
        }
        DefaultEnvelopeCrypto invalidProviderCrypto = new DefaultEnvelopeCrypto(new KeyWrappingBackend() {
            @Override
            public WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context) {
                return new WrappedKey(plaintextKey, "TEST");
            }

            @Override
            public byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context) {
                return null;
            }
        });

        assertThatThrownBy(() -> invalidProviderCrypto.decrypt(envelope, CryptoContext.empty()))
                .isInstanceOf(SecretCryptoException.class)
                .hasMessageContaining("invalid data key");
    }

    private CryptoContext context(String value) {
        return new CryptoContext(value.getBytes(StandardCharsets.UTF_8), Map.of("schema", "test-v1"));
    }

    private static final class ReversibleTestWrappingBackend implements KeyWrappingBackend {
        @Override
        public WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context) {
            byte[] mask = mask(keyReference);
            byte[] wrapped = plaintextKey.clone();
            for (int index = 0; index < wrapped.length; index++) {
                wrapped[index] ^= mask[index % mask.length];
            }
            return new WrappedKey(wrapped, "TEST_XOR");
        }

        @Override
        public byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context) {
            return wrap(keyReference, wrappedKey.value(), context).value();
        }

        private byte[] mask(KeyReference keyReference) {
            byte[] source = (keyReference.logicalKey() + ":" + keyReference.version())
                    .getBytes(StandardCharsets.UTF_8);
            return Arrays.copyOf(source, Math.max(source.length, 1));
        }
    }

    private static final class SharedWrappingBackend implements KeyWrappingBackend {
        @Override
        public WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context) {
            return new WrappedKey(plaintextKey, "TEST_SHARED");
        }

        @Override
        public byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context) {
            return wrappedKey.value();
        }
    }
}
