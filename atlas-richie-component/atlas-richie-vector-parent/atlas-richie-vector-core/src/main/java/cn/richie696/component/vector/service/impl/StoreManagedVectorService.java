/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.vector.service.impl;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.model.VectorRecord;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * Minimal adapter for stores whose Spring AI implementation owns embedding and writes.
 * Provider modules extend this only when no provider-specific data-plane behavior is needed.
 */
public class StoreManagedVectorService extends AbstractVectorService {

    private final Map<String, String> managedIndexes;

    public StoreManagedVectorService(RerankService rerankService, VectorStore vectorStore,
                                     EmbeddingModel embeddingModel, Map<String, String> managedIndexes) {
        super(rerankService, vectorStore, embeddingModel);
        this.managedIndexes = Map.copyOf(managedIndexes == null ? Map.of() : managedIndexes);
    }

    @Override
    protected void validateIndexName(String indexName) {
        super.validateIndexName(indexName);
        if (!managedIndexes.isEmpty() && !managedIndexes.containsKey(indexName)) {
            throw new IllegalArgumentException("index is not declared by this Store: " + indexName);
        }
    }

    @Override
    protected List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore) {
        throw new UnsupportedOperationException("This provider adapter supports text search only");
    }

    @Override
    protected void addEmbeddings(String indexName, List<Document> docs) {
        validateIndexName(indexName);
        vectorStore.add(docs);
    }

    @Override
    protected boolean usesStoreManagedEmbedding() {
        return true;
    }

    @Override
    protected void writeStoreManagedRecords(String indexName, List<VectorRecord> records) {
        validateIndexName(indexName);
        vectorStore.add(records.stream().map(record -> toAiDocument(record, null)).toList());
    }

    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        validateIndexName(indexName);
        vectorStore.delete(ids);
    }
}
