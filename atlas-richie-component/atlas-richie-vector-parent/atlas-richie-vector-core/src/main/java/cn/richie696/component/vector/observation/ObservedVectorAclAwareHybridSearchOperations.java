/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import cn.richie696.component.vector.diagnostics.VectorCategorizedError;
import cn.richie696.component.vector.diagnostics.VectorErrorCategory;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.topology.VectorStoreId;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Payload-free observation decorator for ACL-safe hybrid requests. */
public final class ObservedVectorAclAwareHybridSearchOperations
        implements VectorAclAwareHybridSearchOperations {

    private final VectorAclAwareHybridSearchOperations delegate;
    private final VectorStoreId storeId;
    private final VectorProvider provider;
    private final Set<String> capabilities;
    private final VectorStoreObservationHook hook;

    public ObservedVectorAclAwareHybridSearchOperations(
            VectorAclAwareHybridSearchOperations delegate,
            VectorStoreId storeId,
            VectorProvider provider,
            Set<String> capabilities,
            VectorStoreObservationHook hook) {
        this.delegate = delegate;
        this.storeId = storeId;
        this.provider = provider;
        this.capabilities = Set.copyOf(capabilities);
        this.hook = hook;
    }

    @Override
    public List<VectorSearchResult> hybridSearch(
            String indexName, String text, String keywordQuery, int limit, HybridSearchOptions options) {
        throw new UnsupportedOperationException(
                "ACL-safe hybrid capability requires an explicit structured filter");
    }

    @Override
    public List<VectorSearchResult> hybridSearch(
            String indexName,
            String text,
            String keywordQuery,
            int limit,
            HybridSearchOptions options,
            VectorFilter filter) {
        long started = System.nanoTime();
        try {
            List<VectorSearchResult> results = delegate.hybridSearch(
                    indexName, text, keywordQuery, limit, options, filter);
            emit(VectorStoreOperationResult.SUCCESS, indexName, started, VectorErrorCategory.NONE);
            return results;
        } catch (RuntimeException error) {
            VectorErrorCategory category = error instanceof VectorCategorizedError categorized
                    ? categorized.errorCategory() : VectorErrorCategory.OPERATION_FAILED;
            emit(VectorStoreOperationResult.FAILURE, indexName, started, category);
            throw error;
        }
    }

    private void emit(
            VectorStoreOperationResult result,
            String indexName,
            long started,
            VectorErrorCategory category) {
        VectorStoreObservationHook.safeEmit(hook, new VectorStoreObservationEvent(
                storeId, provider, VectorStoreOperation.SEARCH_HYBRID, result,
                Duration.ofNanos(Math.max(0L, System.nanoTime() - started)),
                VectorStoreObservationEvent.fingerprint(indexName), capabilities, category,
                delegate instanceof HybridSearchExecutionMode mode ? mode.hybridExecutionMode() : "unknown"));
    }
}
