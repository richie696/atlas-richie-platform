/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

/**
 * Provider 的 Key Wrap/Unwrap 能力。
 */
public interface KeyWrappingBackend {

    WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context);

    byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context);
}
