/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.vector.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable provider-neutral sparse coordinates used for both document and query encoding. */
public record SparseVector(Map<Long, Float> coordinates) {
    public SparseVector {
        coordinates = Map.copyOf(coordinates == null ? Map.of() : new LinkedHashMap<>(coordinates));
        if (coordinates.isEmpty()) throw new IllegalArgumentException("sparse vector must not be empty");
        for (Map.Entry<Long, Float> entry : coordinates.entrySet()) {
            if (entry.getKey() == null || entry.getKey() < 0L || entry.getValue() == null
                    || !Float.isFinite(entry.getValue())) {
                throw new IllegalArgumentException("sparse vector contains an invalid token coordinate");
            }
        }
    }
}
