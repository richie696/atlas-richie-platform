/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core.crypto;

import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.CipherEnvelope;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.EnvelopeCrypto;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretIntegrityException;
import cn.richie696.component.secret.core.DestroyableSecretValue;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AES-256-GCM + Provider Key Wrap 的默认信封加密实现。
 */
public final class DefaultEnvelopeCrypto implements EnvelopeCrypto {
    /** Current authenticated-AAD envelope format. */
    public static final int ENVELOPE_VERSION = 2;
    /** Legacy format retained for decrypt-only compatibility. */
    public static final int LEGACY_ENVELOPE_VERSION = 1;
    public static final String ALGORITHM = "AES_256_GCM";
    private static final int DATA_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] CONTENT_HEADER_AAD = "arse\0v2\0AES_256_GCM\0"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] LEGACY_CONTENT_HEADER_AAD = "arse\0v1\0AES_256_GCM\0"
            .getBytes(StandardCharsets.US_ASCII);

    private final KeyWrappingBackend keyWrappingBackend;
    private final SecureRandom secureRandom;

    public DefaultEnvelopeCrypto(KeyWrappingBackend keyWrappingBackend) {
        this(keyWrappingBackend, new SecureRandom());
    }

    DefaultEnvelopeCrypto(KeyWrappingBackend keyWrappingBackend, SecureRandom secureRandom) {
        this.keyWrappingBackend = Objects.requireNonNull(
                keyWrappingBackend,
                "keyWrappingBackend must not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
    }

    @Override
    public CipherEnvelope encrypt(
            KeyReference keyReference,
            SecretValue plaintext,
            CryptoContext context) {
        validateKeyPurpose(keyReference);
        Objects.requireNonNull(plaintext, "plaintext must not be null");
        context = context == null ? CryptoContext.empty() : context;
        byte[] dataKey = new byte[DATA_KEY_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        byte[] cleartext = plaintext.copyBytes();
        secureRandom.nextBytes(dataKey);
        secureRandom.nextBytes(nonce);
        try {
            CryptoContext envelopeContext = envelopeContext(
                    context, keyReference, ENVELOPE_VERSION, ALGORITHM, nonce);
            WrappedKey wrappedKey = keyWrappingBackend.wrap(keyReference, dataKey, envelopeContext);
            byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, dataKey, nonce, cleartext, envelopeContext);
            return new CipherEnvelope(
                    ENVELOPE_VERSION,
                    ALGORITHM,
                    keyReference,
                    wrappedKey,
                    nonce,
                    ciphertext);
        } catch (SecretCryptoException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Envelope encryption failed", exception);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
            Arrays.fill(cleartext, (byte) 0);
        }
    }

    @Override
    public SecretValue decrypt(CipherEnvelope envelope, CryptoContext context) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        validateEnvelope(envelope);
        context = context == null ? CryptoContext.empty() : context;
        CryptoContext envelopeContext = envelopeContext(
                context, envelope.keyReference(), envelope.version(), envelope.algorithm(), envelope.nonce());
        byte[] dataKey = keyWrappingBackend.unwrap(
                envelope.keyReference(),
                envelope.wrappedKey(),
                envelopeContext);
        try {
            if (dataKey == null || dataKey.length != DATA_KEY_BYTES) {
                throw new SecretCryptoException("SEC-CRYPTO-002", "Provider returned an invalid data key");
            }
            byte[] cleartext = crypt(
                    Cipher.DECRYPT_MODE,
                    dataKey,
                    envelope.nonce(),
                    envelope.ciphertext(),
                    envelopeContext);
            try {
                return DestroyableSecretValue.ofBytes(cleartext);
            } finally {
                Arrays.fill(cleartext, (byte) 0);
            }
        } finally {
            if (dataKey != null) {
                Arrays.fill(dataKey, (byte) 0);
            }
        }
    }

    @Override
    public CipherEnvelope rewrap(
            CipherEnvelope envelope,
            KeyReference targetKey,
            CryptoContext context) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        validateEnvelope(envelope);
        validateKeyPurpose(targetKey);
        context = context == null ? CryptoContext.empty() : context;
        byte[] targetNonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(targetNonce);
        CryptoContext sourceContext = envelopeContext(
                context, envelope.keyReference(), envelope.version(), envelope.algorithm(), envelope.nonce());
        byte[] dataKey = keyWrappingBackend.unwrap(
                envelope.keyReference(),
                envelope.wrappedKey(),
                sourceContext);
        try {
            if (dataKey == null || dataKey.length != DATA_KEY_BYTES) {
                throw new SecretCryptoException("SEC-CRYPTO-002", "Provider returned an invalid data key");
            }
            byte[] cleartext = crypt(
                    Cipher.DECRYPT_MODE,
                    dataKey,
                    envelope.nonce(),
                    envelope.ciphertext(),
                    sourceContext);
            try {
                CryptoContext targetContext = envelopeContext(
                        context, targetKey, ENVELOPE_VERSION, ALGORITHM, targetNonce);
                WrappedKey targetWrappedKey = keyWrappingBackend.wrap(targetKey, dataKey, targetContext);
                byte[] targetCiphertext = crypt(
                        Cipher.ENCRYPT_MODE,
                        dataKey,
                        targetNonce,
                        cleartext,
                        targetContext);
                return new CipherEnvelope(
                        ENVELOPE_VERSION,
                        ALGORITHM,
                        targetKey,
                        targetWrappedKey,
                        targetNonce,
                        targetCiphertext);
            } finally {
                Arrays.fill(cleartext, (byte) 0);
            }
        } finally {
            if (dataKey != null) {
                Arrays.fill(dataKey, (byte) 0);
            }
            Arrays.fill(targetNonce, (byte) 0);
        }
    }

    /**
     * Adds canonical envelope identity to the provider context. Providers can
     * use these attributes for KMS encryption-context binding while the same
     * identity is authenticated by the content AAD below.
     */
    private CryptoContext envelopeContext(
            CryptoContext context,
            KeyReference keyReference,
            int version,
            String algorithm,
            byte[] nonce) {
        if (version == LEGACY_ENVELOPE_VERSION) {
            return context;
        }
        Map<String, String> attributes = new LinkedHashMap<>(context.attributes());
        attributes.put("atlas.secret.key", keyReference.logicalKey());
        attributes.put("atlas.secret.version", keyReference.version());
        attributes.put("atlas.secret.purpose", keyReference.purpose().name());
        attributes.put("atlas.secret.envelope-version", Integer.toString(version));
        attributes.put("atlas.secret.algorithm", algorithm);
        attributes.put("atlas.secret.nonce-sha256", sha256(nonce));
        return new CryptoContext(context.associatedData(), attributes);
    }

    private byte[] canonicalAssociatedData(CryptoContext context) {
        byte[] contextData = context.associatedData();
        try {
            if (!context.attributes().containsKey("atlas.secret.envelope-version")) {
                byte[] legacy = new byte[LEGACY_CONTENT_HEADER_AAD.length + contextData.length];
                System.arraycopy(LEGACY_CONTENT_HEADER_AAD, 0, legacy, 0, LEGACY_CONTENT_HEADER_AAD.length);
                System.arraycopy(contextData, 0, legacy, LEGACY_CONTENT_HEADER_AAD.length, contextData.length);
                return legacy;
            }
            String identity = "key=" + requiredAttribute(context, "atlas.secret.key")
                    + "\0version=" + requiredAttribute(context, "atlas.secret.version")
                    + "\0purpose=" + requiredAttribute(context, "atlas.secret.purpose")
                    + "\0envelope-version=" + requiredAttribute(context, "atlas.secret.envelope-version")
                    + "\0algorithm=" + requiredAttribute(context, "atlas.secret.algorithm")
                    + "\0nonce-sha256=" + requiredAttribute(context, "atlas.secret.nonce-sha256");
            byte[] identityData = identity.getBytes(StandardCharsets.UTF_8);
            byte[] associatedData = new byte[CONTENT_HEADER_AAD.length + contextData.length + 1 + identityData.length];
            System.arraycopy(CONTENT_HEADER_AAD, 0, associatedData, 0, CONTENT_HEADER_AAD.length);
            System.arraycopy(contextData, 0, associatedData, CONTENT_HEADER_AAD.length, contextData.length);
            associatedData[CONTENT_HEADER_AAD.length + contextData.length] = 0;
            System.arraycopy(identityData, 0, associatedData,
                    CONTENT_HEADER_AAD.length + contextData.length + 1, identityData.length);
            Arrays.fill(identityData, (byte) 0);
            return associatedData;
        } finally {
            Arrays.fill(contextData, (byte) 0);
        }
    }

    private String requiredAttribute(CryptoContext context, String name) {
        String value = context.attributes().get(name);
        if (value == null || value.isBlank()) {
            throw new SecretIntegrityException("Envelope context is missing authenticated " + name);
        }
        return value;
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private byte[] crypt(
            int mode,
            byte[] dataKey,
            byte[] nonce,
            byte[] input,
            CryptoContext context) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(dataKey, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] associatedData = canonicalAssociatedData(context);
            try {
                cipher.updateAAD(associatedData);
            } finally {
                Arrays.fill(associatedData, (byte) 0);
            }
            return cipher.doFinal(input);
        } catch (AEADBadTagException exception) {
            throw new SecretIntegrityException("Envelope integrity or AAD validation failed", exception);
        } catch (GeneralSecurityException exception) {
            String code = mode == Cipher.ENCRYPT_MODE ? "SEC-CRYPTO-001" : "SEC-CRYPTO-002";
            throw new SecretCryptoException(code, "Envelope cryptographic operation failed", exception);
        }
    }

    private void validateEnvelope(CipherEnvelope envelope) {
        if ((envelope.version() != LEGACY_ENVELOPE_VERSION && envelope.version() != ENVELOPE_VERSION)
                || !ALGORITHM.equals(envelope.algorithm())) {
            throw new SecretIntegrityException("Unsupported or inconsistent CipherEnvelope format");
        }
        validateKeyPurpose(envelope.keyReference());
        if (envelope.nonce().length != NONCE_BYTES) {
            throw new SecretIntegrityException("Invalid CipherEnvelope nonce length");
        }
    }

    private void validateKeyPurpose(KeyReference keyReference) {
        Objects.requireNonNull(keyReference, "keyReference must not be null");
        if (keyReference.purpose() != KeyPurpose.ENVELOPE_ENCRYPTION) {
            throw new SecretCryptoException("SEC-CAP-001", "Key is not authorized for envelope encryption");
        }
    }
}
