/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Locale;

/** Stable provider-neutral IDs for optional vector store capabilities. */
public enum VectorCapability {
    NATIVE_FILTER,
    ACL_FILTER,
    ACL_SAFE_HYBRID,
    CANDIDATE_VECTOR,
    SERVER_SIDE_MMR,
    QUERY_TUNING,
    INDEX_LIFECYCLE,
    SCORE_STAGES,
    PRECOMPUTED_VECTOR;

    public String id() {
        return name();
    }

    public static VectorCapability fromId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("vector capability id must not be blank");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown vector capability: " + value);
        }
    }
}
