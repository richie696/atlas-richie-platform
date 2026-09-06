/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import cn.richie696.component.vector.diagnostics.VectorCategorizedError;
import cn.richie696.component.vector.diagnostics.VectorErrorCategory;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.query.VectorQueryRequest;
import cn.richie696.component.vector.query.VectorSearchExecution;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.topology.VectorStoreId;

import java.time.Duration;
import java.util.Set;

/** Payload-free observation decorator for the typed advanced query entry point. */
public final class ObservedVectorAdvancedSearchOperations implements VectorAdvancedSearchOperations {

    private final VectorAdvancedSearchOperations delegate;
    private final VectorStoreId storeId;
    private final VectorProvider provider;
    private final String logicalIndex;
    private final Set<String> capabilities;
    private final VectorStoreObservationHook hook;

    public ObservedVectorAdvancedSearchOperations(
            VectorAdvancedSearchOperations delegate,
            VectorStoreId storeId,
            VectorProvider provider,
            String logicalIndex,
            Set<String> capabilities,
            VectorStoreObservationHook hook) {
        this.delegate = delegate;
        this.storeId = storeId;
        this.provider = provider;
        this.logicalIndex = logicalIndex;
        this.capabilities = Set.copyOf(capabilities);
        this.hook = hook;
    }

    @Override
    public VectorSearchExecution search(VectorQueryRequest request) {
        VectorStoreOperation operation = request != null && request.diversification() != null
                && request.diversification().mmrEnabled()
                ? VectorStoreOperation.SEARCH_MMR : VectorStoreOperation.SEARCH_TUNED;
        long started = System.nanoTime();
        try {
            VectorSearchExecution execution = delegate.search(request);
            emit(operation, VectorStoreOperationResult.SUCCESS, started, VectorErrorCategory.NONE);
            return execution;
        } catch (RuntimeException error) {
            VectorErrorCategory category = error instanceof VectorCategorizedError categorized
                    ? categorized.errorCategory() : VectorErrorCategory.OPERATION_FAILED;
            emit(operation, VectorStoreOperationResult.FAILURE, started, category);
            throw error;
        }
    }

    private void emit(
            VectorStoreOperation operation,
            VectorStoreOperationResult result,
            long started,
            VectorErrorCategory category) {
        VectorStoreObservationHook.safeEmit(hook, new VectorStoreObservationEvent(
                storeId, provider, operation, result,
                Duration.ofNanos(Math.max(0L, System.nanoTime() - started)),
                VectorStoreObservationEvent.fingerprint(logicalIndex), capabilities, category));
    }
}
