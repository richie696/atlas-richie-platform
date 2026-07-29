package cn.richie696.component.vector.service.impl;

import cn.richie696.ai.vectorstore.vikingdb.VikingDbVectorStore;
import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.config.VikingDbConfig;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.service.VectorService;
import com.volcengine.vikingdb.runtime.exception.ApiClientException;
import com.volcengine.vikingdb.runtime.exception.VectorApiException;
import com.volcengine.vikingdb.runtime.vector.model.request.SearchByVectorRequest;
import com.volcengine.vikingdb.runtime.vector.model.response.DataApiResponse;
import com.volcengine.vikingdb.runtime.vector.model.response.SearchItem;
import com.volcengine.vikingdb.runtime.vector.model.response.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VikingDB 平台向量服务适配。
 *
 * <p>VikingDB 的 Spring AI Store 绑定单个 collection/index；因此本服务的 indexName
 * 必须与 {@code platform.component.vector.vikingdb.collection-name} 一致，避免把同一
 * Store 的数据误写入另一逻辑索引。</p>
 */
@Slf4j
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "vikingdb")
public class VikingDbVectorServiceImpl extends AbstractVectorService implements VectorService {

    private final VikingDbVectorStore vikingDbVectorStore;
    private final VikingDbConfig config;

    @Autowired
    public VikingDbVectorServiceImpl(@Autowired(required = false) RerankService rerankService,
                                     VectorStore vectorStore,
                                     @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                     VikingDbConfig config) {
        super(rerankService, vectorStore, embeddingModel);
        if (!(vectorStore instanceof VikingDbVectorStore store)) {
            throw new IllegalStateException("VikingDB provider 需要 VikingDbVectorStore");
        }
        this.vikingDbVectorStore = store;
        this.config = config;
    }

    @Override
    protected List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore) {
        assertConfiguredIndex(indexName);
        List<Float> denseVector = new ArrayList<>(vector.length);
        for (float value : vector) {
            denseVector.add(value);
        }
        DataApiResponse<SearchResult> response;
        try {
            response = vikingDbVectorStore.getNativeClient().orElseThrow()
                    .searchByVector(SearchByVectorRequest.builder()
                            .collectionName(config.getCollectionName())
                            .indexName(config.getIndexName())
                            .denseVector(denseVector)
                            .limit(limit)
                            .outputFields(vikingDbVectorStore.getOutputFields())
                            .build());
        } catch (VectorApiException | ApiClientException e) {
            throw new IllegalStateException("VikingDB searchByVector failed", e);
        }
        if (response == null || !"Success".equalsIgnoreCase(response.getCode()) || response.getResult() == null
                || response.getResult().getData() == null) {
            throw new IllegalStateException("VikingDB searchByVector 失败: "
                    + (response == null ? "null response" : response.getMessage()));
        }
        return response.getResult().getData().stream()
                .filter(item -> item.getScore() == null || item.getScore() >= minScore)
                .map(this::toDocument)
                .toList();
    }

    @Override
    protected void addEmbeddings(String indexName, List<Document> docs) {
        assertConfiguredIndex(indexName);
        // VikingDbVectorStore 负责 SDK 上限切片、嵌入维度校验以及 metadata schema 校验。
        // AbstractVectorService 为跨 provider 兼容会写入 content/mimeType/modality 三个框架元数据；
        // VikingDB 已将 content 存为保留列，另外两个字段若未在显式 schema 中声明会被 SDK 拒绝，
        // 因此在进入 VikingDB 前移除它们。用户业务 metadata 仍严格按 metadata-fields 校验。
        vikingDbVectorStore.add(docs.stream().map(this::toVikingDocument).toList());
    }

    @Override
    protected boolean usesStoreManagedEmbedding() {
        return true;
    }

    @Override
    protected void writeStoreManagedRecords(String indexName, List<VectorRecord> records) {
        assertConfiguredIndex(indexName);
        vikingDbVectorStore.add(records.stream().map(record -> toVikingDocument(toAiDocument(record, null))).toList());
    }

    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        assertConfiguredIndex(indexName);
        vikingDbVectorStore.delete(ids);
    }

    @Override
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        throw unsupportedRead("getByIds", indexName);
    }

    @Override
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        throw unsupportedRead("listDocuments", indexName);
    }

    private Document toDocument(SearchItem item) {
        Map<String, Object> metadata = new LinkedHashMap<>(item.getFields() == null ? Map.of() : item.getFields());
        Object content = metadata.remove(VikingDbVectorStore.CONTENT_FIELD_NAME);
        return Document.builder()
                .id(String.valueOf(item.getId()))
                .text(content == null ? "" : String.valueOf(content))
                .metadata(metadata)
                .score(item.getScore() == null ? null : item.getScore().doubleValue())
                .build();
    }

    private Document toVikingDocument(Document document) {
        Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
        metadata.remove("content");
        metadata.remove("mimeType");
        metadata.remove("modality");
        return Document.builder().id(document.getId()).text(document.getText()).metadata(metadata).build();
    }

    private void assertConfiguredIndex(String indexName) {
        if (!config.getCollectionName().equals(indexName)) {
            throw new IllegalArgumentException("VikingDB Store 绑定 collection=" + config.getCollectionName()
                    + "，不能操作 index=" + indexName);
        }
    }

    @Override
    protected void validateIndexName(String indexName) {
        assertConfiguredIndex(indexName);
    }

    private UnsupportedOperationException unsupportedRead(String operation, String indexName) {
        return new UnsupportedOperationException(operation + " 未实现: VikingDB 数据面 SDK 当前适配仅提供写入、删除和向量检索, index=" + indexName);
    }
}
