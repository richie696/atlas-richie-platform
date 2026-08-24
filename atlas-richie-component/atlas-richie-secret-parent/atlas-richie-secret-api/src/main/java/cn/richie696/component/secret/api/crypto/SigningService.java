/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

/** Minimal runtime signing facade for provider-managed private keys. */
public interface SigningService {

    SignatureValue sign(String logicalKey, byte[] payload);

    boolean verify(String logicalKey, byte[] payload, SignatureValue signature);
}
