/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import cn.richie696.component.vector.diagnostics.VectorErrorCategory;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.topology.VectorStoreId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Payload-free Store operation event. Metric tags intentionally exclude the
 * index fingerprint and capability summary; those belong only in trace data.
 */
public record VectorStoreObservationEvent(
        VectorStoreId storeId,
        VectorProvider provider,
        VectorStoreOperation operation,
        VectorStoreOperationResult result,
        Duration elapsed,
        String indexFingerprint,
        Set<String> effectiveCapabilities,
        VectorErrorCategory errorCategory,
        String execution) {

    public VectorStoreObservationEvent(
            VectorStoreId storeId, VectorProvider provider, VectorStoreOperation operation,
            VectorStoreOperationResult result, Duration elapsed, String indexFingerprint,
            Set<String> effectiveCapabilities, VectorErrorCategory errorCategory) {
        this(storeId, provider, operation, result, elapsed, indexFingerprint, effectiveCapabilities, errorCategory, "unknown");
    }

    public VectorStoreObservationEvent {
        Objects.requireNonNull(storeId, "storeId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(elapsed, "elapsed must not be null");
        Objects.requireNonNull(indexFingerprint, "indexFingerprint must not be null");
        Objects.requireNonNull(errorCategory, "errorCategory must not be null");
        if (!Set.of("native", "core-rrf", "unknown").contains(execution)) {
            throw new IllegalArgumentException("execution must be native, core-rrf, or unknown");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
        effectiveCapabilities = Set.copyOf(effectiveCapabilities == null ? Set.of() : effectiveCapabilities);
    }

    /** Exactly the bounded fields permitted as metric labels. */
    public Map<String, String> metricTags() {
        return Map.of(
                "store", storeId.value(),
                "provider", provider.name(),
                "operation", operation.name(),
                "result", result.name());
    }

    /** Sanitized attributes for tracing; no query, vector, ACL, credentials or physical index. */
    public Map<String, String> traceAttributes() {
        return Map.of(
                "vector.store", storeId.value(),
                "vector.provider", provider.name(),
                "vector.operation", operation.name(),
                "vector.result", result.name(),
                "vector.index.fingerprint", indexFingerprint,
                "vector.capabilities", String.join(",", new TreeSet<>(effectiveCapabilities)),
                "vector.error.category", errorCategory.name(),
                "vector.hybrid.execution", execution);
    }

    public static String fingerprint(String logicalIndex) {
        String normalized = logicalIndex == null ? "" : logicalIndex;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
