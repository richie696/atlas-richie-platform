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
package com.richie.component.vector.service.impl;

import com.richie.component.ai.api.RerankResponse;
import com.richie.component.ai.api.RerankResult;
import com.richie.component.ai.service.RerankService;
import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.exceptions.UnsupportedModalityException;
import com.richie.component.vector.model.BatchEvent;
import com.richie.component.vector.model.BatchStats;
import com.richie.component.vector.model.HybridSearchOptions;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.SearchOptions;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.embeddings.ModalityAwareEmbeddingService;
import com.richie.component.vector.service.VectorService;
import lombok.Setter;
import com.richie.component.vector.pipeline.BatchPipelineCoordinator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * {@link VectorService} v2 抽象基类 — 所有向量数据库实现的公共父类。
 * <p>
 * <b>职责分层</b>：
 * <ul>
 *   <li><b>本类承担</b>：模态路由、单条 CRUD、rerank、文档转换</li>
 *   <li><b>子类承担</b>：{@link #similaritySearchByVector} / {@link #addEmbeddings} / {@link #deleteByIds} / {@link #getByIds} / 索引管理</li>
 * </ul>
 * <p>
 * 批量异步管线已抽离至 {@link BatchPipelineCoordinator}，本类仅保留单条同步路径与索引/搜索委托。
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

    /**
     * 多模态嵌入路由服务 — 可选（仅在 Spring 容器中存在 {@link ModalityAwareEmbeddingService} Bean 时注入）。
     * <p>
     * <b>非侵入式装配</b>：本字段使用 {@code @Autowired(required = false)}，避免修改 7 个 provider
     * 子类的构造器签名。当注入失败（即没有图像嵌入模型配置）时，IMAGE 写入路径仍按 Phase A
     * 行为抛 {@link UnsupportedModalityException}。
     * <p>
     * <b>子类覆盖建议</b>：若子类需要在构造期立即可见 modalityService，可重写本字段或暴露 setter。
     */
    @Autowired(required = false)
    protected ModalityAwareEmbeddingService modalityService;

    /**
     * 向量库配置属性 — 可选。携带 {@link VectorProperties.Batch} 批量管线配置；
     * 未注入时 {@code BatchPipelineCoordinator.run} 回退至 {@link BatchPipelineCoordinator#DEFAULT_BATCH}。
     * <p>
     * setter 注入后下次 {@code run()} 立即生效（{@link BatchPipelineCoordinator.ConfigProvider#get()} 每批重新读取）。
     */
    @Setter
    @Autowired(required = false)
    protected VectorProperties vectorProperties;

    /**
     * 批量管线协调器 — 把 Stage A 嵌入 + Stage B 攒批写入 + 终态统计从本类抽离。
     * <p>
     * 通过三个回调（{@link BatchPipelineCoordinator.ConfigProvider} /
     * {@link BatchPipelineCoordinator.Embedder} /
     * {@link BatchPipelineCoordinator.DocumentWriter}）解耦具体实现：
     * <ul>
     *   <li>ConfigProvider — {@code () -> vectorProperties != null ? vectorProperties.getBatch() : null}，由 coordinator 处理 DEFAULT_BATCH 兜底</li>
     *   <li>Embedder — 委托 {@link #embedForBatch}</li>
     *   <li>DocumentWriter — 委托 {@link #addEmbeddings}</li>
     * </ul>
     */
    private final BatchPipelineCoordinator batchCoordinator;

    // ==================== 构造器 ====================

    protected AbstractVectorService(RerankService rerankService,
                                    VectorStore vectorStore,
                                    EmbeddingModel embeddingModel) {
        this.rerankService = rerankService;
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.batchCoordinator = new BatchPipelineCoordinator(
                () -> vectorProperties != null ? vectorProperties.getBatch() : null,
                this::embedForBatch,
                this::addEmbeddings);
    }

    /**
     * 注入完整配置（含 {@link VectorProperties.Batch}）的扩展构造器。
     * <p>
     * <b>向后兼容</b>：旧的 {@code super(rerankService, vectorStore, embeddingModel)} 链路不受影响 —
     * 现有 7 个 provider 子类无需修改；本构造器为新接入点。
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

    /**
     * 显式注入 modalityService 的可选 setter — 用于非 Spring 容器场景（例如直接 {@code new} 子类做测试）。
     * <p>
     * 由 Spring 自动注入时也走本方法（容器会优先调用 setter 完成 {@code @Autowired} 注入），
     * 因此子类无需额外关注。
     *
     * @param s 多模态嵌入路由服务；传 {@code null} 表示禁用多模态
     */
    @Autowired(required = false)
    public void setModalityService(ModalityAwareEmbeddingService s) {
        this.modalityService = s;
    }

    // ====================================================================
    // 单条同步 — Add
    // ====================================================================

    @Override
    public String addText(String indexName, String text, Map<String, Object> metadata) {
        return add(VectorRecord.text(indexName, text, metadata));
    }

    @Override
    public String add(VectorRecord record) {
        validateRecord(record);
        if (record.getId() == null) {
            record.setId(UUID.randomUUID().toString());
        }
        if (record.getContent() == null) {
            throw new IllegalArgumentException("VectorRecord.content 不能为空");
        }

        Modality modality = record.getContent().modality();
        if (modality == Modality.IMAGE) {
            if (modalityService == null || !modalityService.supportsModality(Modality.IMAGE)) {
                throw new UnsupportedModalityException(
                        "v2 IMAGE 模态需要在 ai 模块配置 ImageEmbeddingModel (CLIP/SigLIP) — 见 Phase C");
            }
            float[] embedding = modalityService.embed(Modality.IMAGE, record.getContent());
            Document aiDoc = toAiDocument(record, embedding);
            addEmbeddings(record.getIndexName(), List.of(aiDoc));
            return record.getId();
        }

        // 文本路径：调 TextEmbeddingModel
        Document aiDoc = toAiDocument(record, embedText(record));
        addEmbeddings(record.getIndexName(), List.of(aiDoc));
        return record.getId();
    }

    @Override
    public String addImage(String indexName, byte[] image, String mimeType, Map<String, Object> metadata) {
        return add(VectorRecord.image(indexName, image, mimeType).setMetadata(metadata));
    }

    @Override
    public String addImage(String indexName, Path imagePath, String mimeType, Map<String, Object> metadata) {
        return add(VectorRecord.image(indexName, imagePath, mimeType).setMetadata(metadata));
    }

    @Override
    public String addImageUrl(String indexName, String url, String mimeType, Map<String, Object> metadata) {
        // Phase A 简化：远程图片同步下载后入库
        try {
            byte[] data = downloadImage(url);
            return addImage(indexName, data, mimeType, metadata);
        } catch (Exception e) {
            throw new RuntimeException("下载远程图片失败: " + url, e);
        }
    }

    // ====================================================================
    // 单条同步 — Update
    // ====================================================================

    @Override
    public void updateText(String indexName, String id, String text, Map<String, Object> metadata) {
        VectorRecord r = VectorRecord.text(indexName, text, metadata).setId(id);
        update(r);
    }

    @Override
    public void update(VectorRecord record) {
        validateRecord(record);
        if (record.getId() == null) {
            throw new IllegalArgumentException("update 需要指定 id");
        }
        // delete + insert 等价语义
        delete(record.getIndexName(), record.getId());
        add(record);
    }

    @Override
    public void updateImage(String indexName, String id, byte[] image, String mimeType, Map<String, Object> metadata) {
        VectorRecord r = VectorRecord.image(indexName, image, mimeType).setId(id).setMetadata(metadata);
        update(r);
    }

    @Override
    public void updateImage(String indexName, String id, Path imagePath, String mimeType, Map<String, Object> metadata) {
        VectorRecord r = VectorRecord.image(indexName, imagePath, mimeType).setId(id).setMetadata(metadata);
        update(r);
    }

    // ====================================================================
    // 单条同步 — Delete
    // ====================================================================

    @Override
    public void delete(String indexName, String id) {
        deleteByIds(indexName, List.of(id));
    }

    @Override
    public long deleteIf(String indexName, Predicate<VectorRecord> filter) {
        // Phase A 默认实现：拉全量 → 过滤 → 删除（性能差，仅用于低频管理操作）
        List<VectorRecord> all = listDocuments(indexName, 0, MAX_LIST_LIMIT);
        List<String> matched = all.stream()
                .filter(filter)
                .map(VectorRecord::getId)
                .collect(Collectors.toList());
        if (!matched.isEmpty()) {
            deleteByIds(indexName, matched);
        }
        return matched.size();
    }

    // ====================================================================
    // 单条同步 — Get
    // ====================================================================

    @Override
    public Optional<VectorRecord> get(String indexName, String id) {
        List<VectorRecord> result = getByIds(indexName, List.of(id));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    @Override
    public List<VectorRecord> getAll(String indexName, Collection<String> ids) {
        return getByIds(indexName, new ArrayList<>(ids));
    }

    // ====================================================================
    // 单条同步 — Search
    // ====================================================================

    @Override
    public List<VectorSearchResult> searchByText(String indexName, String text, int limit) {
        return searchByText(indexName, text, limit, SearchOptions.builder().build());
    }

    @Override
    public List<VectorSearchResult> searchByText(String indexName, String text, int limit, double minScore) {
        SearchOptions opts = SearchOptions.builder().minScore(minScore).build();
        return searchByText(indexName, text, limit, opts);
    }

    @Override
    public List<VectorSearchResult> searchByText(String indexName, String text, int limit, SearchOptions options) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        int topK = limit > 0 ? limit : 10;
        double minScore = options.getMinScore() != null ? options.getMinScore() : 0.0;

        SearchRequest request = SearchRequest.builder()
                .query(text)
                .topK(topK)
                .similarityThreshold(minScore)
                .filterExpression(options.getFilterExpression())
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
                        embeddingModel != null
                                ? embeddingModel.embed(d.getText() == null ? "" : d.getText())
                                : null))
                .collect(Collectors.toList());

        boolean rerankEnabled = Boolean.TRUE.equals(options.getRerank());
        return rerankEnabled ? tryRerank(text, mapped) : mapped;
    }

    /**
     * 按图片检索 — Phase C 最佳努力接入点。
     * <p>
     * 当前默认实现：检测到 {@code modalityService != null && modalityService.supportsModality(IMAGE)}
     * 时路由到 {@link ModalityAwareEmbeddingService} 取得 query 向量后调用底层
     * {@link #similaritySearchByVector}；否则抛 {@link UnsupportedModalityException}。
     * <p>
     * Provider 子类可重写本方法以提供更精确的 IMAGE 检索语义（如：metadata 过滤中携带 MIME 提示）。
     *
     * @param indexName 索引名
     * @param image     原始图片字节
     * @param mimeType  MIME 类型（{@code image/} 前缀）
     * @param limit     topK
     * @return 检索结果（按相似度降序）
     */
    @Override
    public List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit) {
        if (modalityService != null && modalityService.supportsModality(Modality.IMAGE)) {
            VectorContent.ImageContent img = new VectorContent.ImageContent(image, mimeType);
            float[] queryVec = modalityService.embed(Modality.IMAGE, img);
            return searchByImageViaVector(indexName, queryVec, limit);
        }
        throw new UnsupportedModalityException(
                "v2 IMAGE 搜索需要在 ai 模块配置 ImageEmbeddingModel — 见 Phase C。当前 image model 不可用。");
    }

    @Override
    public List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit, double minScore) {
        if (modalityService != null && modalityService.supportsModality(Modality.IMAGE)) {
            VectorContent.ImageContent img = new VectorContent.ImageContent(image, mimeType);
            float[] queryVec = modalityService.embed(Modality.IMAGE, img);
            return searchByImageViaVector(indexName, queryVec, limit, minScore);
        }
        throw new UnsupportedModalityException("v2 IMAGE 搜索尚未启用");
    }

    @Override
    public List<VectorSearchResult> searchByImage(String indexName, Path imagePath, String mimeType, int limit) {
        if (modalityService != null && modalityService.supportsModality(Modality.IMAGE)) {
            VectorContent.ImageContent img = VectorContent.ImageContent.ofPath(imagePath, mimeType);
            float[] queryVec = modalityService.embed(Modality.IMAGE, img);
            return searchByImageViaVector(indexName, queryVec, limit);
        }
        throw new UnsupportedModalityException("v2 IMAGE 搜索尚未启用");
    }

    /**
     * 用预计算的查询向量做图像检索 — 三个 {@code searchByImage} 重载的共同下层入口。
     * <p>
     * 默认实现：委托 {@link #similaritySearchByVector}（minScore=0.0）并把 {@link Document}
     * 映射成 {@link VectorSearchResult}（直接走 {@link #similaritySearchByVector} 路径）。
     */
    private List<VectorSearchResult> searchByImageViaVector(String indexName, float[] queryVec, int limit) {
        return searchByImageViaVector(indexName, queryVec, limit, 0.0);
    }

    private List<VectorSearchResult> searchByImageViaVector(String indexName, float[] queryVec, int limit, double minScore) {
        if (queryVec == null) {
            throw new IllegalArgumentException("image query vector 不能为空");
        }
        List<Document> docs = similaritySearchByVector(indexName, queryVec, limit, minScore);
        List<VectorSearchResult> results = new ArrayList<>(docs.size());
        for (Document d : docs) {
            Double score = d.getScore();
            if (score == null && d.getMetadata().get("score") instanceof Number n) {
                score = n.doubleValue();
            }
            results.add(VectorSearchResult.of(d.getId(), d.getText(), score, queryVec));
        }
        return results;
    }

    // ====================================================================
    // 索引管理 — 基础（默认抛 UnsupportedOperationException，由子类按需实现）
    // ====================================================================

    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        createIndexImpl(indexName, config);
    }

    @Override
    public void deleteIndex(String indexName) {
        deleteIndexImpl(indexName);
    }

    @Override
    public boolean indexExists(String indexName) {
        return indexExistsImpl(indexName);
    }

    @Override
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
        return getIndexConfigImpl(indexName);
    }

    @Override
    public long countDocuments(String indexName) {
        return countDocumentsImpl(indexName);
    }

    @Override
    public final List<VectorRecord> listDocuments(String indexName, int offset, int limit) {
        if (limit <= 0) return List.of();
        int cappedLimit = Math.min(limit, MAX_LIST_LIMIT);
        return listDocumentsImpl(indexName, offset, cappedLimit);
    }

    // ====================================================================
    // 索引管理 — 扩展 (§14.2 抽象方法委托)
    // ====================================================================

    @Override
    public List<IndexInfo> listIndexes() {
        return listIndexesImpl();
    }

    @Override
    public long truncateIndex(String indexName) {
        long deleted = truncateIndexImpl(indexName);
        log.info("truncateIndex: 索引 [{}] 清空完成, 删除文档数={}", indexName, deleted);
        return deleted;
    }

    @Override
    public boolean updateIndexConfig(String indexName, VectorProperties.IndexConfig config) {
        return updateIndexConfigImpl(indexName, config);
    }

    @Override
    public boolean cloneIndex(String sourceIndexName, String targetIndexName) {
        return cloneIndexImpl(sourceIndexName, targetIndexName);
    }

    @Override
    public boolean awaitIndexReady(String indexName, Duration timeout) {
        return awaitIndexReadyImpl(indexName, timeout);
    }

    @Override
    public IndexInfo describeIndex(String indexName) {
        return describeIndexImpl(indexName);
    }

    // ====================================================================
    // 统计健康 (§14.2 + §14.3 抽象方法委托)
    // ====================================================================

    @Override
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
    @Override
    public boolean healthCheck(String indexName) {
        return healthCheckImpl(indexName);
    }

    // ====================================================================
    // 高级搜索 (§14.3 抽象方法委托)
    // ====================================================================

    @Override
    public List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery,
                                                 int limit, HybridSearchOptions options) {
        double vectorWeight = options != null && options.getVectorWeight() != null ? options.getVectorWeight() : 0.7;
        double keywordWeight = options != null && options.getKeywordWeight() != null ? options.getKeywordWeight() : 0.3;
        SearchOptions inner = options != null && options.getSearchOptions() != null
                ? options.getSearchOptions()
                : SearchOptions.builder().build();
        return hybridSearchImpl(indexName, text, keywordQuery, limit, vectorWeight, keywordWeight, inner);
    }

    @Override
    public List<VectorSearchResult> searchByMultiVector(String indexName, List<float[]> vectors, int limit) {
        throw new UnsupportedOperationException("searchByMultiVector 未实现 — 仅支持 named vectors 的 provider 实现");
    }

    // ====================================================================
    // 运维 / 别名 / 备份 (§14.4 抽象方法委托)
    // ====================================================================

    @Override
    public boolean optimize(String indexName) {
        return optimizeImpl(indexName);
    }

    @Override
    public boolean createAlias(String indexName, String alias) {
        return createAliasImpl(indexName, alias);
    }

    @Override
    public boolean switchAlias(String oldIndexName, String newIndexName, String alias) {
        return switchAliasImpl(oldIndexName, newIndexName, alias);
    }

    @Override
    public boolean backup(String indexName, String targetPath) {
        return backupImpl(indexName, targetPath);
    }

    @Override
    public boolean restore(String sourcePath, String indexName) {
        return restoreImpl(sourcePath, indexName);
    }

    // ====================================================================
    // 批量异步 — 反应式事件流（Phase A 基础管线）
    // ====================================================================

    @Override
    public Flux<BatchEvent> addBatch(String indexName, Flux<VectorRecord> records) {
        return batchCoordinator.run(indexName, records);
    }

    @Override
    public Flux<BatchEvent> addBatch(String indexName, List<VectorRecord> records) {
        return addBatch(indexName, Flux.fromIterable(records));
    }

    @Override
    public Flux<BatchEvent> updateBatch(String indexName, Flux<VectorRecord> records) {
        return batchCoordinator.run(indexName, records);
    }

    @Override
    public Flux<BatchEvent> deleteBatch(String indexName, Flux<String> ids) {
        String batchId = UUID.randomUUID().toString();
        Instant started = Instant.now();
        AtomicLong succeeded = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        long total = 0L;

        return Flux.concat(
                        Flux.<BatchEvent>create(sink -> {
                            // 计数需要上游预先知道 total — 这里取消费中的近似值，调用方可在 BatchStarted 之后自行计算
                            sink.next(new BatchEvent.BatchStarted(batchId, -1L, Instant.now()));
                        }),
                        ids.index()
                                .flatMap(indexed -> Mono.fromRunnable(() -> {
                                    try {
                                        deleteByIds(indexName, List.of(indexed.getT2()));
                                        succeeded.incrementAndGet();
                                    } catch (Exception e) {
                                        failed.incrementAndGet();
                                        throw new RuntimeException(e);
                                    }
                                }).subscribeOn(Schedulers.boundedElastic())
                                        .onErrorResume(e -> {
                                            log.warn("deleteBatch 单条失败: id={}, error={}", indexed.getT2(), e.getMessage());
                                            return Mono.empty();
                                        })
                                .then(Mono.fromSupplier(() -> (BatchEvent) new BatchEvent.ItemCompleted(
                                        batchId, indexed.getT2(), indexed.getT2(), Modality.TEXT, Instant.now()))))
                        )
                .concatWith(Flux.defer(() -> Flux.just(new BatchEvent.BatchCompleted(
                        batchId,
                        new BatchStats(
                                Math.max(succeeded.get() + failed.get(), total),
                                succeeded.get(), failed.get(),
                                Duration.between(started, Instant.now()), 0, succeeded.get()),
                        Instant.now()))));
    }

    /**
     * 批量场景下的嵌入调用 — 文本路径调 EmbeddingModel.embed()。
     * 图片路径抛 UnsupportedModalityException（Phase A 暂未启用）。
     * <p>
     * 本方法作为 {@link BatchPipelineCoordinator.Embedder} 回调被 coordinator 在 Stage A 调用。
     */
    private float[] embedForBatch(VectorRecord record) {
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel 未配置 — 无法执行嵌入");
        }
        VectorContent content = record.getContent();
        if (content == null) {
            throw new IllegalArgumentException("VectorRecord.content 不能为空");
        }
        if (content.modality() == Modality.IMAGE) {
            if (modalityService == null || !modalityService.supportsModality(Modality.IMAGE)) {
                throw new UnsupportedModalityException("IMAGE 模态暂未启用 — 见 Phase C ai 模块集成");
            }
            return modalityService.embed(Modality.IMAGE, content);
        }
        return embedText(record);
    }

    // ====================================================================
    // 抽象方法 — 子类必实现
    // ====================================================================

    /** 按向量搜索（含 minScore 过滤） */
    protected abstract List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore);

    /** 批量写入已嵌入文档 */
    protected abstract void addEmbeddings(String indexName, List<Document> docs);

    /** 按 ID 列表删除 */
    protected abstract void deleteByIds(String indexName, List<String> ids);

    /** 按 ID 列表读取 */
    protected abstract List<VectorRecord> getByIds(String indexName, List<String> ids);

    /** 分页列出索引内文档 */
    protected abstract List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit);

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

    private Document toAiDocument(VectorRecord record, float[] embedding) {
        // Document 构造的 text 参数此前一直是空串——Milvus 等依赖 doc.getText() 的 provider 读不到原始内容
        // 现在按 VectorContent 类型选择 text，并保留 metadata["content"] 向后兼容
        String text = "";
        if (record.getContent() instanceof VectorContent.TextContent t) {
            text = t.text();
        }
        Document aiDoc = new Document(record.getId(), text, record.getMetadata() != null ? record.getMetadata() : Map.of());
        if (record.getMetadata() != null) aiDoc.getMetadata().putAll(record.getMetadata());
        if (record.getTags() != null) aiDoc.getMetadata().put("tags", String.join(",", record.getTags()));
        if (record.getSource() != null) aiDoc.getMetadata().put("source", record.getSource());
        if (record.getStatus() != null) aiDoc.getMetadata().put("status", record.getStatus());
        if (record.getNamespace() != null) aiDoc.getMetadata().put("namespace", record.getNamespace());
        if (record.getContent() instanceof VectorContent.TextContent(String text1, String mimeType)) {
            aiDoc.getMetadata().put("content", text1);
            aiDoc.getMetadata().put("mimeType", mimeType);
            aiDoc.getMetadata().put("modality", Modality.TEXT.name());
        } else if (record.getContent() instanceof VectorContent.ImageContent img) {
            aiDoc.getMetadata().put("mimeType", img.mimeType());
            aiDoc.getMetadata().put("modality", Modality.IMAGE.name());
        }
        aiDoc.getMetadata().put("embedding", embedding);
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

    private byte[] downloadImage(String url) throws java.io.IOException {
        java.net.URI uri = java.net.URI.create(url);
        try (java.io.InputStream in = uri.toURL().openStream()) {
            return in.readAllBytes();
        }
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
        log.debug("hybridSearch: provider 不支持混合搜索，降级为 searchByText");
        return searchByText(indexName, text, limit, inner);
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