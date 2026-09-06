/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorScoreStage;
import cn.richie696.component.vector.topology.VectorScoreThresholdExecution;
import cn.richie696.component.vector.topology.VectorScoreThresholdKind;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cross-Provider score-threshold contract tests for R-001. */
class VectorScoreThresholdContractTest {

    @Test
    void defaultsToNormalizedRelevanceWhenStoreAdvertisesNormalizedFinalScore() {
        VectorScoreSemantics semantics = VectorScoreSemantics.finalScore(
                "milvus.adapter", 0.0, 1.0, true, "metric-aware-conversion-clamped",
                VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        VectorQueryResolver resolver = new VectorQueryResolver(null, null, semantics);

        ResolvedVectorQuery resolved = resolver.resolve(new VectorQueryRequest(
                "q", 5, 0.5D, null, null, Set.of(), 10, Duration.ofSeconds(5), null, null, null));

        assertThat(resolved.thresholdKind()).isEqualTo(VectorScoreThresholdKind.NORMALIZED_RELEVANCE);
        assertThat(resolved.minScore()).isEqualTo(0.5D);
    }

    @Test
    void defaultsToProviderRawWhenStoreAdvertisesUnnormalizedFinalScore() {
        VectorScoreSemantics semantics = VectorScoreSemantics.finalScore(
                "weaviate.provider.dot", null, null, false, "weaviate-dot-score",
                VectorScoreThresholdKind.PROVIDER_RAW,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        VectorQueryResolver resolver = new VectorQueryResolver(null, null, semantics);

        ResolvedVectorQuery resolved = resolver.resolve(new VectorQueryRequest(
                "q", 5, 12.5D, null, null, Set.of(), 10, Duration.ofSeconds(5), null, null, null));

        assertThat(resolved.thresholdKind()).isEqualTo(VectorScoreThresholdKind.PROVIDER_RAW);
        assertThat(resolved.minScore()).isEqualTo(12.5D);
    }

    @Test
    void honorsExplicitProviderRawOverrideEvenWhenStoreIsNormalized() {
        VectorScoreSemantics semantics = VectorScoreSemantics.finalScore(
                "milvus.adapter", 0.0, 1.0, true, "metric-aware-conversion-clamped",
                VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        VectorQueryResolver resolver = new VectorQueryResolver(null, null, semantics);

        ResolvedVectorQuery resolved = resolver.resolve(new VectorQueryRequest(
                "q", 5, 0.9D, VectorScoreThresholdKind.PROVIDER_RAW, null, Set.of(),
                10, Duration.ofSeconds(5), null, null, null));

        assertThat(resolved.thresholdKind()).isEqualTo(VectorScoreThresholdKind.PROVIDER_RAW);
    }

    @Test
    void rejectsNormalizedRelevanceAboveOneRegardlessOfStoreBounds() {
        VectorScoreSemantics semantics = VectorScoreSemantics.finalScore(
                "pgvector.cosine", 0.0, 1.0, true, "1.0-distance",
                VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        VectorQueryResolver resolver = new VectorQueryResolver(null, null, semantics);

        assertThatThrownBy(() -> resolver.resolve(new VectorQueryRequest(
                "q", 5, 1.5D, null, null, Set.of(), 10, Duration.ofSeconds(5), null, null, null)))
                .isInstanceOf(VectorQueryValidationException.class)
                .hasMessageContaining("NORMALIZED_RELEVANCE");
    }

    @Test
    void allowsProviderRawAboveOneWhenStoreRangeIsUnbounded() {
        VectorScoreSemantics semantics = VectorScoreSemantics.finalScore(
                "pgvector.inner-product", null, null, false, "-distance",
                VectorScoreThresholdKind.PROVIDER_RAW,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        VectorQueryResolver resolver = new VectorQueryResolver(null, null, semantics);

        ResolvedVectorQuery resolved = resolver.resolve(new VectorQueryRequest(
                "q", 5, 0.0D, null, null, Set.of(), 10, Duration.ofSeconds(5), null, null, null));
        assertThat(resolved.minScore()).isEqualTo(0.0D);
    }

    @Test
    void rejectsProviderRawExceedingStoreDeclaredMaximum() {
        VectorScoreSemantics semantics = VectorScoreSemantics.finalScore(
                "weaviate.cosine", 0.0, 1.0, true, "weaviate-certainty",
                VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        VectorQueryResolver resolver = new VectorQueryResolver(null, null, semantics);

        assertThatThrownBy(() -> resolver.resolve(new VectorQueryRequest(
                "q", 5, 5.0D, VectorScoreThresholdKind.PROVIDER_RAW, null, Set.of(),
                10, Duration.ofSeconds(5), null, null, null)))
                .isInstanceOf(VectorQueryValidationException.class)
                .hasMessageContaining("PROVIDER_RAW");
    }

    @Test
    void rejectsNegativeMinScoreEvenInProviderRawMode() {
        VectorScoreSemantics semantics = VectorScoreSemantics.finalScore(
                "weaviate.dot", null, null, false, "weaviate-dot-score",
                VectorScoreThresholdKind.PROVIDER_RAW,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        VectorQueryResolver resolver = new VectorQueryResolver(null, null, semantics);

        assertThatThrownBy(() -> resolver.resolve(new VectorQueryRequest(
                "q", 5, -0.1D, null, null, Set.of(), 10, Duration.ofSeconds(5), null, null, null)))
                .isInstanceOf(VectorQueryValidationException.class);
    }

    @Test
    void rejectsNaNAndInfiniteMinScore() {
        VectorQueryResolver resolver = new VectorQueryResolver(null, null);
        assertThatThrownBy(() -> resolver.resolve(new VectorQueryRequest(
                "q", 5, Double.NaN, null, null, Set.of(), 10, Duration.ofSeconds(5), null, null, null)))
                .isInstanceOf(VectorQueryValidationException.class);
        assertThatThrownBy(() -> resolver.resolve(new VectorQueryRequest(
                "q", 5, Double.POSITIVE_INFINITY, null, null, Set.of(), 10,
                Duration.ofSeconds(5), null, null, null)))
                .isInstanceOf(VectorQueryValidationException.class);
    }

    @Test
    void coreSafeDefaultIsNormalizedRelevance() {
        assertThat(VectorQueryDefaults.CORE_SAFE.thresholdKind())
                .isEqualTo(VectorScoreThresholdKind.NORMALIZED_RELEVANCE);
    }

    @Test
    void scoreDescriptorRejectsInconsistentKindAndNormalizedCombination() {
        assertThatThrownBy(() -> new cn.richie696.component.vector.topology.VectorScoreDescriptor(
                VectorScoreStage.FINAL_SCORE, "x", cn.richie696.component.vector.topology.VectorScoreDirection.HIGHER_IS_BETTER,
                0.0, 1.0, true, false, "identity",
                VectorScoreThresholdKind.PROVIDER_RAW,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROVIDER_RAW");
    }
}
