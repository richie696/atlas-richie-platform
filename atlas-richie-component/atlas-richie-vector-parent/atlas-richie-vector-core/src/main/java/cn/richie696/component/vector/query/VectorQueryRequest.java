/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.topology.VectorScoreThresholdKind;

import java.time.Duration;
import java.util.Set;

/**
 * Store-bound advanced text query. It intentionally carries no Store ID,
 * connection data, credentials or physical index name.
 *
 * <p>{@link #minScore} is interpreted in the {@link #thresholdKind} the caller
 * declares. When the kind is {@code null} the Store's default applies
 * ({@code NORMALIZED_RELEVANCE} if the Store advertises a normalized final score,
 * otherwise {@code PROVIDER_RAW}).</p>
 */
public record VectorQueryRequest(
        String query,
        Integer topK,
        Double minScore,
        VectorScoreThresholdKind thresholdKind,
        VectorFilter filter,
        Set<String> returnFields,
        Integer candidateLimit,
        Duration timeout,
        VectorConsistencyPreference consistency,
        VectorDiversificationOptions diversification,
        ProviderQueryOptions providerOptions) {

    public VectorQueryRequest(
            String query,
            Integer topK,
            Double minScore,
            VectorFilter filter,
            Set<String> returnFields,
            Integer candidateLimit,
            Duration timeout,
            VectorConsistencyPreference consistency,
            ProviderQueryOptions providerOptions) {
        this(query, topK, minScore, null, filter, returnFields, candidateLimit, timeout,
                consistency, null, providerOptions);
    }

    /** Backward-compatible 10-arg constructor preserving the original diversification-as-9th signature. */
    public VectorQueryRequest(
            String query,
            Integer topK,
            Double minScore,
            VectorFilter filter,
            Set<String> returnFields,
            Integer candidateLimit,
            Duration timeout,
            VectorConsistencyPreference consistency,
            VectorDiversificationOptions diversification,
            ProviderQueryOptions providerOptions) {
        this(query, topK, minScore, null, filter, returnFields, candidateLimit, timeout,
                consistency, diversification, providerOptions);
    }

    public VectorQueryRequest {
        if (query == null || query.isBlank()) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.INVALID_VALUE, "query must not be blank");
        }
        if (minScore != null && !Double.isFinite(minScore)) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.INVALID_VALUE, "minScore must be finite");
        }
        returnFields = Set.copyOf(returnFields == null ? Set.of() : returnFields);
        diversification = diversification == null ? VectorDiversificationOptions.DISABLED : diversification;
    }

    public static VectorQueryRequest of(String query) {
        return new VectorQueryRequest(query, null, null, null, null, Set.of(), null, null, null, null, null);
    }
}
