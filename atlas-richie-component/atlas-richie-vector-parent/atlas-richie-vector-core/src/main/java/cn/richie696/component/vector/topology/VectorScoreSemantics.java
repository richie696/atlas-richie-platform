/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Store-bound score semantics; absent stages are explicitly unsupported or unproven. */
public final class VectorScoreSemantics {

    private final Map<VectorScoreStage, VectorScoreDescriptor> descriptors;

    public VectorScoreSemantics(Collection<VectorScoreDescriptor> descriptors) {
        Objects.requireNonNull(descriptors, "vector score descriptors must not be null");
        EnumMap<VectorScoreStage, VectorScoreDescriptor> indexed = new EnumMap<>(VectorScoreStage.class);
        for (VectorScoreDescriptor descriptor : descriptors) {
            Objects.requireNonNull(descriptor, "vector score descriptors must not contain null");
            if (indexed.putIfAbsent(descriptor.stage(), descriptor) != null) {
                throw new IllegalArgumentException("duplicate vector score stage: " + descriptor.stage());
            }
        }
        if (indexed.isEmpty()) {
            throw new IllegalArgumentException("vector score descriptors must not be empty");
        }
        this.descriptors = Map.copyOf(indexed);
    }

    public Optional<VectorScoreDescriptor> descriptor(VectorScoreStage stage) {
        return Optional.ofNullable(descriptors.get(Objects.requireNonNull(stage, "vector score stage must not be null")));
    }

    public List<VectorScoreDescriptor> descriptors() {
        return descriptors.values().stream().toList();
    }

    /** A common adapter output contract for a final relevance score. */
    public static VectorScoreSemantics finalScore(
            String source,
            Double minimum,
            Double maximum,
            boolean normalized,
            String transformation) {
        return finalScore(source, minimum, maximum, normalized, transformation, null, null);
    }

    /**
     * Variant that lets callers explicitly declare the {@code minScore} threshold
     * semantics and execution stage. The defaults are derived from {@code normalized}
     * when either parameter is {@code null}.
     */
    public static VectorScoreSemantics finalScore(
            String source,
            Double minimum,
            Double maximum,
            boolean normalized,
            String transformation,
            VectorScoreThresholdKind thresholdKind,
            VectorScoreThresholdExecution thresholdExecution) {
        return new VectorScoreSemantics(List.of(new VectorScoreDescriptor(
                VectorScoreStage.FINAL_SCORE,
                source,
                VectorScoreDirection.HIGHER_IS_BETTER,
                minimum,
                maximum,
                normalized,
                false,
                transformation,
                thresholdKind,
                thresholdExecution)));
    }
}
