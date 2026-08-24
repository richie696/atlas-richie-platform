/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core.crypto;

import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.SigningBackend;
import cn.richie696.component.secret.api.crypto.SigningService;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;

import java.util.Objects;

/** Provider-neutral signing facade that owns logical-key mapping. */
public final class DefaultSigningService implements SigningService {
    private final SigningBackend backend;

    public DefaultSigningService(SigningBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
    }

    @Override
    public SignatureValue sign(String logicalKey, byte[] payload) {
        validate(logicalKey, payload);
        return backend.sign(
                new KeyReference(logicalKey, "current", KeyPurpose.SIGNING),
                payload,
                CryptoContext.empty());
    }

    @Override
    public boolean verify(String logicalKey, byte[] payload, SignatureValue signature) {
        validate(logicalKey, payload);
        Objects.requireNonNull(signature, "signature must not be null");
        return backend.verify(
                new KeyReference(logicalKey, "current", KeyPurpose.SIGNING),
                payload,
                signature,
                CryptoContext.empty());
    }

    private void validate(String logicalKey, byte[] payload) {
        if (logicalKey == null || logicalKey.isBlank()) {
            throw new SecretConfigurationException("SEC-KEY-001", "Signing logical key must not be blank");
        }
        if (payload == null || payload.length == 0) {
            throw new SecretConfigurationException("SEC-SIGN-001", "Signing payload must not be empty");
        }
    }
}
