/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service.support;

import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorSearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Provider-neutral, ACL-safe hybrid fallback support.
 *
 * <p>This class deliberately does not perform provider queries itself. A provider must first
 * recall dense and sparse candidates with the <em>same ACL filter already pushed down to both
 * provider requests</em>, then pass those safe candidate sets here for rank fusion. This keeps
 * provider SDK types out of Core and prevents an accidentally post-filtered candidate from
 * participating in ranking.</p>
 *
 * <p>{@link #nativeFirst(CheckedSearch, CheckedSearch, CheckedSearch, HybridSearchOptions, int,
 * Predicate)} falls back only for errors explicitly classified by the provider as a native
 * hybrid capability mismatch. Authentication, timeout and ordinary data-plane errors must be
 * propagated rather than hidden by a fallback.</p>
 */
public final class AclSafeHybridSearchFallback {

    private static final int RRF_K = 60;

    private AclSafeHybridSearchFallback() {
    }

    /** Executes native hybrid first and fuses two ACL-filtered candidate sets only when allowed. */
    public static List<VectorSearchResult> nativeFirst(
            CheckedSearch nativeSearch,
            CheckedSearch denseSearch,
            CheckedSearch sparseSearch,
            HybridSearchOptions options,
            int limit,
            Predicate<? super RuntimeException> fallbackAllowed) {
        Objects.requireNonNull(nativeSearch, "nativeSearch must not be null");
        Objects.requireNonNull(denseSearch, "denseSearch must not be null");
        Objects.requireNonNull(sparseSearch, "sparseSearch must not be null");
        Objects.requireNonNull(fallbackAllowed, "fallbackAllowed must not be null");
        try {
            List<VectorSearchResult> nativeResults = nativeSearch.search();
            return limit(nativeResults, limit);
        } catch (RuntimeException error) {
            if (!fallbackAllowed.test(error)) {
                throw error;
            }
            return fuse(denseSearch.search(), sparseSearch.search(), options, limit);
        }
    }

    /**
     * Fuses ACL-filtered dense and sparse candidates with weighted reciprocal-rank fusion.
     * Rank fusion is intentionally used instead of comparing provider scores, whose scales are
     * generally not comparable across dense and sparse query branches.
     */
    public static List<VectorSearchResult> fuse(
            List<VectorSearchResult> denseCandidates,
            List<VectorSearchResult> sparseCandidates,
            HybridSearchOptions options,
            int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("hybrid search limit must be greater than zero");
        }
        Weights weights = weights(options);
        Map<String, FusedCandidate> candidates = new LinkedHashMap<>();
        addCandidates(candidates, denseCandidates, weights.dense());
        addCandidates(candidates, sparseCandidates, weights.sparse());
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(FusedCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.result().getId()))
                .limit(limit)
                .map(FusedCandidate::result)
                .toList();
    }

    private static List<VectorSearchResult> limit(List<VectorSearchResult> results, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("hybrid search limit must be greater than zero");
        }
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream().filter(Objects::nonNull).limit(limit).map(AclSafeHybridSearchFallback::copy).toList();
    }

    private static void addCandidates(
            Map<String, FusedCandidate> candidates, List<VectorSearchResult> source, double weight) {
        if (source == null || source.isEmpty() || weight == 0.0D) {
            return;
        }
        int rank = 0;
        for (VectorSearchResult candidate : source) {
            if (candidate == null || candidate.getId() == null || candidate.getId().isBlank()) {
                continue;
            }
            rank++;
            double contribution = weight / (RRF_K + rank);
            FusedCandidate fused = candidates.computeIfAbsent(candidate.getId(), ignored ->
                    new FusedCandidate(copy(candidate), 0.0D));
            fused.score += contribution;
            fused.result.setScore(fused.score);
        }
    }

    private static Weights weights(HybridSearchOptions options) {
        double dense = options != null && options.getVectorWeight() != null ? options.getVectorWeight() : 0.7D;
        double sparse = options != null && options.getKeywordWeight() != null ? options.getKeywordWeight() : 0.3D;
        if (!Double.isFinite(dense) || !Double.isFinite(sparse)
                || dense < 0.0D || dense > 1.0D || sparse < 0.0D || sparse > 1.0D
                || Math.abs(dense + sparse - 1.0D) > 0.000_001D) {
            throw new IllegalArgumentException(
                    "ACL-safe hybrid weights must be finite, within [0,1], and sum to 1");
        }
        return new Weights(dense, sparse);
    }

    private static VectorSearchResult copy(VectorSearchResult source) {
        return VectorSearchResult.of(source.getId(), source.getContent(), source.getScore(),
                        source.getVector() == null ? null : source.getVector().clone())
                .setType(source.getType())
                .setTags(source.getTags() == null ? null : source.getTags().clone())
                .setSource(source.getSource())
                .setCreatedAt(source.getCreatedAt())
                .setUpdatedAt(source.getUpdatedAt())
                .setDocumentScore(source.getDocumentScore())
                .setStatus(source.getStatus())
                .setMetadata(source.getMetadata())
                .setNamespace(source.getNamespace());
    }

    /** Provider callback which must perform a fully ACL-filtered candidate recall. */
    @FunctionalInterface
    public interface CheckedSearch {
        List<VectorSearchResult> search();
    }

    private static final class FusedCandidate {
        private final VectorSearchResult result;
        private double score;

        private FusedCandidate(VectorSearchResult result, double score) {
            this.result = result;
            this.score = score;
        }

        private VectorSearchResult result() {
            return result;
        }

        private double score() {
            return score;
        }
    }

    private record Weights(double dense, double sparse) {
    }
}
