/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

/** Provider SPI for non-exportable signing keys. */
public interface SigningBackend {

    SignatureValue sign(KeyReference keyReference, byte[] payload, CryptoContext context);

    boolean verify(KeyReference keyReference, byte[] payload, SignatureValue signature, CryptoContext context);
}
