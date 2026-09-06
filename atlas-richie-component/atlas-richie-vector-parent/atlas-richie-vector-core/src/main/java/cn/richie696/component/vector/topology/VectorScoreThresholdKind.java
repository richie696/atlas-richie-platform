/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

/** Meaning of a {@code minScore} threshold supplied to a Store-bound search. */
public enum VectorScoreThresholdKind {

    /**
     * Default. The threshold is compared against a {@code [0, 1]} higher-is-better normalized
     * relevance score produced by the adapter.
     */
    NORMALIZED_RELEVANCE,

    /**
     * The threshold is compared against the raw, post-recall provider score (e.g. inner product
     * value, raw cosine, raw distance). Range and direction follow the Store's
     * {@link VectorScoreDescriptor} and are NOT forced to {@code [0, 1]}.
     */
    PROVIDER_RAW
}
