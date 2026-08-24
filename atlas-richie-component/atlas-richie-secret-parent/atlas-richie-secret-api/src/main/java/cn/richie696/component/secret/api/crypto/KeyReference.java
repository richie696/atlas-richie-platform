/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

import java.util.Objects;

/**
 * Provider 无关的逻辑密钥引用。
 *
 * @param logicalKey 应用侧逻辑 Key
 * @param version 逻辑 Key 版本或 Alias
 * @param purpose Key 用途
 */
public record KeyReference(
        String logicalKey,
        String version,
        KeyPurpose purpose) {

    public KeyReference {
        logicalKey = safeLogicalKey(logicalKey);
        version = required(version, "version");
        Objects.requireNonNull(purpose, "purpose must not be null");
    }

    public static KeyReference envelopeEncryption(String logicalKey) {
        return envelopeEncryption(logicalKey, "current");
    }

    public static KeyReference envelopeEncryption(String logicalKey, String version) {
        return new KeyReference(logicalKey, version, KeyPurpose.ENVELOPE_ENCRYPTION);
    }

    private static String safeLogicalKey(String value) {
        value = required(value, "logicalKey");
        if (value.contains("..")
                || value.indexOf('\0') >= 0
                || value.startsWith("/")
                || value.contains("://")) {
            throw new IllegalArgumentException("logicalKey must not contain a physical provider path");
        }
        return value;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
