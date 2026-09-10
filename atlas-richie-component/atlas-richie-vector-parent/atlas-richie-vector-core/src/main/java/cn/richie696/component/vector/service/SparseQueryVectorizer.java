/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service;

import java.util.Map;

/**
 * Backward-compatible sparse query encoder for providers whose native hybrid API
 * requires sparse coordinates rather than a server-side BM25 text function.
 *
 * <p>Implementations are selected by bean name from a provider Store's advanced configuration.
 * They must produce the same token-id / weight space as the sparse vectors written to that
 * Store. This is intentionally not part of the mandatory {@code EmbeddingModel} contract:
 * ordinary dense-only projects do not need to configure it.</p>
 */
@FunctionalInterface
@Deprecated(forRemoval = false)
public interface SparseQueryVectorizer {

    /** Returns non-empty sparse coordinates for one query; keys are non-negative token IDs. */
    Map<Long, Float> vectorize(String query);
}
