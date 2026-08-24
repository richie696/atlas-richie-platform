/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.SecretCallback;
import cn.richie696.component.secret.api.SecretOperations;
import cn.richie696.component.secret.api.SecretResolver;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.crypto.SecretCipher;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.SigningService;

import java.util.Arrays;
import java.util.Objects;

/**
 * 组合 Secret 读取与密码能力，并内聚临时明文生命周期。
 */
public final class DefaultSecretOperations implements SecretOperations {
    private final SecretResolver resolver;
    private final SecretCipher cipher;
    private final SigningService signingService;

    public DefaultSecretOperations(SecretResolver resolver, SecretCipher cipher) {
        this(resolver, cipher, null);
    }

    public DefaultSecretOperations(
            SecretResolver resolver,
            SecretCipher cipher,
            SigningService signingService) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
        this.signingService = signingService;
    }

    @Override
    public <T> T read(String logicalName, SecretCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        try (SecretValue value = resolver.resolve(logicalName)) {
            byte[] plaintext = value.copyBytes();
            try {
                return callback.apply(plaintext);
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    @Override
    public String encrypt(String logicalKey, byte[] plaintext) {
        return cipher.encrypt(logicalKey, plaintext);
    }

    @Override
    public <T> T decrypt(String ciphertext, SecretCallback<T> callback) {
        return cipher.decrypt(ciphertext, callback);
    }

    @Override
    public SignatureValue sign(String logicalKey, byte[] payload) {
        if (signingService == null) {
            return SecretOperations.super.sign(logicalKey, payload);
        }
        return signingService.sign(logicalKey, payload);
    }

    @Override
    public boolean verify(String logicalKey, byte[] payload, SignatureValue signature) {
        if (signingService == null) {
            return SecretOperations.super.verify(logicalKey, payload, signature);
        }
        return signingService.verify(logicalKey, payload, signature);
    }

}
