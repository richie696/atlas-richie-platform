/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.service.impl;

import cn.richie696.ai.vectorstore.vikingdb.VikingDbVectorStore;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbResourceRef;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchAdvanceOptions;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchCommonOptions;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchHit;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchResponse;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbVectorSearchRequest;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.VikingDbVectorFilterAdapter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.observation.VectorStoreObservationEvent;
import cn.richie696.component.vector.query.ProviderQueryOptions;
import cn.richie696.component.vector.query.ResolvedVectorQuery;
import cn.richie696.component.vector.query.VectorConsistencyPreference;
import cn.richie696.component.vector.query.VectorParameterDisposition;
import cn.richie696.component.vector.query.VectorParameterEvidence;
import cn.richie696.component.vector.query.VectorParameterSource;
import cn.richie696.component.vector.query.VectorQueryDefaults;
import cn.richie696.component.vector.query.VectorQueryErrorCode;
import cn.richie696.component.vector.query.VectorQueryExecutionGuard;
import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.VectorQueryResolver;
import cn.richie696.component.vector.query.VectorQueryValidationException;
import cn.richie696.component.vector.query.VectorSearchExecution;
import cn.richie696.component.vector.query.VectorSearchExecutionReceipt;
import cn.richie696.component.vector.query.vikingdb.VikingDbQueryOptions;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorStoreId;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Store-bound advanced entry point backed by atlas-richie-ai-vikingdb-store. */
public final class VikingDbAdvancedSearchOperations implements VectorAdvancedSearchOperations {

    private final VikingDbVectorStore store;
    private final EmbeddingModel embeddingModel;
    private final VectorStoreId storeId;
    private final String logicalIndex;
    private final VectorQueryResolver resolver;
    private final VectorQueryExecutionGuard executionGuard = new VectorQueryExecutionGuard();

    public VikingDbAdvancedSearchOperations(VikingDbVectorStore store, EmbeddingModel embeddingModel,
                                            VectorStoreId storeId, String logicalIndex,
                                            VectorQueryDefaults providerDefaults,
                                            VectorQueryDefaults storeDefaults) {
        this(store, embeddingModel, storeId, logicalIndex, providerDefaults, storeDefaults, null);
    }

    public VikingDbAdvancedSearchOperations(VikingDbVectorStore store, EmbeddingModel embeddingModel,
                                            VectorStoreId storeId, String logicalIndex,
                                            VectorQueryDefaults providerDefaults,
                                            VectorQueryDefaults storeDefaults,
                                            VectorScoreSemantics scoreSemantics) {
        this.store = store;
        this.embeddingModel = embeddingModel;
        this.storeId = storeId;
        this.logicalIndex = logicalIndex;
        this.resolver = new VectorQueryResolver(providerDefaults, storeDefaults, scoreSemantics);
    }

    @Override
    public VectorSearchExecution search(VectorQueryRequest request) {
        ResolvedVectorQuery query = resolver.resolve(request);
        VikingDbQueryOptions options = query.providerOptions() == null
                ? VikingDbQueryOptions.empty() : queryOptions(query.providerOptions());
        rejectUnsupportedCommonOptions(query);

        float[] denseVector = null;
        if (options.mode() != VikingDbVectorSearchRequest.Mode.SPARSE) {
            denseVector = embeddingModel.embed(query.query());
            if (denseVector.length != store.getEmbeddingDimension()) {
                throw new VectorQueryValidationException(VectorQueryErrorCode.INVALID_VALUE,
                        "VikingDB query embedding dimension does not match the bound Store");
            }
        }
        if (options.mode() != VikingDbVectorSearchRequest.Mode.DENSE && options.sparseVector().isEmpty()) {
            throw new VectorQueryValidationException(VectorQueryErrorCode.INVALID_VALUE,
                    "VikingDB sparse or hybrid query requires sparseVector in VikingDbQueryOptions");
        }

        Filter.Expression filter = query.filter() == null ? null : VikingDbVectorFilterAdapter.toSpring(query.filter());
        VikingDbSearchCommonOptions common = withLimit(options.common(), query.candidateLimit(), query.returnFields());
        VikingDbVectorSearchRequest nativeRequest = VikingDbVectorSearchRequest.builder()
                .mode(options.mode())
                .denseVector(denseVector)
                .sparseVector(options.sparseVector())
                .queryFilter(filter)
                .common(common)
                .advance(options.advance())
                .build();
        VikingDbSearchResponse response = executionGuard.execute(query.timeout(), () -> store.search(nativeRequest));
        List<VectorSearchResult> candidates = response.hits().stream().map(VikingDbAdvancedSearchOperations::toResult).toList();
        return new VectorSearchExecution(candidates.stream().limit(query.topK()).toList(),
                receipt(request, query, options, response));
    }

