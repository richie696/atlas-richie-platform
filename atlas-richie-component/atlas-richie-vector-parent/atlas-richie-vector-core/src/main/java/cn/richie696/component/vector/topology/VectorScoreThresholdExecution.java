/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

/** Where a {@code minScore} threshold is actually enforced for a Store. */
public enum VectorScoreThresholdExecution {

    /** Provider applies the threshold as a server-side filter on the recalled candidates. */
    PROVIDER_PUSHED,

    /**
     * Adapter receives the full candidate set from the provider, then drops results whose
     * adapted score falls below the threshold before returning to the caller.
     */
    ADAPTER_NORMALIZED,

    /**
     * Adapter does not enforce the threshold; the value is recorded in the receipt and
     * returned as-is. Callers must not assume a provider-side filter happened.
     */
    UNSUPPORTED
}
