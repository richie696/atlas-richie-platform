/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.model.VectorSearchResult;

import java.util.List;
import java.util.Objects;

public record VectorSearchExecution(
        List<VectorSearchResult> results,
        VectorSearchExecutionReceipt receipt) {

    public VectorSearchExecution {
        results = List.copyOf(results == null ? List.of() : results);
        Objects.requireNonNull(receipt, "receipt must not be null");
    }
}
