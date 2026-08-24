/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.exception;

/**
 * 启动早期 Secret 加载失败。
 */
public class SecretBootstrapException extends SecretException {
    public SecretBootstrapException(String errorCode, String message) {
        super(errorCode, message);
    }

    public SecretBootstrapException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
