/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

import java.util.Objects;

/**
 * 版本化的信封密文对象。数组访问器始终返回副本。
 */
public final class CipherEnvelope {
    private final int version;
    private final String algorithm;
    private final KeyReference keyReference;
    private final WrappedKey wrappedKey;
    private final byte[] nonce;
    private final byte[] ciphertext;

    public CipherEnvelope(
            int version,
            String algorithm,
            KeyReference keyReference,
            WrappedKey wrappedKey,
            byte[] nonce,
            byte[] ciphertext) {
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        if (nonce == null || nonce.length == 0 || ciphertext == null || ciphertext.length == 0) {
            throw new IllegalArgumentException("nonce and ciphertext must not be empty");
        }
        this.version = version;
        this.algorithm = algorithm;
        this.keyReference = Objects.requireNonNull(keyReference, "keyReference must not be null");
        this.wrappedKey = Objects.requireNonNull(wrappedKey, "wrappedKey must not be null");
        this.nonce = nonce.clone();
        this.ciphertext = ciphertext.clone();
    }

    public int version() {
        return version;
    }

    public String algorithm() {
        return algorithm;
    }

    public KeyReference keyReference() {
        return keyReference;
    }

    public WrappedKey wrappedKey() {
        return wrappedKey;
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public String toString() {
        return "CipherEnvelope[version=" + version + ", algorithm=" + algorithm
                + ", keyReference=" + keyReference.logicalKey() + ", ciphertext=[PROTECTED]]";
    }
}
