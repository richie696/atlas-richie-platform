/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.exception;

/**
 * Secret 密码运算失败。
 */
public class SecretCryptoException extends SecretException {
    public SecretCryptoException(String errorCode, String message) {
        super(errorCode, message);
    }

    public SecretCryptoException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
