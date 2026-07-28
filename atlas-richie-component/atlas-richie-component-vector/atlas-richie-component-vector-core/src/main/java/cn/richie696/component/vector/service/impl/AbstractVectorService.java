/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.vector.service.impl;

import cn.richie696.component.ai.api.RerankResponse;
import cn.richie696.component.ai.api.RerankResult;
import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.bulk.BulkIngestionPipeline;
import cn.richie696.component.vector.bulk.BulkOperationEvent;
import cn.richie696.component.vector.bulk.BulkOperationSummary;
import cn.richie696.component.vector.bulk.BulkOperationType;
import cn.richie696.component.vector.bulk.BulkProcessingStage;
import cn.richie696.component.vector.config.VectorProperties;
import cn.richie696.component.vector.embeddings.ModalityAwareEmbeddingService;
import cn.richie696.component.vector.exceptions.UnsupportedModalityException;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.IndexInfo;
import cn.richie696.component.vector.model.Modality;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorContent;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.VectorService;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * {@link VectorService} v2 抽象基类 — 所有向量数据库实现的公共父类。
 * <p>
 * <b>职责分层</b>：
 * <ul>
 *   <li><b>本类承担</b>：文本嵌入、单条写入/删除、rerank、文档转换</li>
 *   <li><b>子类承担</b>：{@link #similaritySearchByVector} / {@link #addEmbeddings} / {@link #deleteByIds} / {@link #getByIds} / 索引管理</li>
 * </ul>
 * <p>
 * 批量异步编排由 {@link BulkIngestionPipeline} 承担，本类只提供 provider 数据面回调。
 *
 * @author richie696
 * @since 2.0.0
 */
@Slf4j
public abstract class AbstractVectorService implements VectorService {

    /** 列表查询最大单页限制 */
    public static final int MAX_LIST_LIMIT = 1000;

    /** 批量查询默认批大小 */
    protected static final int LIST_DOCUMENTS_BATCH_SIZE = 200;

    protected final VectorStore vectorStore;
    protected final EmbeddingModel embeddingModel;
    protected final RerankService rerankService;

    /** 可选的图片嵌入路由器；未配置时文本能力不受影响。 */
    @Autowired(required = false)
    @Setter
    protected ModalityAwareEmbeddingService modalityService;

    /**
     * 向量库配置属性；批量操作每次执行时读取其中的 {@link VectorProperties.Bulk}。
     */
    @Setter
    @Autowired(required = false)
    protected VectorProperties vectorProperties;

    @Autowired(required = false)
    protected VectorFilterCompiler vectorFilterCompiler;

    /**
     * 批量文本入库编排器。其依赖仅是文本嵌入和已嵌入记录写入回调，因而不反向依赖本抽象类。
     */
    private final BulkIngestionPipeline bulkIngestionPipeline;
    private final BulkIngestionPipeline storeManagedBulkIngestionPipeline;

    // ==================== 构造器 ====================

    protected AbstractVectorService(RerankService rerankService,
                                    VectorStore vectorStore,
                                    EmbeddingModel embeddingModel) {
        this.rerankService = rerankService;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.bulkIngestionPipeline = new BulkIngestionPipeline(this::embedRecord,
                (indexName, records) -> addEmbeddings(indexName, records.stream()
                        .map(item -> toAiDocument(item.record(), item.embedding()))
                        .toList()));
        this.storeManagedBulkIngestionPipeline = new BulkIngestionPipeline(this::writeStoreManagedRecords);
    }

    /**
     * 注入完整配置（含 {@link VectorProperties.Bulk}）的扩展构造器。
     *
     * @param rerankService    重排序服务（可为 {@code null}）
     * @param vectorStore      Spring AI 向量库句柄
     * @param embeddingModel   文本嵌入模型
     * @param vectorProperties 向量库配置属性（可为 {@code null}，回退至默认）
     */
    protected AbstractVectorService(RerankService rerankService,
                                    VectorStore vectorStore,
                                    EmbeddingModel embeddingModel,
                                    VectorProperties vectorProperties) {
        this(rerankService, vectorStore, embeddingModel);
        this.vectorProperties = vectorProperties;
    }

    /** 向后兼容构造器（无 rerank） */
    protected AbstractVectorService(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this(null, vectorStore, embeddingModel);
    }

    // ====================================================================
    // 核心写入 — Upsert
    // ====================================================================

    @Override
    public String upsert(VectorRecord record) {
        validateRecord(record);
        validateIndexName(record.getIndexName());
        if (record.getId() == null) {
            record.setId(UUID.randomUUID().toString());
        }
        if (record.getContent() == null) {
            throw new IllegalArgumentException("VectorRecord.content 不能为空");
        }

        if (usesStoreManagedEmbedding()) {
            writeStoreManagedRecords(record.getIndexName(), List.of(record));
        } else {
            float[] embedding = embedRecord(record);
            Document aiDoc = toAiDocument(record, embedding);
            addEmbeddings(record.getIndexName(), List.of(aiDoc));
        }
        return record.getId();
    }

    // ====================================================================
    // 核心删除 — vectorId
    // ====================================================================

    @Override
    public void deleteById(String indexName, String vectorId) {
        validateIndexName(indexName);
        deleteByIds(indexName, List.of(vectorId));
    }

    @Override
    public void deleteByIds(String indexName, Collection<String> vectorIds) {
        validateIndexName(indexName);
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        deleteByIds(indexName, List.copyOf(vectorIds));
    }

    // ====================================================================
    // 单条同步 — Search
    // ====================================================================

    public List<VectorSearchResult> searchByText(String indexName, String text, int limit) {
        return searchByText(indexName, text, limit, SearchOptions.builder().build());
    }

    public List<VectorSearchResult> searchByText(String indexName, String text, int limit, double minScore) {
        SearchOptions opts = SearchOptions.builder().minScore(minScore).build();
        return searchByText(indexName, text, limit, opts);
    }

    @Override
    public List<VectorSearchResult> searchByText(String indexName, String text, int limit, SearchOptions options) {
        validateIndexName(indexName);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        options = options == null ? SearchOptions.builder().build() : options;
        int topK = limit > 0 ? limit : 10;
        double minScore = options.getMinScore() != null ? options.getMinScore() : 0.0;

        SearchRequest request = SearchRequest.builder()
                .query(text)
                .topK(topK)
                .similarityThreshold(minScore)
                .filterExpression(compileProviderFilter(options))
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        if (results.isEmpty()) {
            return List.of();
        }

        List<VectorSearchResult> mapped = results.stream()
                .map(d -> VectorSearchResult.of(
                        d.getId(),
                        d.getFormattedContent(),
                        d.getScore(),
                        null).setMetadata(d.getMetadata()))
                .collect(Collectors.toList());

        boolean rerankEnabled = Boolean.TRUE.equals(options.getRerank());
        return rerankEnabled ? tryRerank(text, mapped) : mapped;
    }

    @Override
    public List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType,
                                                   int limit, double minScore) {
        VectorContent.ImageContent content = new VectorContent.ImageContent(image, mimeType);
        return searchByImageVector(indexName, content, limit, minScore);
    }

    @Override
    public List<VectorSearchResult> searchByImage(String indexName, Path imagePath, String mimeType, int limit) {
        return searchByImageVector(indexName, VectorContent.ImageContent.ofPath(imagePath, mimeType), limit, 0.0);
    }

    /** 供具体 provider 或旧调用方使用的默认图片检索重载。 */
    public List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit) {
        return searchByImage(indexName, image, mimeType, limit, 0.0);
    }

    private List<VectorSearchResult> searchByImageVector(String indexName, VectorContent.ImageContent image,
                                                          int limit, double minScore) {
        validateIndexName(indexName);
        if (modalityService == null || !modalityService.supportsModality(Modality.IMAGE)) {
            throw new UnsupportedModalityException(Modality.IMAGE, "IMAGE 模态未配置 imageEmbeddingModel");
        }
        float[] vector = modalityService.embed(Modality.IMAGE, image);
        return similaritySearchByVector(indexName, vector, limit, minScore).stream()
                .map(document -> VectorSearchResult.of(document.getId(), document.getFormattedContent(),
                        document.getScore(), vector))
                .toList();
    }

    // ====================================================================
    // 索引管理 — 基础（默认抛 UnsupportedOperationException，由子类按需实现）
    // ====================================================================

    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        createIndexImpl(indexName, config);
    }

    public void deleteIndex(String indexName) {
        deleteIndexImpl(indexName);
    }

    public boolean indexExists(String indexName) {
        return indexExistsImpl(indexName);
    }

    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
        return getIndexConfigImpl(indexName);
    }

    public long countDocuments(String indexName) {
        return countDocumentsImpl(indexName);
    }

    public final List<VectorRecord> listDocuments(String indexName, int offset, int limit) {
        if (limit <= 0) return List.of();
        int cappedLimit = Math.min(limit, MAX_LIST_LIMIT);
        return listDocumentsImpl(indexName, offset, cappedLimit);
    }

    /**
     * 精确读取是可选能力；只有声明 {@code VectorRecordReadOperations} 的 provider 才承诺该方法可用。
     */
    public Optional<VectorRecord> getById(String indexName, String vectorId) {
        if (vectorId == null || vectorId.isBlank()) {
            throw new IllegalArgumentException("vectorId 不能为空");
        }
        return getByIds(indexName, List.of(vectorId)).stream().findFirst();
    }

    /**
     * 以 vectorId 为键返回精确读取结果；底层 provider 的返回顺序不参与 API 语义。
     */
    public Map<String, VectorRecord> getByIds(String indexName, Collection<String> vectorIds) {
        validateIndexName(indexName);
        if (vectorIds == null || vectorIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = vectorIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, VectorRecord> records = new LinkedHashMap<>();
        for (VectorRecord record : getByIds(indexName, ids)) {
            if (record != null && record.getId() != null) {
                records.put(record.getId(), record);
            }
        }
        return Map.copyOf(records);
    }

    // ====================================================================
    // 索引管理 — 扩展 (§14.2 抽象方法委托)
    // ====================================================================

    public List<IndexInfo> listIndexes() {
        return listIndexesImpl();
    }

    public long truncateIndex(String indexName) {
        long deleted = truncateIndexImpl(indexName);
        log.info("truncateIndex: 索引 [{}] 清空完成, 删除文档数={}", indexName, deleted);
        return deleted;
    }

    public boolean updateIndexConfig(String indexName, VectorProperties.IndexConfig config) {
        return updateIndexConfigImpl(indexName, config);
    }

    public boolean cloneIndex(String sourceIndexName, String targetIndexName) {
        return cloneIndexImpl(sourceIndexName, targetIndexName);
    }

    public boolean awaitIndexReady(String indexName, Duration timeout) {
        return awaitIndexReadyImpl(indexName, timeout);
    }

    public IndexInfo describeIndex(String indexName) {
        return describeIndexImpl(indexName);
    }

    // ====================================================================
    // 统计健康 (§14.2 + §14.3 抽象方法委托)
    // ====================================================================

    public IndexInfo getIndexStats(String indexName) {
        return getIndexStatsImpl(indexName);
    }

    /**
     * 健康检查 — 委托给 {@link #healthCheckImpl(String)}。
     * <p>
     * 默认实现见 {@link #healthCheckImpl(String)} — 三步探针：schema 存在 → 文档计数可读 → 计数非负。
     * Provider 可通过 override {@code healthCheckImpl} 提供更精确的 provider-specific 健康判定。
     *
     * @param indexName 索引名称
     * @return true=索引存在且可读，false=任一检查失败或抛异常
     */
    public boolean healthCheck(String indexName) {
        return healthCheckImpl(indexName);
    }

    // ====================================================================
    // 高级搜索 (§14.3 抽象方法委托)
    // ====================================================================

    public List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery,
                                                 int limit, HybridSearchOptions options) {
        double vectorWeight = options != null && options.getVectorWeight() != null ? options.getVectorWeight() : 0.7;
        double keywordWeight = options != null && options.getKeywordWeight() != null ? options.getKeywordWeight() : 0.3;
        SearchOptions inner = options != null && options.getSearchOptions() != null
                ? options.getSearchOptions()
                : SearchOptions.builder().build();
        return hybridSearchImpl(indexName, text, keywordQuery, limit, vectorWeight, keywordWeight, inner);
    }

    public List<VectorSearchResult> searchByMultiVector(String indexName, List<float[]> vectors, int limit) {
        throw new UnsupportedOperationException("searchByMultiVector 未实现 — 仅支持 named vectors 的 provider 实现");
    }

    // ====================================================================
    // 运维 / 别名 / 备份 (§14.4 抽象方法委托)
    // ====================================================================

    public boolean optimize(String indexName) {
        return optimizeImpl(indexName);
    }

    public boolean createAlias(String indexName, String alias) {
        return createAliasImpl(indexName, alias);
    }

    public boolean switchAlias(String oldIndexName, String newIndexName, String alias) {
        return switchAliasImpl(oldIndexName, newIndexName, alias);
    }

    public boolean backup(String indexName, String targetPath) {
        return backupImpl(indexName, targetPath);
    }

    public boolean restore(String sourcePath, String indexName) {
        return restoreImpl(sourcePath, indexName);
    }

    // ====================================================================
    // 批量异步 — 反应式事件流
    // ====================================================================

    @Override
    public Flux<BulkOperationEvent> upsertAll(String indexName, Flux<VectorRecord> records) {
        return (usesStoreManagedEmbedding() ? storeManagedBulkIngestionPipeline : bulkIngestionPipeline).execute(indexName, records,
                vectorProperties == null ? null : vectorProperties.getBulk());
    }

    @Override
    public Flux<BulkOperationEvent> deleteAll(String indexName, Flux<String> vectorIds) {
        if (indexName == null || indexName.isBlank()) {
            return Flux.error(new IllegalArgumentException("indexName 不能为空"));
        }
        if (vectorIds == null) {
            return Flux.error(new IllegalArgumentException("vectorIds 不能为空"));
        }
        String operationId = UUID.randomUUID().toString();
        Instant started = Instant.now();
        AtomicLong succeeded = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        AtomicLong deleteRequests = new AtomicLong();
        VectorProperties.Bulk bulkOptions = vectorProperties == null ? null : vectorProperties.getBulk();
        int concurrency = bulkOptions == null ? 8 : Math.max(1, bulkOptions.getWriteConcurrency());

        Flux<BulkOperationEvent> work = vectorIds.flatMap(vectorId -> Flux.concat(
                Mono.<BulkOperationEvent>just(new BulkOperationEvent.ItemStarted(operationId, vectorId,
                        BulkProcessingStage.DELETING, Instant.now())),
                Mono.fromRunnable(() -> {
                            if (vectorId.isBlank()) {
                                throw new IllegalArgumentException("vectorId 不能为空");
                            }
                            deleteRequests.incrementAndGet();
                            deleteById(indexName, vectorId);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .then(Mono.fromSupplier(() -> {
                            succeeded.incrementAndGet();
                            return (BulkOperationEvent) new BulkOperationEvent.ItemSucceeded(
                                    operationId, vectorId, vectorId, Instant.now());
                        }))
                        .onErrorResume(error -> {
                            failed.incrementAndGet();
                            return Mono.just(new BulkOperationEvent.ItemFailed(operationId, vectorId,
                                    BulkProcessingStage.DELETING, error.getClass().getSimpleName(),
                                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                                    Instant.now()));
                        })
        ), concurrency);

        return Flux.concat(
                Mono.just(new BulkOperationEvent.Started(operationId, BulkOperationType.DELETE, started)),
                work,
                Mono.fromSupplier(() -> new BulkOperationEvent.Completed(operationId, BulkOperationType.DELETE,
                        new BulkOperationSummary(succeeded.get(), failed.get(), Duration.between(started, Instant.now()),
                                0, deleteRequests.get()), Instant.now())));
    }

    // ====================================================================
    // 抽象方法 — 子类必实现
    // ====================================================================

    /** 按向量搜索（含 minScore 过滤） */
    protected abstract List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore);

    /**
     * 带 provider 侧标量过滤的向量检索钩子。
     * 不支持原生过滤的 provider 复用无过滤实现；需要 ACL 的 provider 必须覆盖此方法。
     */
    protected List<Document> similaritySearchByVector(String indexName, float[] vector, int limit,
                                                       double minScore, String providerFilter) {
        if (providerFilter != null && !providerFilter.isBlank()) {
            throw new UnsupportedOperationException("provider does not support native vector filter pushdown");
        }
        return similaritySearchByVector(indexName, vector, limit, minScore);
    }

    /** 批量写入已嵌入文档 */
    protected abstract void addEmbeddings(String indexName, List<Document> docs);

    /** provider 的 {@code VectorStore.add} 自行调用 EmbeddingModel 时返回 true，避免 bulk 双重嵌入。 */
    protected boolean usesStoreManagedEmbedding() { return false; }

    /** 仅供 {@link #usesStoreManagedEmbedding()} 为 true 的 provider 覆盖。 */
    protected void writeStoreManagedRecords(String indexName, List<VectorRecord> records) {
        throw new UnsupportedOperationException("provider does not support store-managed bulk embedding");
    }

    /** 按 ID 列表删除 */
    protected abstract void deleteByIds(String indexName, List<String> ids);

    /** 按 ID 列表读取 */
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        throw new UnsupportedOperationException("provider does not support exact vector record reads");
    }

    /** 分页列出索引内文档 */
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        throw new UnsupportedOperationException("provider does not support record listing");
    }

    /** provider 可限制一个实例实际绑定的 index，避免调用参数与数据面不一致。 */
    protected void validateIndexName(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName must not be blank");
        }
    }

    // ==================== 子类可选实现（默认抛 UnsupportedOperationException） ====================

    protected void createIndexImpl(String indexName, VectorProperties.IndexConfig config) {
        throw new UnsupportedOperationException("createIndex 未实现");
    }

    protected void deleteIndexImpl(String indexName) {
        throw new UnsupportedOperationException("deleteIndex 未实现");
    }

    protected boolean indexExistsImpl(String indexName) {
        throw new UnsupportedOperationException("indexExists 未实现");
    }

    protected VectorProperties.IndexConfig getIndexConfigImpl(String indexName) {
        throw new UnsupportedOperationException("getIndexConfig 未实现");
    }

    protected long countDocumentsImpl(String indexName) {
        throw new UnsupportedOperationException("countDocuments 未实现");
    }

    // ====================================================================
    // 内部工具
    // ====================================================================

    private void validateRecord(VectorRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("VectorRecord 不能为空");
        }
        if (record.getIndexName() == null || record.getIndexName().isBlank()) {
            throw new IllegalArgumentException("VectorRecord.indexName 不能为空");
        }
    }

    private float[] embedRecord(VectorRecord record) {
        Modality modality = record.getContent().modality();
        if (modality == Modality.IMAGE) {
            if (modalityService == null || !modalityService.supportsModality(Modality.IMAGE)) {
                throw new UnsupportedModalityException(Modality.IMAGE, "IMAGE 模态未配置 imageEmbeddingModel");
            }
            return modalityService.embed(Modality.IMAGE, record.getContent());
        }
        return embedText(record);
    }

    /**
     * 将统一的结构化过滤树编译为当前 VectorStore 所需的底层 DSL。
     * Spring AI 的 {@link SearchRequest} 仍以字符串承载过滤条件，但业务 API 不再接收原始 DSL。
     */
    protected String compileProviderFilter(SearchOptions options) {
        if (options.getFilter() == null) {
            return null;
        }
        if (vectorFilterCompiler == null) {
            throw new UnsupportedOperationException("structured VectorFilter requires a provider VectorFilterCompiler");
        }
        return vectorFilterCompiler.compile(options.getFilter());
    }

    private float[] embedText(VectorRecord record) {
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel 未配置 — 无法执行嵌入");
        }
        VectorContent content = record.getContent();
        if (!(content instanceof VectorContent.TextContent text)) {
            throw new IllegalArgumentException("该路径仅处理 TEXT 模态");
        }
        return embeddingModel.embed(text.text());
    }

    protected Document toAiDocument(VectorRecord record, float[] embedding) {
        // Document 构造的 text 参数此前一直是空串——Milvus 等依赖 doc.getText() 的 provider 读不到原始内容
        // 现在按 VectorContent 类型选择 text，并保留 metadata["content"] 向后兼容
        String text = "";
        if (record.getContent() instanceof VectorContent.TextContent t) {
            text = t.text();
        }
        Document aiDoc = new Document(record.getId(), text, record.getMetadata() != null ? record.getMetadata() : Map.of());
        if (record.getMetadata() != null) aiDoc.getMetadata().putAll(record.getMetadata());
        if (record.getTags() != null) aiDoc.getMetadata().put("tags", String.join(",", record.getTags()));
        if (record.getDocumentId() != null) aiDoc.getMetadata().put("documentId", record.getDocumentId());
        if (record.getChunkNo() != null) aiDoc.getMetadata().put("chunkNo", record.getChunkNo());
        if (record.getVersion() != null) aiDoc.getMetadata().put("version", record.getVersion());
        if (record.getSource() != null) aiDoc.getMetadata().put("source", record.getSource());
        if (record.getStatus() != null) aiDoc.getMetadata().put("status", record.getStatus());
        if (record.getNamespace() != null) aiDoc.getMetadata().put("namespace", record.getNamespace());
        if (record.getContent() instanceof VectorContent.TextContent(String text1, String mimeType)) {
            aiDoc.getMetadata().put("content", text1);
            aiDoc.getMetadata().put("mimeType", mimeType);
            aiDoc.getMetadata().put("modality", Modality.TEXT.name());
        } else if (record.getContent() instanceof VectorContent.ImageContent image) {
            aiDoc.getMetadata().put("mimeType", image.mimeType());
            aiDoc.getMetadata().put("modality", Modality.IMAGE.name());
        }
        if (embedding != null) {
            aiDoc.getMetadata().put("embedding", embedding);
        }
        return aiDoc;
    }

    /**
     * 重排序（仅文本搜索时生效）。
     */
    protected List<VectorSearchResult> tryRerank(String queryText, List<VectorSearchResult> results) {
        if (rerankService == null || results == null || results.size() < 2
                || queryText == null || queryText.isBlank()) {
            return results;
        }

        List<String> documents = results.stream()
                .map(r -> r.getContent() != null ? r.getContent() : "")
                .collect(Collectors.toList());

        RerankResponse resp;
        try {
            resp = rerankService.rerank(queryText, documents, null, null);
        } catch (Exception e) {
            log.warn("重排序服务调用异常，跳过重排", e);
            return results;
        }

        if (!resp.isSuccess() || resp.getResults() == null || resp.getResults().isEmpty()) {
            return results;
        }

        VectorSearchResult[] arr = results.toArray(new VectorSearchResult[0]);
        List<VectorSearchResult> reranked = new ArrayList<>(resp.getResults().size());
        for (RerankResult rr : resp.getResults()) {
            int idx = rr.getIndex();
            if (idx >= 0 && idx < arr.length && arr[idx] != null) {
                VectorSearchResult orig = arr[idx];
                reranked.add(new VectorSearchResult()
                        .setId(orig.getId())
                        .setContent(orig.getContent())
                        .setScore(rr.getRelevanceScore())
                        .setVector(orig.getVector())
                        .setMetadata(orig.getMetadata()));
            }
        }

        if (reranked.isEmpty()) {
            return results;
        }

        log.debug("tryRerank: 重排生效，结果数 {} (原始 {})", reranked.size(), results.size());
        return reranked;
    }

    // ====================================================================
    // §14.2 索引管理 — 子类可选实现（默认抛 UnsupportedOperationException）
    // ====================================================================

    protected List<IndexInfo> listIndexesImpl() {
        throw new UnsupportedOperationException("listIndexes 未实现");
    }

    protected IndexInfo describeIndexImpl(String indexName) {
        return getIndexStatsImpl(indexName);
    }

    protected boolean updateIndexConfigImpl(String indexName, VectorProperties.IndexConfig config) {
        throw new UnsupportedOperationException("updateIndexConfig 未实现");
    }

    protected long truncateIndexImpl(String indexName) {
        throw new UnsupportedOperationException("truncateIndex 未实现");
    }

    protected IndexInfo getIndexStatsImpl(String indexName) {
        throw new UnsupportedOperationException("getIndexStats 未实现");
    }

    protected boolean awaitIndexReadyImpl(String indexName, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                if (indexExists(indexName)) return true;
            } catch (Exception ignored) {
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // ====================================================================
    // §14.3 高级搜索 — 子类可选实现
    // ====================================================================

    protected List<VectorSearchResult> hybridSearchImpl(String indexName, String text, String keywordQuery,
                                                        int limit, double vectorWeight, double keywordWeight,
                                                        SearchOptions inner) {
        throw new UnsupportedOperationException("provider does not support hybrid search; do not silently fall back to dense search");
    }

    /**
     * 三步探针：schema 存在 → 文档计数可读 → 计数非负。
     *
     * <p>仅检查 schema 不足以反映真实可用性（collection/table 可能存在但权限异常、
     * 连接断开或查询失败）。该实现补齐第二步：对索引执行一次轻量计数，确认
     * 后端可读；任何步骤抛异常均视为不健康，整体判定为 false，不向调用方抛出异常。</p>
     */
    protected boolean healthCheckImpl(String indexName) {
        try {
            if (!indexExists(indexName)) {
                log.warn("healthCheck: 索引不存在: {}", indexName);
                return false;
            }
            long count = countDocuments(indexName);
            if (count < 0) {
                log.warn("healthCheck: countDocuments 返回负数: {}", indexName);
                return false;
            }
            log.debug("healthCheck: 索引 {} 健康, 文档数={}", indexName, count);
            return true;
        } catch (Exception e) {
            log.warn("healthCheck: 索引 {} 检查失败: {}", indexName, e.getMessage());
            return false;
        }
    }

    // ====================================================================
    // §14.4 运维 / 别名 / 备份 — 子类可选实现（默认抛 UnsupportedOperationException）
    // ====================================================================

    /**
     * 统一抛 {@link UnsupportedOperationException} — 把 7 provider × 5 ops 的 30+ 处
     * 「provider X 没有 Y 能力」异常合并到一处。
     * <p>
     * 消息格式：{@code "<op> 未实现: provider=<provider>, index=<indexName>"} —
     * 同时携带 op 名（{@code "optimize"} / {@code "createAlias"} 等）和 provider 标识
     * （{@code "redis"} / {@code "neo4j"} 等），便于日志聚类与 stack trace 定位。
     * <p>
     * 返回 {@code boolean} 是 Java 标准 "unconditional throw" 模式 — 编译器基于
     * 「方法体始终抛异常」判定本方法不会正常返回，使外部的 {@code optimizeImpl(...)}
     * 等 {@code boolean} 返回方法可直接 {@code return throwUnsupportedOps(...);}，
     * 不需要额外拼凑不可达 return 语句。
     * <p>
     * 调用方（provider impl）应当固定传 provider 名（与测试中 {@code hasMessageContaining("...")}
     * 断言对齐），让异常文本可作为契约的一部分被验证。
     *
     * @param op        操作名（如 {@code "optimize"} / {@code "createAlias"}）
     * @param indexName 主索引名（{@code switchAlias} 应传新索引）；可空
     * @param provider  provider 标识（如 {@code "redis"} / {@code "neo4j"}），可空
     * @return 永远不会返回；仅用于满足 {@code boolean} 返回方法的语法
     * @throws UnsupportedOperationException 始终抛出
     */
    protected static boolean throwUnsupportedOps(String op, String indexName, String provider) {
        throw new UnsupportedOperationException(
                op + " 未实现: provider=" + (provider == null ? "unknown" : provider)
                        + ", index=" + (indexName == null ? "null" : indexName));
    }

    protected boolean optimizeImpl(String indexName) {
        throw new UnsupportedOperationException("optimize 未实现");
    }

    protected boolean createAliasImpl(String indexName, String alias) {
        throw new UnsupportedOperationException("createAlias 未实现");
    }

    protected boolean switchAliasImpl(String oldIndexName, String newIndexName, String alias) {
        throw new UnsupportedOperationException("switchAlias 未实现");
    }

    protected boolean backupImpl(String indexName, String targetPath) {
        throw new UnsupportedOperationException("backup 未实现");
    }

    protected boolean restoreImpl(String sourcePath, String indexName) {
        throw new UnsupportedOperationException("restore 未实现");
    }

    protected boolean cloneIndexImpl(String sourceIndexName, String targetIndexName) {
        throw new UnsupportedOperationException("cloneIndex 未实现");
    }
}
