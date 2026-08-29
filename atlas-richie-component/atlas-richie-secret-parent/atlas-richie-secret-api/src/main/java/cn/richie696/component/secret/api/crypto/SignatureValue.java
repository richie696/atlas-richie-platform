/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

/**
 * Provider-neutral, opaque signature value.
 *
 * <p>Provider-specific key versions and algorithm parameters remain inside
 * the encoded value so verification remains correct after key rotation.</p>
 */
public record SignatureValue(String value) {
    private static final int MAX_SERIALIZED_LENGTH = 64 * 1024;

    public SignatureValue {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("signature value must not be blank");
        }
        if (value.length() > MAX_SERIALIZED_LENGTH) {
            throw new IllegalArgumentException("signature value exceeds the maximum length");
        }
    }

    @Override
    public String toString() {
        return "SignatureValue[value=[PROTECTED]]";
    }
}
