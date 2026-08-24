/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.exception;

/**
 * Secret 体系的受控异常基类。
 */
public class SecretException extends RuntimeException {
    private final String errorCode;

    public SecretException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SecretException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
