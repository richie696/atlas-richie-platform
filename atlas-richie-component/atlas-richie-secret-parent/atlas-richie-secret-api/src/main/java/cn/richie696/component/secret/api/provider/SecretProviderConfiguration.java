/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.api.provider;

import java.util.Map;

/**
 * Provider 运行期配置的不可变视图。
 */
public record SecretProviderConfiguration(String providerId, Map<String, Object> properties) {
    public SecretProviderConfiguration {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
