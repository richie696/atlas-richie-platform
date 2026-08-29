/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.provider;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Provider 运行期配置的不可变视图。
 */
public record SecretProviderConfiguration(String providerId, Map<String, Object> properties) {
    public SecretProviderConfiguration {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        properties = copyProperties(properties);
    }

    @Override
    public Map<String, Object> properties() {
        return copyProperties(properties);
    }

    @Override
    public String toString() {
        return "SecretProviderConfiguration[providerId=" + providerId + ", properties=[PROTECTED]]";
    }

    private static Map<String, Object> copyProperties(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value instanceof byte[] bytes
                ? bytes.clone() : value instanceof char[] chars ? chars.clone() : value));
        return Map.copyOf(copy);
    }
}
