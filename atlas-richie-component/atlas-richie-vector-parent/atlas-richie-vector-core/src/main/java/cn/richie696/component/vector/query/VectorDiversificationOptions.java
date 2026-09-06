/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

/** Optional candidate-vector and deterministic client-side MMR settings. */
public record VectorDiversificationOptions(
        boolean includeVectors,
        boolean mmrEnabled,
        double lambda) {

    public static final VectorDiversificationOptions DISABLED =
            new VectorDiversificationOptions(false, false, 0.5D);

    public VectorDiversificationOptions {
        if (!Double.isFinite(lambda) || lambda < 0.0D || lambda > 1.0D) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.INVALID_VALUE, "MMR lambda must be within [0,1]");
        }
    }

    public boolean requiresCandidateVectors() {
        return includeVectors || mmrEnabled;
    }
}
