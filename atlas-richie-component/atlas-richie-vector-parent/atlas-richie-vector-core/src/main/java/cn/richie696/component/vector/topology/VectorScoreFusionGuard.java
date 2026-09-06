/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Collection;
import java.util.Objects;

/** Prevents accidental cross-Store ranking when score semantics are absent or incompatible. */
public final class VectorScoreFusionGuard {

    private VectorScoreFusionGuard() {
    }

    public static VectorScoreDescriptor requireComparable(
            VectorScoreStage stage,
            Collection<VectorScoreSemantics> stores) {
        Objects.requireNonNull(stage, "score stage must not be null");
        if (stores == null || stores.size() < 2) {
            throw new IllegalArgumentException("cross-Store score comparison requires at least two Stores");
        }
        VectorScoreDescriptor baseline = null;
        for (VectorScoreSemantics semantics : stores) {
            if (semantics == null) {
                throw incompatible(stage);
            }
            VectorScoreDescriptor descriptor = semantics.descriptor(stage).orElseThrow(() -> incompatible(stage));
            if (!descriptor.normalized() || !descriptor.comparableAcrossQueries()) {
                throw incompatible(stage);
            }
            if (baseline == null) {
                baseline = descriptor;
            } else if (!compatible(baseline, descriptor)) {
                throw incompatible(stage);
            }
        }
        return baseline;
    }

    private static boolean compatible(VectorScoreDescriptor left, VectorScoreDescriptor right) {
        return left.source().equals(right.source())
                && left.direction() == right.direction()
                && Objects.equals(left.minimum(), right.minimum())
                && Objects.equals(left.maximum(), right.maximum())
                && left.transformation().equals(right.transformation());
    }

    private static IllegalArgumentException incompatible(VectorScoreStage stage) {
        return new IllegalArgumentException(
                "cross-Store score comparison is not proven compatible for stage " + stage);
    }
}
