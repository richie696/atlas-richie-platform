/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

import cn.richie696.component.secret.api.SecretValue;

/**
 * Provider 无关的信封加密入口。
 */
public interface EnvelopeCrypto {

    CipherEnvelope encrypt(KeyReference keyReference, SecretValue plaintext, CryptoContext context);

    SecretValue decrypt(CipherEnvelope envelope, CryptoContext context);

    CipherEnvelope rewrap(CipherEnvelope envelope, KeyReference targetKey, CryptoContext context);
}
