package cn.richie696.component.vector.service.impl;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.model.VectorRecord;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * Tencent VectorDB dense-only write adapter.
 *
 * <p>Core keeps {@code metadata.content} for compatibility, whereas the
 * Tencent Spring AI store owns {@code content} as a reserved document field.
 * Remove only that duplicate metadata entry before delegating to the SDK; the
 * actual document body remains available through {@link Document#getText()}.</p>
 */
public final class TencentVectorDbStoreManagedVectorService extends StoreManagedVectorService {

    public TencentVectorDbStoreManagedVectorService(
            RerankService rerankService,
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            Map<String, String> managedIndexes) {
        super(rerankService, vectorStore, embeddingModel, managedIndexes);
    }

    @Override
    protected void writeStoreManagedRecords(String indexName, List<VectorRecord> records) {
        validateIndexName(indexName);
        vectorStore.add(records.stream()
                .map(record -> {
                    Document document = toAiDocument(record, null);
                    document.getMetadata().remove("content");
                    return document;
                })
                .toList());
    }
}
