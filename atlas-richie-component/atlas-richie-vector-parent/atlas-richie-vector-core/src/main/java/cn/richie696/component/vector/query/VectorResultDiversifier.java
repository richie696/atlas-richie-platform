/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query;

import cn.richie696.component.vector.model.VectorSearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.charset.StandardCharsets;

/** Resource-bounded, deterministic client-side MMR over provider-filtered candidates. */
public final class VectorResultDiversifier {

    public static final int MAX_VECTOR_DIMENSION = 4_096;
    public static final long MAX_VECTOR_BYTES = 4L * 1024L * 1024L;
    public static final long MAX_RESPONSE_BYTES = 8L * 1024L * 1024L;

    private VectorResultDiversifier() {
    }

    public static List<VectorSearchResult> apply(
            List<VectorSearchResult> candidates,
            int topK,
            VectorDiversificationOptions options) {
        List<VectorSearchResult> safeCandidates = List.copyOf(candidates == null ? List.of() : candidates);
        VectorDiversificationOptions effective = options == null ? VectorDiversificationOptions.DISABLED : options;
        if (topK < 1 || topK > VectorQueryResolver.MAX_TOP_K
                || safeCandidates.size() > VectorQueryResolver.MAX_CANDIDATES) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.LIMIT_EXCEEDED, "candidate diversification exceeds a safety limit");
        }
        if (estimatedResponseBytes(safeCandidates) > MAX_RESPONSE_BYTES) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.LIMIT_EXCEEDED, "advanced vector response exceeds a safety limit");
        }
        if (!effective.requiresCandidateVectors()) {
            return safeCandidates.stream().limit(topK).map(result -> copy(result, false)).toList();
        }
        validateVectors(safeCandidates);
        List<VectorSearchResult> selected = effective.mmrEnabled()
                ? mmr(safeCandidates, topK, effective.lambda())
                : safeCandidates.stream().limit(topK).toList();
        return selected.stream().map(result -> copy(result, effective.includeVectors())).toList();
    }

    private static long estimatedResponseBytes(List<VectorSearchResult> candidates) {
        long bytes = 0L;
        for (VectorSearchResult candidate : candidates) {
            bytes += utf8(candidate.getId()) + utf8(candidate.getContent()) + utf8(candidate.getMetadata());
            if (candidate.getVector() != null) bytes += (long) candidate.getVector().length * Float.BYTES;
            if (bytes > MAX_RESPONSE_BYTES) return bytes;
        }
        return bytes;
    }

    private static int utf8(Object value) {
        return value == null ? 0 : String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
    }

    private static void validateVectors(List<VectorSearchResult> candidates) {
        int dimension = -1;
        long bytes = 0L;
        for (VectorSearchResult candidate : candidates) {
            float[] vector = candidate.getVector();
            if (vector == null || vector.length == 0) {
                throw new VectorQueryValidationException(
                        VectorQueryErrorCode.UNSUPPORTED_OPTION, "candidate vector is missing");
            }
            if (dimension < 0) dimension = vector.length;
            if (vector.length != dimension) {
                throw new VectorQueryValidationException(
                        VectorQueryErrorCode.INVALID_VALUE, "candidate vector dimensions do not match");
            }
            bytes += (long) vector.length * Float.BYTES;
        }
        if (dimension > MAX_VECTOR_DIMENSION || bytes > MAX_VECTOR_BYTES) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.LIMIT_EXCEEDED, "candidate vectors exceed a safety limit");
        }
    }

    private static List<VectorSearchResult> mmr(List<VectorSearchResult> candidates, int topK, double lambda) {
        List<VectorSearchResult> remaining = new ArrayList<>(candidates);
        List<VectorSearchResult> selected = new ArrayList<>();
        Comparator<VectorSearchResult> deterministic = Comparator
                .comparingDouble(VectorResultDiversifier::score).reversed()
                .thenComparing(result -> result.getId() == null ? "" : result.getId());
        remaining.sort(deterministic);
        while (!remaining.isEmpty() && selected.size() < topK) {
            VectorSearchResult best = remaining.stream()
                    .max(Comparator.comparingDouble(
                                    (VectorSearchResult candidate) -> mmrScore(candidate, selected, lambda))
                            .thenComparing((VectorSearchResult candidate) ->
                                            candidate.getId() == null ? "" : candidate.getId(),
                                    Comparator.reverseOrder()))
                    .orElseThrow();
            selected.add(best);
            remaining.remove(best);
        }
        return List.copyOf(selected);
    }

    private static double mmrScore(
            VectorSearchResult candidate, List<VectorSearchResult> selected, double lambda) {
        double redundancy = selected.stream()
                .mapToDouble(item -> cosine(candidate.getVector(), item.getVector()))
                .max().orElse(0.0D);
        return lambda * score(candidate) - (1.0D - lambda) * redundancy;
    }

    private static double score(VectorSearchResult result) {
        return result.getScore() == null || !Double.isFinite(result.getScore()) ? 0.0D : result.getScore();
    }

    private static double cosine(float[] left, float[] right) {
        double dot = 0.0D;
        double leftNorm = 0.0D;
        double rightNorm = 0.0D;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0.0D || rightNorm == 0.0D ? 0.0D : dot / Math.sqrt(leftNorm * rightNorm);
    }

    private static VectorSearchResult copy(VectorSearchResult source, boolean includeVector) {
        return new VectorSearchResult()
                .setId(source.getId())
                .setContent(source.getContent())
                .setScore(source.getScore())
                .setVector(includeVector && source.getVector() != null ? source.getVector().clone() : null)
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
}
