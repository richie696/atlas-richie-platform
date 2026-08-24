/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.testkit;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.core.DestroyableSecretValue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider 契约测试使用的内存 SecretBackend。
 */
public final class MapSecretBackend implements SecretBackend, AutoCloseable {
    private final Map<String, byte[]> values = new LinkedHashMap<>();

    public synchronized void put(String logicalName, byte[] value) {
        if (logicalName == null || logicalName.isBlank() || value == null) {
            throw new IllegalArgumentException("logicalName and value are required");
        }
        byte[] previous = values.put(logicalName, value.clone());
        if (previous != null) {
            java.util.Arrays.fill(previous, (byte) 0);
        }
    }

    @Override
    public synchronized SecretValue read(SecretReference reference) {
        byte[] value = values.get(reference.logicalName());
        if (value == null) {
            throw new SecretException("SEC-STORE-001", "Test Secret was not found");
        }
        return DestroyableSecretValue.ofBytes(value);
    }

    @Override
    public synchronized SecretMetadata metadata(SecretReference reference) {
        if (!values.containsKey(reference.logicalName())) {
            throw new SecretException("SEC-STORE-001", "Test Secret was not found");
        }
        return new SecretMetadata("test-v1", Instant.EPOCH, null, Map.of("backend", "memory"));
    }

    @Override
    public synchronized void close() {
        values.values().forEach(value -> java.util.Arrays.fill(value, (byte) 0));
        values.clear();
    }
}
