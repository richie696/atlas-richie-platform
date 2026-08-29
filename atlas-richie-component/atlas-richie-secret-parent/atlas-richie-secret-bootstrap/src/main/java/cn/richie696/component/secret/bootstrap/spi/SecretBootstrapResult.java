/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.spi;

import java.time.Instant;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Provider 返回的不可变 Secret Bundle 快照。
 */
public record SecretBootstrapResult(
        String providerId,
        String version,
        String logicalPath,
        Instant loadedAt,
        Map<String, Object> values,
        String requestId) {

    public SecretBootstrapResult {
        if (providerId == null || providerId.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("providerId and version must not be blank");
        }
        loadedAt = loadedAt == null ? Instant.now() : loadedAt;
        values = copyValues(values);
    }

    @Override
    public Map<String, Object> values() {
        return copyValues(values);
    }

    @Override
    public String toString() {
        return "SecretBootstrapResult[providerId=" + providerId + ", version=" + version
                + ", logicalPath=" + logicalPath + ", loadedAt=" + loadedAt
                + ", values=[PROTECTED], requestId=" + requestId + "]";
    }

    private static Map<String, Object> copyValues(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyValue(value)));
        return Map.copyOf(copy);
    }

    private static Object copyValue(Object value) {
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof char[] chars) return chars.clone();
        return value;
    }
}
