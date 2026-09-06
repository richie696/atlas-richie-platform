/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Versioned effective capability description for one concrete Store handle. */
public record VectorCapabilityDescriptor(
        VectorCapability capability,
        String version,
        Set<VectorCapabilityOperation> operations,
        Map<String, String> constraints) {

    public VectorCapabilityDescriptor(
            VectorCapability capability, String version, Map<String, String> constraints) {
        this(capability, version, defaultOperations(capability), constraints);
    }

    public VectorCapabilityDescriptor {
        Objects.requireNonNull(capability, "capability must not be null");
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("capability version must not be blank");
        }
        operations = Set.copyOf(operations == null ? Set.of() : operations);
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("capability operations must not be empty");
        }
        constraints = Map.copyOf(constraints == null ? Map.of() : constraints);
    }

    public static VectorCapabilityDescriptor supported(VectorCapability capability) {
        return new VectorCapabilityDescriptor(capability, "1.0", Map.of());
    }

    private static Set<VectorCapabilityOperation> defaultOperations(VectorCapability capability) {
        Objects.requireNonNull(capability, "capability must not be null");
        return switch (capability) {
            case NATIVE_FILTER, ACL_FILTER, QUERY_TUNING, SCORE_STAGES -> Set.of(
                    VectorCapabilityOperation.SEARCH_TEXT,
                    VectorCapabilityOperation.SEARCH_VECTOR,
                    VectorCapabilityOperation.HYBRID_SEARCH);
            case ACL_SAFE_HYBRID -> Set.of(VectorCapabilityOperation.HYBRID_SEARCH);
            case CANDIDATE_VECTOR, SERVER_SIDE_MMR -> Set.of(
                    VectorCapabilityOperation.SEARCH_TEXT,
                    VectorCapabilityOperation.SEARCH_VECTOR,
                    VectorCapabilityOperation.SEARCH_IMAGE,
                    VectorCapabilityOperation.HYBRID_SEARCH);
            case INDEX_LIFECYCLE -> Set.of(VectorCapabilityOperation.INDEX_MANAGEMENT);
            case PRECOMPUTED_VECTOR -> Set.of(
                    VectorCapabilityOperation.SEARCH_VECTOR,
                    VectorCapabilityOperation.UPSERT);
        };
    }
}
