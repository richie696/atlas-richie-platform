package cn.richie696.component.vector.service.impl;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.model.VectorRecord;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

/**
 * DashVector-specific store-managed write adapter.
 *
 * <p>The shared document mapper retains {@code metadata.content} for backward
 * compatibility. DashVector reserves that field for the document body, so it
 * must be removed before the upstream DashVector store performs its own
 * content-field mapping.</p>
 */
public final class DashVectorStoreManagedVectorService extends StoreManagedVectorService {

    public DashVectorStoreManagedVectorService(
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
