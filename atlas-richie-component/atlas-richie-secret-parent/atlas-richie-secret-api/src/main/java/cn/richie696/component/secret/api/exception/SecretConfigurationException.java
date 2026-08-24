/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.exception;

/**
 * Secret 配置或 Binding Catalog 非法。
 */
public class SecretConfigurationException extends SecretException {
    public SecretConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public SecretConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