    private static VikingDbQueryOptions queryOptions(ProviderQueryOptions options) {
        if (!(options instanceof VikingDbQueryOptions viking) || !"1.0".equals(options.version())) {
            throw new VectorQueryValidationException(VectorQueryErrorCode.UNSUPPORTED_OPTION,
                    "unsupported provider query extension for VikingDB");
        }
        return viking;
    }

    private static void rejectUnsupportedCommonOptions(ResolvedVectorQuery query) {
        if (!query.returnFields().isEmpty()) {
            // The AI adapter accepts outputFields, so this is intentionally handled below.
        }
        if (query.consistency() != VectorConsistencyPreference.PROVIDER_DEFAULT) {
            throw new VectorQueryValidationException(VectorQueryErrorCode.UNSUPPORTED_OPTION,
                    "VikingDB does not expose per-query consistency overrides");
        }
    }

    private static VikingDbSearchCommonOptions withLimit(VikingDbSearchCommonOptions source, int limit,
                                                          java.util.Set<String> returnFields) {
        List<String> fields = returnFields.isEmpty()
                ? source.outputFields()
                : List.copyOf(returnFields);
        return VikingDbSearchCommonOptions.builder()
                .limit(limit).offset(source.offset()).partition(source.partition())
                .outputFields(fields).returnSchema(source.returnSchema())
                .returnDownloadUrl(source.returnDownloadUrl())
                .returnAnalyzedResult(source.returnAnalyzedResult())
                .returnDetailInfo(source.returnDetailInfo()).build();
    }

    private VectorSearchExecutionReceipt receipt(VectorQueryRequest request, ResolvedVectorQuery query,
                                                 VikingDbQueryOptions options, VikingDbSearchResponse response) {
        List<VectorParameterEvidence> evidence = new ArrayList<>();
        evidence.add(parameter("topK", request.topK(), resolver.configured().topK(), query.topK(), query.topK(),
                resolver.sourceFor("topK", request.topK() != null), "framework-result-limit"));
        evidence.add(parameter("candidateLimit", request.candidateLimit(), resolver.configured().candidateLimit(),
                query.candidateLimit(), query.candidateLimit(), resolver.sourceFor("candidateLimit", request.candidateLimit() != null),
                "vikingdb-sdk-limit"));
        evidence.add(parameter("minScore", request.minScore(), resolver.configured().minScore(), query.minScore(),
                query.minScore(), resolver.sourceFor("minScore", request.minScore() != null), "vikingdb-adapter-jvm-post-filter"));
        evidence.add(parameter("thresholdKind", request.thresholdKind(), null, query.thresholdKind(),
                query.thresholdKind(),
                request.thresholdKind() == null ? VectorParameterSource.CORE_DEFAULT : VectorParameterSource.REQUEST,
                "vikingdb-adapter-score-semantics"));
        evidence.add(parameter("mode", null, null, options.mode(), options.mode(), VectorParameterSource.REQUEST,
                "vikingdb-searchByVector"));
        evidence.add(parameter("sparseVector", null, null, options.sparseVector().isEmpty() ? "absent" : "present",
                options.sparseVector().isEmpty() ? "absent" : "applied", VectorParameterSource.REQUEST,
                "vikingdb-searchByVector"));
        if (response.execution() != null) {
            response.execution().effectiveOptions().forEach((name, value) -> evidence.add(parameter(
                    name, null, null, value, value, VectorParameterSource.PROVIDER_DEFAULT, "vikingdb-adapter-evidence")));
        }
        return new VectorSearchExecutionReceipt(storeId, VectorProvider.VIKINGDB, "1.0", "1.0", "1.0", "1.0",
                VectorStoreObservationEvent.fingerprint(logicalIndex), evidence);
    }

    private static VectorParameterEvidence parameter(String name, Object requested, Object configured,
                                                      Object effective, Object applied, VectorParameterSource source,
                                                      String proof) {
        return new VectorParameterEvidence(name, value(requested), value(configured), value(effective), value(applied),
                source, requested == null ? VectorParameterDisposition.DEFAULTED : VectorParameterDisposition.APPLIED, proof);
    }

    private static String value(Object value) {
        return value == null ? null : value.toString();
    }

    private static VectorSearchResult toResult(VikingDbSearchHit hit) {
        Map<String, Object> fields = new LinkedHashMap<>(hit.fields());
        Object content = fields.remove(VikingDbVectorStore.CONTENT_FIELD_NAME);
        return VectorSearchResult.of(hit.id(), content == null ? "" : String.valueOf(content), hit.score())
                .setMetadata(fields);
    }
}
