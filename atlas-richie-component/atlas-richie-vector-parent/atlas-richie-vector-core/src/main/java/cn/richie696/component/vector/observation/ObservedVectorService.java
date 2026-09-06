/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import cn.richie696.component.vector.bulk.BulkOperationEvent;
import cn.richie696.component.vector.diagnostics.VectorCategorizedError;
import cn.richie696.component.vector.diagnostics.VectorErrorCategory;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.VectorService;
import cn.richie696.component.vector.topology.VectorStoreId;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Package-private decorator used only when an observation hook is configured. */
public final class ObservedVectorService implements VectorService {

    private final VectorService delegate;
    private final VectorStoreId storeId;
    private final VectorProvider provider;
    private final Set<String> capabilities;
    private final VectorStoreObservationHook hook;

    public ObservedVectorService(
            VectorService delegate,
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
    public String upsert(VectorRecord record) {
        return observe(VectorStoreOperation.UPSERT, record == null ? null : record.getIndexName(),
                () -> delegate.upsert(record));
    }

    @Override
    public void deleteById(String indexName, String vectorId) {
        observeVoid(VectorStoreOperation.DELETE_ONE, indexName, () -> delegate.deleteById(indexName, vectorId));
    }

    @Override
    public void deleteByIds(String indexName, Collection<String> vectorIds) {
        observeVoid(VectorStoreOperation.DELETE_BATCH, indexName, () -> delegate.deleteByIds(indexName, vectorIds));
    }

    @Override
    public List<VectorSearchResult> searchByText(
            String indexName, String text, int limit, SearchOptions options) {
        VectorStoreOperation operation = options == null || Boolean.TRUE.equals(options.getRerank())
                ? VectorStoreOperation.SEARCH_RERANK : VectorStoreOperation.SEARCH_TEXT;
        return observe(operation, indexName,
                () -> delegate.searchByText(indexName, text, limit, options));
    }

    @Override
    public List<VectorSearchResult> searchByImage(
            String indexName, byte[] image, String mimeType, int limit, double minScore) {
        return observe(VectorStoreOperation.SEARCH_IMAGE, indexName,
                () -> delegate.searchByImage(indexName, image, mimeType, limit, minScore));
    }

    @Override
    public List<VectorSearchResult> searchByImage(
            String indexName, Path imagePath, String mimeType, int limit) {
        return observe(VectorStoreOperation.SEARCH_IMAGE, indexName,
                () -> delegate.searchByImage(indexName, imagePath, mimeType, limit));
    }

    @Override
    public Flux<BulkOperationEvent> upsertAll(String indexName, Flux<VectorRecord> records) {
        return observeFlux(VectorStoreOperation.BULK_UPSERT, indexName,
                () -> delegate.upsertAll(indexName, records));
    }

    @Override
    public Flux<BulkOperationEvent> deleteAll(String indexName, Flux<String> vectorIds) {
        return observeFlux(VectorStoreOperation.BULK_DELETE, indexName,
                () -> delegate.deleteAll(indexName, vectorIds));
    }

    private <T> T observe(VectorStoreOperation operation, String indexName, Supplier<T> action) {
        long started = System.nanoTime();
        try {
            T result = action.get();
            emit(operation, VectorStoreOperationResult.SUCCESS, indexName, started, VectorErrorCategory.NONE);
            return result;
        } catch (RuntimeException error) {
            emit(operation, VectorStoreOperationResult.FAILURE, indexName, started, category(error));
            throw error;
        }
    }

    private void observeVoid(VectorStoreOperation operation, String indexName, Runnable action) {
        observe(operation, indexName, () -> {
            action.run();
            return null;
        });
    }

    private Flux<BulkOperationEvent> observeFlux(
            VectorStoreOperation operation,
            String indexName,
            Supplier<Flux<BulkOperationEvent>> action) {
        return Flux.defer(() -> {
            long started = System.nanoTime();
            AtomicBoolean terminal = new AtomicBoolean();
            try {
                return action.get()
                        .doOnComplete(() -> emitOnce(terminal, operation,
                                VectorStoreOperationResult.SUCCESS, indexName, started, VectorErrorCategory.NONE))
                        .doOnError(error -> emitOnce(terminal, operation,
                                VectorStoreOperationResult.FAILURE, indexName, started, category(error)))
                        .doOnCancel(() -> emitOnce(terminal, operation,
                                VectorStoreOperationResult.CANCELLED, indexName, started, VectorErrorCategory.NONE));
            } catch (RuntimeException error) {
                emitOnce(terminal, operation, VectorStoreOperationResult.FAILURE,
                        indexName, started, category(error));
                return Flux.error(error);
            }
        });
    }

    private void emitOnce(
            AtomicBoolean terminal,
            VectorStoreOperation operation,
            VectorStoreOperationResult result,
            String indexName,
            long started,
            VectorErrorCategory category) {
        if (terminal.compareAndSet(false, true)) {
            emit(operation, result, indexName, started, category);
        }
    }

    private void emit(
            VectorStoreOperation operation,
            VectorStoreOperationResult result,
            String indexName,
            long started,
            VectorErrorCategory category) {
        VectorStoreObservationHook.safeEmit(hook, new VectorStoreObservationEvent(
                storeId,
                provider,
                operation,
                result,
                Duration.ofNanos(Math.max(0, System.nanoTime() - started)),
                VectorStoreObservationEvent.fingerprint(indexName),
                capabilities,
                category));
    }

    private static VectorErrorCategory category(Throwable error) {
        return error instanceof VectorCategorizedError categorized
                ? categorized.errorCategory()
                : VectorErrorCategory.OPERATION_FAILED;
    }
}
