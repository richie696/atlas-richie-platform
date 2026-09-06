/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Objects;

/** Redacted optional-store degradation record. */
public record VectorStoreStartupFailure(
        VectorStoreId storeId,
        VectorStoreStartupPhase phase,
        String failureType) {

    public VectorStoreStartupFailure {
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        if (failureType == null || failureType.isBlank()) {
            throw new IllegalArgumentException("failureType must not be blank");
        }
    }
}
