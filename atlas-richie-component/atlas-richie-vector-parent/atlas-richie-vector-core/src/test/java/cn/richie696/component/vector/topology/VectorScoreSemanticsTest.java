/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorScoreSemanticsTest {

    @Test
    void shouldExposeOnlyProvenStageSemantics() {
        VectorScoreSemantics semantics = VectorScoreSemantics.finalScore(
                "milvus.adapter", 0.0, 1.0, true, "metric-aware-adapter-conversion");

        assertThat(semantics.descriptor(VectorScoreStage.FINAL_SCORE))
                .get()
                .extracting(VectorScoreDescriptor::direction,
                        VectorScoreDescriptor::normalized,
                        VectorScoreDescriptor::comparableAcrossQueries)
                .containsExactly(VectorScoreDirection.HIGHER_IS_BETTER, true, false);
        assertThat(semantics.descriptor(VectorScoreStage.RERANK_SCORE)).isEmpty();
        assertThatThrownBy(() -> semantics.descriptors().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectAmbiguousNormalizedRangesAndDuplicateStages() {
        assertThatThrownBy(() -> VectorScoreSemantics.finalScore(
                "adapter", null, null, true, "identity"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declared range");

        VectorScoreDescriptor descriptor = new VectorScoreDescriptor(
                VectorScoreStage.FINAL_SCORE,
                "adapter",
                VectorScoreDirection.HIGHER_IS_BETTER,
                null,
                null,
                false,
                false,
                "provider-defined",
                VectorScoreThresholdKind.PROVIDER_RAW,
                VectorScoreThresholdExecution.UNSUPPORTED);
        assertThatThrownBy(() -> new VectorScoreSemantics(List.of(descriptor, descriptor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate vector score stage");
    }

    @Test
    void shouldRejectUnknownCrossStoreMixingAndAllowOnlyIdenticalComparableContracts() {
        VectorScoreSemantics providerFinal = VectorScoreSemantics.finalScore(
                "provider.adapter", 0.0, 1.0, true, "provider-defined");

        assertThatThrownBy(() -> VectorScoreFusionGuard.requireComparable(
                VectorScoreStage.FINAL_SCORE, List.of(providerFinal, providerFinal)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not proven compatible");

        VectorScoreDescriptor normalized = new VectorScoreDescriptor(
                VectorScoreStage.FUSED_SCORE,
                "framework.normalized-relevance-v1",
                VectorScoreDirection.HIGHER_IS_BETTER,
                0.0,
                1.0,
                true,
                true,
                "calibrated-min-max-v1",
                VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        VectorScoreSemantics comparable = new VectorScoreSemantics(List.of(normalized));

        assertThat(VectorScoreFusionGuard.requireComparable(
                VectorScoreStage.FUSED_SCORE, List.of(comparable, comparable)))
                .isEqualTo(normalized);
    }
}
