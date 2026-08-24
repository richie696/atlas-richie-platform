/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core.crypto;

import cn.richie696.component.secret.api.SecretCallback;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.CipherEnvelope;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.EnvelopeCrypto;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.SecretCipher;
import cn.richie696.component.secret.core.DestroyableSecretValue;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 隐藏 KeyReference、CipherEnvelope 与编解码细节的业务门面。
 */
public final class DefaultSecretCipher implements SecretCipher {
    private final EnvelopeCrypto envelopeCrypto;
    private final ArseEnvelopeCodec envelopeCodec;
    private final Supplier<CryptoContext> contextSupplier;

    public DefaultSecretCipher(EnvelopeCrypto envelopeCrypto, ArseEnvelopeCodec envelopeCodec) {
        this(envelopeCrypto, envelopeCodec, CryptoContext::empty);
    }

    public DefaultSecretCipher(
            EnvelopeCrypto envelopeCrypto,
            ArseEnvelopeCodec envelopeCodec,
            Supplier<CryptoContext> contextSupplier) {
        this.envelopeCrypto = Objects.requireNonNull(envelopeCrypto, "envelopeCrypto must not be null");
        this.envelopeCodec = Objects.requireNonNull(envelopeCodec, "envelopeCodec must not be null");
        this.contextSupplier = Objects.requireNonNull(contextSupplier, "contextSupplier must not be null");
    }

    @Override
    public String encrypt(String logicalKey, byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        try (SecretValue value = DestroyableSecretValue.ofBytes(plaintext)) {
            CipherEnvelope envelope = envelopeCrypto.encrypt(
                    KeyReference.envelopeEncryption(logicalKey),
                    value,
                    context());
            return envelopeCodec.encodeToString(envelope);
        }
    }

    @Override
    public <T> T decrypt(String ciphertext, SecretCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        try (SecretValue value = envelopeCrypto.decrypt(envelopeCodec.decode(ciphertext), context())) {
            byte[] plaintext = value.copyBytes();
            try {
                return callback.apply(plaintext);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    @Override
    public String rewrap(String ciphertext, String targetLogicalKey) {
        CipherEnvelope rewrapped = envelopeCrypto.rewrap(
                envelopeCodec.decode(ciphertext),
                KeyReference.envelopeEncryption(targetLogicalKey),
                context());
        return envelopeCodec.encodeToString(rewrapped);
    }

    private CryptoContext context() {
        CryptoContext context = contextSupplier.get();
        return context == null ? CryptoContext.empty() : context;
    }
}
