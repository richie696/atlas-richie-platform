/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

/**
 * Provider 包装后的数据密钥及其非敏感元数据。
 */
public final class WrappedKey {
    private final byte[] value;
    private final String algorithm;

    public WrappedKey(byte[] value, String algorithm) {
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("wrapped key must not be empty");
        }
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be blank");
        }
        this.value = value.clone();
        this.algorithm = algorithm;
    }

    public byte[] value() {
        return value.clone();
    }

    public String algorithm() {
        return algorithm;
    }

    @Override
    public String toString() {
        return "WrappedKey[value=[PROTECTED], algorithm=" + algorithm + "]";
    }
}
