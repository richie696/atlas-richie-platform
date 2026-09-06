/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.diagnostics;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.topology.VectorStoreId;

import java.util.Objects;

/** Low-cardinality, payload-free health snapshot for one logical Store. */
public record VectorStoreHealthSnapshot(
        VectorStoreId storeId,
        VectorProvider provider,
        boolean required,
        VectorStoreHealthStatus status,
        VectorErrorCategory errorCategory,
        int checkedIndexCount) {

    public VectorStoreHealthSnapshot {
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(errorCategory, "errorCategory must not be null");
        if (checkedIndexCount < 0) {
            throw new IllegalArgumentException("checkedIndexCount must not be negative");
        }
    }
}
