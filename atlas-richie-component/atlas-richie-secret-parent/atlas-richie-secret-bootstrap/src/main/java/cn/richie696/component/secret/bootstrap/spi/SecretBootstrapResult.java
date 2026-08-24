/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.spi;

import java.time.Instant;
import java.util.Map;

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
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
