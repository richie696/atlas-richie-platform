/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.model.VectorSearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class VectorResultDiversifierTest {

    @Test
    void defaultPathPreservesRankingWithoutReturningVectors() {
        List<VectorSearchResult> results = VectorResultDiversifier.apply(
                List.of(result("one", 0.9, 1.0f, 0.0f), result("two", 0.8, 0.0f, 1.0f)),
                1,
                VectorDiversificationOptions.DISABLED);

        assertThat(results).extracting(VectorSearchResult::getId).containsExactly("one");
        assertThat(results.getFirst().getVector()).isNull();
    }

    @Test
    void deterministicMmrSelectsDiverseCandidateAndReturnsVectorsOnlyWhenRequested() {
        List<VectorSearchResult> candidates = List.of(
                result("a", 1.0, 1.0f, 0.0f),
                result("b", 0.95, 0.99f, 0.01f),
                result("c", 0.8, 0.0f, 1.0f));

        List<VectorSearchResult> hidden = VectorResultDiversifier.apply(
                candidates, 2, new VectorDiversificationOptions(false, true, 0.5D));
        List<VectorSearchResult> included = VectorResultDiversifier.apply(
                candidates, 2, new VectorDiversificationOptions(true, true, 0.5D));

        assertThat(hidden).extracting(VectorSearchResult::getId).containsExactly("a", "c");
        assertThat(hidden).allSatisfy(result -> assertThat(result.getVector()).isNull());
        assertThat(included).extracting(VectorSearchResult::getId).containsExactly("a", "c");
        assertThat(included).allSatisfy(result -> assertThat(result.getVector()).hasSize(2));
    }

    @Test
    void enforcesMissingDimensionAndResponseSizeLimits() {
        assertThatThrownBy(() -> VectorResultDiversifier.apply(
                List.of(VectorSearchResult.of("missing", "", 1.0)),
                1,
                new VectorDiversificationOptions(true, false, 0.5D)))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.UNSUPPORTED_OPTION));
        assertThatThrownBy(() -> VectorResultDiversifier.apply(
                List.of(result("oversized", 1.0, new float[VectorResultDiversifier.MAX_VECTOR_DIMENSION + 1])),
                1,
                new VectorDiversificationOptions(true, false, 0.5D)))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.LIMIT_EXCEEDED));

        List<VectorSearchResult> tooManyBytes = new ArrayList<>();
        for (int index = 0; index < 257; index++) {
            tooManyBytes.add(result("id-" + index, 1.0, new float[4_096]));
        }
        assertThatThrownBy(() -> VectorResultDiversifier.apply(
                tooManyBytes, 1, new VectorDiversificationOptions(true, false, 0.5D)))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.LIMIT_EXCEEDED));

        VectorSearchResult oversizedContent = VectorSearchResult.of(
                "large", "x".repeat((int) VectorResultDiversifier.MAX_RESPONSE_BYTES + 1), 1.0);
        assertThatThrownBy(() -> VectorResultDiversifier.apply(
                List.of(oversizedContent), 1, VectorDiversificationOptions.DISABLED))
                .isInstanceOfSatisfying(VectorQueryValidationException.class,
                        error -> assertThat(error.code()).isEqualTo(VectorQueryErrorCode.LIMIT_EXCEEDED));
    }

    @Test
    void keepsMaximumSupportedCandidateMmrWithinTheLocalPerformanceBudget() {
        List<VectorSearchResult> candidates = new ArrayList<>();
        for (int row = 0; row < 1_000; row++) {
            float[] vector = new float[128];
            vector[row % vector.length] = 1.0f;
            candidates.add(result("candidate-" + row, 1.0D - row / 2_000.0D, vector));
        }

        List<VectorSearchResult> selected = assertTimeout(Duration.ofSeconds(2), () ->
                VectorResultDiversifier.apply(
                        candidates, 20, new VectorDiversificationOptions(false, true, 0.5D)));

        assertThat(selected).hasSize(20).allSatisfy(result -> assertThat(result.getVector()).isNull());
    }

    private static VectorSearchResult result(String id, double score, float... vector) {
        return VectorSearchResult.of(id, id, score, vector);
    }
}
