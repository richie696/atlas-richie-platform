/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.exception;

/**
 * 密文、AAD 或版本完整性校验失败。
 */
public class SecretIntegrityException extends SecretCryptoException {
    public SecretIntegrityException(String message, Throwable cause) {
        super("SEC-CRYPTO-003", message, cause);
    }

    public SecretIntegrityException(String message) {
        super("SEC-CRYPTO-003", message);
    }
}
