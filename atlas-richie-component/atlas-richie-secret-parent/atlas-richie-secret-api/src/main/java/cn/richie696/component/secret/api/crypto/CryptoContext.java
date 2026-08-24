/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.crypto;

import java.util.Arrays;
import java.util.Map;

/**
 * 密码运算上下文。AAD 由受信任字段规范化后传入，不允许包含 Secret。
 */
public final class CryptoContext {
    private final byte[] associatedData;
    private final Map<String, String> attributes;

    public CryptoContext(byte[] associatedData, Map<String, String> attributes) {
        this.associatedData = associatedData == null ? new byte[0] : associatedData.clone();
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static CryptoContext empty() {
        return new CryptoContext(new byte[0], Map.of());
    }

    public byte[] associatedData() {
        return associatedData.clone();
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    @Override
    public String toString() {
        return "CryptoContext[associatedData=[PROTECTED], attributes=" + attributes.keySet() + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CryptoContext that)) {
            return false;
        }
        return Arrays.equals(associatedData, that.associatedData) && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(associatedData) + attributes.hashCode();
    }
}
