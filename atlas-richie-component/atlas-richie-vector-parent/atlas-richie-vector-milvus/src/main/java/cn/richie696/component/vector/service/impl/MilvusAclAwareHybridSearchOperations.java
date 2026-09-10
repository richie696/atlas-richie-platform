/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.context.utils.data.JsonUtils;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Native Milvus hybrid search backed by a dense field and the server-side BM25 function.
 *
 * <p>The same compiled expression is attached to every {@link AnnSearchReq}.  This is the
 * security boundary for ACL-safe hybrid search: neither the dense nor sparse candidate set may
 * be recalled before the structured access predicate is applied.</p>
 */
public final class MilvusAclAwareHybridSearchOperations implements VectorAclAwareHybridSearchOperations {

    static final String DENSE_VECTOR_FIELD = "vector";
    static final String SPARSE_VECTOR_FIELD = "sparse_vector";
    private static final List<String> OUTPUT_FIELDS = List.of("content", "metadata");

    private final Function<HybridSearchReq, SearchResp> hybridSearch;
    private final EmbeddingModel embeddingModel;
    private final VectorFilterCompiler filterCompiler;

    public MilvusAclAwareHybridSearchOperations(
            MilvusClientV2 client, EmbeddingModel embeddingModel, VectorFilterCompiler filterCompiler) {
        this(client::hybridSearch, embeddingModel, filterCompiler);
    }

    /** Package-visible seam for request-contract tests without a live Milvus client. */
    MilvusAclAwareHybridSearchOperations(
            Function<HybridSearchReq, SearchResp> hybridSearch,
            EmbeddingModel embeddingModel,
            VectorFilterCompiler filterCompiler) {
        this.hybridSearch = hybridSearch;
        this.embeddingModel = embeddingModel;
        this.filterCompiler = filterCompiler;
    }

    @Override
    public List<VectorSearchResult> hybridSearch(
            String indexName, String text, String keywordQuery, int limit, HybridSearchOptions options) {
        throw new UnsupportedOperationException("Milvus ACL-safe hybrid search requires an explicit structured ACL filter");
    }

    @Override
    public List<VectorSearchResult> hybridSearch(
            String indexName,
            String text,
            String keywordQuery,
            int limit,
            HybridSearchOptions options,
            VectorFilter filter) {
        if (indexName == null || indexName.isBlank()) throw new IllegalArgumentException("indexName must not be blank");
        if (filter == null) throw new IllegalArgumentException("ACL filter must not be null");
        if (limit <= 0) throw new IllegalArgumentException("hybrid search limit must be greater than zero");

        double vectorWeight = options != null && options.getVectorWeight() != null ? options.getVectorWeight() : 0.7D;
        double keywordWeight = options != null && options.getKeywordWeight() != null ? options.getKeywordWeight() : 0.3D;
        validateWeights(vectorWeight, keywordWeight);
        SearchOptions searchOptions = options != null && options.getSearchOptions() != null
                ? options.getSearchOptions() : SearchOptions.builder().build();
        if (searchOptions.getFilter() != null && !searchOptions.getFilter().equals(filter)) {
            throw new IllegalArgumentException("ACL filter conflicts with hybrid search options filter");
        }

        String denseQuery = nonBlank(text) ? text : null;
        String sparseQuery = nonBlank(keywordQuery) ? keywordQuery : null;
        if (denseQuery == null && sparseQuery == null) {
            throw new IllegalArgumentException("text and keywordQuery must not both be blank");
        }
        String expression = filterCompiler.compile(filter);
        List<AnnSearchReq> requests = new ArrayList<>(2);
        List<Float> weights = new ArrayList<>(2);
        if (denseQuery != null) {
            float[] embedding = embeddingModel.embed(denseQuery);
            if (embedding == null || embedding.length == 0) {
                throw new IllegalStateException("Milvus hybrid dense embedding must not be empty");
            }
            requests.add(AnnSearchReq.builder()
                    .vectorFieldName(DENSE_VECTOR_FIELD)
                    .vectors(List.of(new FloatVec(embedding)))
                    .metricType(IndexParam.MetricType.COSINE)
                    .filter(expression)
                    .limit(limit)
                    .params("{}")
                    .build());
            weights.add((float) vectorWeight);
        }
        if (sparseQuery != null) {
            requests.add(AnnSearchReq.builder()
                    .vectorFieldName(SPARSE_VECTOR_FIELD)
                    .vectors(List.of(new EmbeddedText(sparseQuery)))
                    .metricType(IndexParam.MetricType.BM25)
                    .filter(expression)
                    .limit(limit)
                    .params("{}")
                    .build());
            weights.add((float) keywordWeight);
        }

        SearchResp response = hybridSearch.apply(HybridSearchReq.builder()
                .collectionName(indexName)
                .searchRequests(List.copyOf(requests))
                .ranker(WeightedRanker.builder().weights(List.copyOf(weights)).build())
                .outFields(outputFields(Boolean.TRUE.equals(searchOptions.getIncludeCandidateVectors())))
                .limit(limit)
                .build());
        return translate(response, searchOptions.getMinScore(),
                Boolean.TRUE.equals(searchOptions.getIncludeCandidateVectors()));
    }

    private static void validateWeights(double vectorWeight, double keywordWeight) {
        if (!Double.isFinite(vectorWeight) || !Double.isFinite(keywordWeight)
                || vectorWeight < 0.0D || vectorWeight > 1.0D
                || keywordWeight < 0.0D || keywordWeight > 1.0D
                || Math.abs(vectorWeight + keywordWeight - 1.0D) > 0.000_001D) {
            throw new IllegalArgumentException("ACL-safe hybrid weights must be finite, within [0,1], and sum to 1");
        }
    }

    private static List<VectorSearchResult> translate(SearchResp response, Double minScore, boolean includeCandidateVectors) {
        if (response == null || response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
            return List.of();
        }
        double threshold = minScore == null ? 0.0D : minScore;
        List<VectorSearchResult> results = new ArrayList<>();
        for (SearchResp.SearchResult hit : response.getSearchResults().getFirst()) {
            double score = hit.getScore() == null ? 0.0D : hit.getScore().doubleValue();
            if (score < threshold || hit.getId() == null) continue;
            Map<String, Object> entity = hit.getEntity() == null ? Map.of() : hit.getEntity();
            String content = String.valueOf(entity.getOrDefault("content", ""));
            float[] vector = includeCandidateVectors ? vector(entity.get(DENSE_VECTOR_FIELD)) : null;
            if (includeCandidateVectors && vector == null) {
                throw new UnsupportedOperationException("Milvus hybrid candidate vector projection returned no vector");
            }
            results.add(VectorSearchResult.of(String.valueOf(hit.getId()), content, score, vector)
                    .setMetadata(metadata(entity.get("metadata"))));
        }
        return List.copyOf(results);
    }

    private static List<String> outputFields(boolean includeCandidateVectors) {
        if (!includeCandidateVectors) return OUTPUT_FIELDS;
        return List.of("content", "metadata", DENSE_VECTOR_FIELD);
    }

    private static float[] vector(Object raw) {
        if (raw instanceof float[] vector) return vector.clone();
        if (!(raw instanceof List<?> values) || values.isEmpty()) return null;
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof Number number)) return null;
            vector[index] = number.floatValue();
        }
        return vector;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(Object raw) {
        if (raw == null || raw.toString().isBlank()) return new LinkedHashMap<>();
        try {
            Map<String, Object> parsed = JsonUtils.getInstance().deserialize(raw.toString(), Map.class);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (RuntimeException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
