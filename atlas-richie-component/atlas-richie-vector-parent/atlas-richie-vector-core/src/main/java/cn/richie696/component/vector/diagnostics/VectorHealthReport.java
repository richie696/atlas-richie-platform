/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.diagnostics;

import java.util.List;
import java.util.Objects;

/** Immutable aggregate plus independent Store snapshots. */
public record VectorHealthReport(VectorStoreHealthStatus status, List<VectorStoreHealthSnapshot> stores) {

    public VectorHealthReport {
        Objects.requireNonNull(status, "status must not be null");
        stores = List.copyOf(stores);
    }
}
