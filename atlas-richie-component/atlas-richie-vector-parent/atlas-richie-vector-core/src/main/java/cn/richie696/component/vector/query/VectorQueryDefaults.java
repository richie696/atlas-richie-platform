/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.topology.VectorScoreThresholdKind;

import java.time.Duration;
import java.util.Set;

/** Optional provider/Store default layer; null fields inherit from the preceding layer. */
public record VectorQueryDefaults(
        Integer topK,
        Double minScore,
        Integer candidateLimit,
        Duration timeout,
        VectorConsistencyPreference consistency,
        Set<String> returnFields,
        VectorScoreThresholdKind thresholdKind) {

    public static final VectorQueryDefaults CORE_SAFE = new VectorQueryDefaults(
            10, 0.0D, 10, Duration.ofSeconds(30),
            VectorConsistencyPreference.PROVIDER_DEFAULT, Set.of(),
            VectorScoreThresholdKind.NORMALIZED_RELEVANCE);

    public VectorQueryDefaults(
            Integer topK, Double minScore, Integer candidateLimit, Duration timeout,
            VectorConsistencyPreference consistency, Set<String> returnFields) {
        this(topK, minScore, candidateLimit, timeout, consistency, returnFields, null);
    }

    public VectorQueryDefaults {
        returnFields = Set.copyOf(returnFields == null ? Set.of() : returnFields);
    }

    public VectorQueryDefaults overlay(VectorQueryDefaults override) {
        if (override == null) return this;
        return new VectorQueryDefaults(
                override.topK != null ? override.topK : topK,
                override.minScore != null ? override.minScore : minScore,
                override.candidateLimit != null ? override.candidateLimit : candidateLimit,
                override.timeout != null ? override.timeout : timeout,
                override.consistency != null ? override.consistency : consistency,
                override.returnFields.isEmpty() ? returnFields : override.returnFields,
                override.thresholdKind != null ? override.thresholdKind : thresholdKind);
    }
}
