/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.topology.VectorScoreThresholdKind;

import java.time.Duration;
import java.util.Set;

/** Fully resolved and validated effective query options. */
public record ResolvedVectorQuery(
        String query,
        int topK,
        double minScore,
        VectorScoreThresholdKind thresholdKind,
        VectorFilter filter,
        Set<String> returnFields,
        int candidateLimit,
        Duration timeout,
        VectorConsistencyPreference consistency,
        VectorDiversificationOptions diversification,
        ProviderQueryOptions providerOptions) {

    public ResolvedVectorQuery {
        returnFields = Set.copyOf(returnFields);
        diversification = diversification == null ? VectorDiversificationOptions.DISABLED : diversification;
        if (thresholdKind == null) {
            thresholdKind = VectorScoreThresholdKind.NORMALIZED_RELEVANCE;
        }
    }
}
