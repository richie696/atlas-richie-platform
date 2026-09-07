/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.observation.VectorStoreObservationEvent;
import cn.richie696.component.vector.query.ProviderQueryOptions;
import cn.richie696.component.vector.query.ResolvedVectorQuery;
import cn.richie696.component.vector.query.VectorConsistencyPreference;
import cn.richie696.component.vector.query.VectorParameterDisposition;
import cn.richie696.component.vector.query.VectorParameterEvidence;
import cn.richie696.component.vector.query.VectorParameterSource;
import cn.richie696.component.vector.query.VectorQueryDefaults;
import cn.richie696.component.vector.query.VectorQueryExecutionGuard;
import cn.richie696.component.vector.query.VectorQueryErrorCode;
import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.VectorQueryResolver;
import cn.richie696.component.vector.query.VectorQueryValidationException;
import cn.richie696.component.vector.query.VectorResultDiversifier;
import cn.richie696.component.vector.query.VectorSearchExecution;
import cn.richie696.component.vector.query.VectorSearchExecutionReceipt;
import cn.richie696.component.vector.query.milvus.MilvusQueryOptions;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorStoreId;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Store-bound typed Milvus query entry point. */
public final class MilvusAdvancedSearchOperations implements VectorAdvancedSearchOperations {

    private final MilvusVectorServiceImpl service;
    private final VectorStoreId storeId;
    private final String logicalIndex;
    private final String indexType;
    private final VectorQueryResolver resolver;
    private final VectorQueryExecutionGuard executionGuard = new VectorQueryExecutionGuard();

    public MilvusAdvancedSearchOperations(
            MilvusVectorServiceImpl service,
            VectorStoreId storeId,
            String logicalIndex,
            String indexType,
            VectorQueryDefaults providerDefaults,
            VectorQueryDefaults storeDefaults) {
        this(service, storeId, logicalIndex, indexType, providerDefaults, storeDefaults, null);
    }

    public MilvusAdvancedSearchOperations(
            MilvusVectorServiceImpl service,
            VectorStoreId storeId,
            String logicalIndex,
            String indexType,
            VectorQueryDefaults providerDefaults,
            VectorQueryDefaults storeDefaults,
            VectorScoreSemantics scoreSemantics) {
        this.service = service;
        this.storeId = storeId;
        this.logicalIndex = logicalIndex;
        this.indexType = indexType.toUpperCase(Locale.ROOT);
        this.resolver = new VectorQueryResolver(providerDefaults, storeDefaults, scoreSemantics);
    }

    @Override
    public VectorSearchExecution search(VectorQueryRequest request) {
        ResolvedVectorQuery query = resolver.resolve(request);
        rejectUnsupportedCommonOptions(query);
        Map<String, Integer> providerParameters = providerParameters(query.providerOptions());
        SearchOptions options = SearchOptions.builder()
                .rerank(false)
                .minScore(query.minScore())
                .filter(query.filter())
                .providerSearchParameters(providerParameters)
                .includeCandidateVectors(query.diversification().requiresCandidateVectors())
                .build();
        List<VectorSearchResult> candidates = executionGuard.execute(query.timeout(), () -> service.searchByText(
                logicalIndex, query.query(), query.candidateLimit(), options));
        List<VectorSearchResult> results = VectorResultDiversifier.apply(
                candidates, query.topK(), query.diversification());
        return new VectorSearchExecution(results, receipt(request, query, providerParameters));
    }

    private void rejectUnsupportedCommonOptions(ResolvedVectorQuery query) {
        if (!query.returnFields().isEmpty()) {
            throw unsupported("Milvus adapter does not expose return field projection");
        }
        if (query.consistency() != VectorConsistencyPreference.PROVIDER_DEFAULT) {
            throw unsupported("Milvus adapter does not expose per-query consistency overrides");
        }
    }

    private Map<String, Integer> providerParameters(ProviderQueryOptions options) {
        if (options == null) return Map.of();
        if (!(options instanceof MilvusQueryOptions milvus) || !"1.0".equals(options.version())) {
            throw unsupported("unsupported provider query extension for Milvus");
        }
        Map<String, Integer> parameters = new LinkedHashMap<>();
        if (milvus.ef() != null) {
            if (!"HNSW".equals(indexType)) {
                throw unsupported("Milvus ef is only valid for HNSW indexes");
            }
            parameters.put("milvus.ef", milvus.ef());
        }
        if (milvus.nprobe() != null) {
            if (!indexType.startsWith("IVF_")) {
                throw unsupported("Milvus nprobe is only valid for IVF indexes");
            }
            parameters.put("milvus.nprobe", milvus.nprobe());
        }
        return Map.copyOf(parameters);
    }

    private VectorSearchExecutionReceipt receipt(
            VectorQueryRequest request, ResolvedVectorQuery query, Map<String, Integer> providerParameters) {
        List<VectorParameterEvidence> evidence = new ArrayList<>();
        evidence.add(parameter("topK", request.topK(), resolver.configured().topK(), query.topK(), query.topK(),
                resolver.sourceFor("topK", request.topK() != null), "framework-result-limit"));
        evidence.add(parameter("candidateLimit", request.candidateLimit(), resolver.configured().candidateLimit(),
                query.candidateLimit(), query.candidateLimit(),
                resolver.sourceFor("candidateLimit", request.candidateLimit() != null), "milvus-sdk-top-k"));
        evidence.add(parameter("minScore", request.minScore(), resolver.configured().minScore(), query.minScore(),
                query.minScore(), resolver.sourceFor("minScore", request.minScore() != null),
                "milvus-adapter-jvm-post-filter"));
        evidence.add(parameter("thresholdKind", request.thresholdKind(), null, query.thresholdKind(),
                query.thresholdKind(),
                request.thresholdKind() == null ? VectorParameterSource.CORE_DEFAULT : VectorParameterSource.REQUEST,
                "milvus-adapter-score-semantics"));
        evidence.add(parameter("timeout", request.timeout(), resolver.configured().timeout(), query.timeout(),
                query.timeout(), resolver.sourceFor("timeout", request.timeout() != null), "framework-timeout-guard"));
        providerParameters.forEach((name, value) -> evidence.add(parameter(
                name, value, null, value, value, VectorParameterSource.REQUEST, "milvus-sdk-search-params")));
        return new VectorSearchExecutionReceipt(
                storeId, VectorProvider.MILVUS, "1.0", "1.0", "1.0", "1.0",
                VectorStoreObservationEvent.fingerprint(logicalIndex), evidence);
    }

    private static VectorParameterEvidence parameter(
            String name, Object requested, Object configured, Object effective, Object applied,
            VectorParameterSource source, String proof) {
        return new VectorParameterEvidence(
                name, value(requested), value(configured), value(effective), value(applied),
                source,
                requested == null ? VectorParameterDisposition.DEFAULTED : VectorParameterDisposition.APPLIED,
                proof);
    }

    private static String value(Object value) {
        return value == null ? null : value.toString();
    }

    private static VectorQueryValidationException unsupported(String message) {
        return new VectorQueryValidationException(VectorQueryErrorCode.UNSUPPORTED_OPTION, message);
    }
}
