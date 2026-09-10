package cn.richie696.component.vector.service.impl;

import cn.richie696.ai.vectorstore.dashvector.DashVectorVectorStore;
import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.service.SparseVectorizer;
import com.aliyun.dashvector.models.Doc;
import com.aliyun.dashvector.models.requests.UpsertDocRequest;
import com.aliyun.dashvector.models.responses.Response;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the dense and sparse representation in one DashVector upsert.
 *
 * <p>The upstream Spring AI adapter owns dense embedding and deliberately has no
 * sparse-vectorizer dependency.  Hybrid Stores therefore use this provider-local
 * data-plane adapter so a record can never be visible to dense recall without its
 * corresponding sparse representation.</p>
 */
public final class DashVectorHybridVectorService extends StoreManagedVectorService {

    private static final int MAX_BATCH_SIZE = 1_024;

    private final DashVectorVectorStore dashVector;
    private final SparseVectorizer sparseVectorizer;
    private final String partition;

    public DashVectorHybridVectorService(
            RerankService rerankService,
            DashVectorVectorStore dashVector,
            EmbeddingModel embeddingModel,
            Map<String, String> managedIndexes,
            SparseVectorizer sparseVectorizer,
            String partition) {
        super(rerankService, dashVector, embeddingModel, managedIndexes);
        this.dashVector = dashVector;
        this.sparseVectorizer = sparseVectorizer;
        this.partition = partition;
    }

    @Override
    protected boolean usesStoreManagedEmbedding() {
        return false;
    }

    @Override
    protected void addEmbeddings(String indexName, List<Document> documents) {
        validateIndexName(indexName);
        for (int from = 0; from < documents.size(); from += MAX_BATCH_SIZE) {
            int to = Math.min(from + MAX_BATCH_SIZE, documents.size());
            List<Doc> nativeDocuments = documents.subList(from, to).stream()
                    .map(this::toNativeDocument)
                    .toList();
            Response<?> response = dashVector.upsert(UpsertDocRequest.builder()
                    .docs(nativeDocuments)
                    .partition(partition)
                    .build());
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException(failure("DashVector hybrid upsert", response));
            }
        }
    }

    private Doc toNativeDocument(Document document) {
        Object rawEmbedding = document.getMetadata().get("embedding");
        if (!(rawEmbedding instanceof float[] embedding)) {
            throw new IllegalStateException("DashVector hybrid write requires a dense embedding");
        }
        Map<String, Object> fields = new LinkedHashMap<>(document.getMetadata());
        fields.remove("embedding");
        fields.remove("content");
        fields.put(DashVectorVectorStore.CONTENT_FIELD_NAME,
                document.getText() == null ? "" : document.getText());
        return Doc.builder()
                .id(document.getId())
                .vector(com.aliyun.dashvector.models.Vector.builder().value(floats(embedding)).build())
                .sparseVector(sparseVectorizer.encode(document.getText()).coordinates())
                .fields(fields)
                .build();
    }

    private static List<Float> floats(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }

    private static String failure(String operation, Response<?> response) {
        if (response == null) {
            return operation + " failed: empty provider response";
        }
        return operation + " failed: code=" + response.getCode()
                + ", message=" + String.valueOf(response.getMessage())
                + ", requestId=" + String.valueOf(response.getRequestId());
    }
}
