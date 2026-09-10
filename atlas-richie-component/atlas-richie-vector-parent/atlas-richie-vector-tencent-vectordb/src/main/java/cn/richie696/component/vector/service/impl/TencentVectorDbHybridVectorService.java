package cn.richie696.component.vector.service.impl;

import cn.richie696.ai.vectorstore.tencentvectordb.TencentVectorDbVectorStore;
import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.service.SparseVectorizer;
import com.tencent.tcvectordb.model.DocField;
import com.tencent.tcvectordb.model.param.dml.InsertParam;
import com.tencent.tcvectordb.model.param.entity.AffectRes;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps dense and sparse vectors atomic at Tencent VectorDB's upsert boundary. */
public final class TencentVectorDbHybridVectorService extends StoreManagedVectorService {

    private static final int MAX_BATCH_SIZE = 1_000;

    private final TencentVectorDbVectorStore vectorDb;
    private final SparseVectorizer sparseVectorizer;

    public TencentVectorDbHybridVectorService(
            RerankService rerankService,
            TencentVectorDbVectorStore vectorDb,
            EmbeddingModel embeddingModel,
            Map<String, String> managedIndexes,
            SparseVectorizer sparseVectorizer) {
        super(rerankService, vectorDb, embeddingModel, managedIndexes);
        this.vectorDb = vectorDb;
        this.sparseVectorizer = sparseVectorizer;
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
            List<com.tencent.tcvectordb.model.Document> nativeDocuments = documents.subList(from, to).stream()
                    .map(this::toNativeDocument)
                    .toList();
            AffectRes response = vectorDb.upsert(InsertParam.newBuilder()
                    .withDocuments(nativeDocuments)
                    .build());
            if (response == null || response.getCode() != 0) {
                throw new IllegalStateException("Tencent VectorDB hybrid upsert failed");
            }
        }
    }

    private com.tencent.tcvectordb.model.Document toNativeDocument(Document document) {
        Object rawEmbedding = document.getMetadata().get("embedding");
        if (!(rawEmbedding instanceof float[] embedding)) {
            throw new IllegalStateException("Tencent VectorDB hybrid write requires a dense embedding");
        }
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        metadata.remove("embedding");
        metadata.remove("content");
        List<DocField> fields = new ArrayList<>();
        fields.add(new DocField(TencentVectorDbVectorStore.CONTENT_FIELD_NAME,
                document.getText() == null ? "" : document.getText()));
        metadata.forEach((name, value) -> fields.add(new DocField(name, value)));
        List<Pair<Long, Float>> sparse = sparseVectorizer.encode(document.getText()).coordinates().entrySet().stream()
                .map(entry -> Pair.of(entry.getKey(), entry.getValue()))
                .toList();
        return com.tencent.tcvectordb.model.Document.newBuilder()
                .withId(document.getId())
                .withVector(floats(embedding))
                .withSparseVector(sparse)
                .addDocFields(fields)
                .build();
    }

    private static List<Float> floats(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }
}
