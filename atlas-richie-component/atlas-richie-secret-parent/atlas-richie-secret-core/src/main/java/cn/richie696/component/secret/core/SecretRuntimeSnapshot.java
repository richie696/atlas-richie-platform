/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.SecretValue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行期不可变 Secret 快照，拥有其中 SecretValue 的生命周期。
 */
public final class SecretRuntimeSnapshot implements AutoCloseable {
    private final String providerId;
    private final String version;
    private final Instant loadedAt;
    private final Map<String, SecretValue> values;

    public SecretRuntimeSnapshot(
            String providerId,
            String version,
            Instant loadedAt,
            Map<String, SecretValue> values) {
        if (providerId == null || providerId.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("providerId and version must not be blank");
        }
        this.providerId = providerId;
        this.version = version;
        this.loadedAt = loadedAt == null ? Instant.now() : loadedAt;
        this.values = Map.copyOf(new LinkedHashMap<>(values));
    }

    public String providerId() {
        return providerId;
    }

    public String version() {
        return version;
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public Map<String, SecretValue> values() {
        return values;
    }

    @Override
    public void close() {
        values.values().forEach(SecretValue::close);
    }

    @Override
    public String toString() {
        return "SecretRuntimeSnapshot[providerId=" + providerId + ", version=" + version
                + ", loadedAt=" + loadedAt + ", values=[PROTECTED]]";
    }
}
