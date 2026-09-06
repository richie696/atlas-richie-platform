/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Objects;

/**
 * Immutable semantics for one score stage exposed by a Store.
 *
 * <p>{@link #thresholdKind} and {@link #thresholdExecution} together describe how a
 * caller-supplied {@code minScore} is interpreted and enforced. They MUST be consistent
 * with {@link #normalized} and with the actual code path that applies the threshold; the
 * {@code framework-result} fields in the {@code VectorSearchExecutionReceipt} are the
 * runtime evidence.</p>
 */
public record VectorScoreDescriptor(
        VectorScoreStage stage,
        String source,
        VectorScoreDirection direction,
        Double minimum,
        Double maximum,
        boolean normalized,
        boolean comparableAcrossQueries,
        String transformation,
        VectorScoreThresholdKind thresholdKind,
        VectorScoreThresholdExecution thresholdExecution) {

    public VectorScoreDescriptor {
        Objects.requireNonNull(stage, "vector score stage must not be null");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("vector score source must not be blank");
        }
        Objects.requireNonNull(direction, "vector score direction must not be null");
        if (minimum != null && (!Double.isFinite(minimum))) {
            throw new IllegalArgumentException("vector score minimum must be finite");
        }
        if (maximum != null && (!Double.isFinite(maximum))) {
            throw new IllegalArgumentException("vector score maximum must be finite");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("vector score minimum must not exceed maximum");
        }
        if (normalized && (minimum == null || maximum == null)) {
            throw new IllegalArgumentException("normalized vector score requires a declared range");
        }
        if (transformation == null || transformation.isBlank()) {
            throw new IllegalArgumentException("vector score transformation must not be blank");
        }
        if (thresholdKind == null) {
            thresholdKind = normalized ? VectorScoreThresholdKind.NORMALIZED_RELEVANCE
                    : VectorScoreThresholdKind.PROVIDER_RAW;
        }
        if (thresholdExecution == null) {
            thresholdExecution = normalized ? VectorScoreThresholdExecution.ADAPTER_NORMALIZED
                    : VectorScoreThresholdExecution.UNSUPPORTED;
        }
        if (normalized && thresholdKind == VectorScoreThresholdKind.PROVIDER_RAW) {
            throw new IllegalArgumentException(
                    "normalized vector scores cannot declare PROVIDER_RAW threshold kind");
        }
        if (!normalized && thresholdKind == VectorScoreThresholdKind.NORMALIZED_RELEVANCE) {
            throw new IllegalArgumentException(
                    "unnormalized vector scores cannot declare NORMALIZED_RELEVANCE threshold kind");
        }
    }
}
