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

import com.richie.component.ai.service.RerankService;
import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.IndexStatus;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.SearchOptions;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.batch.model.BatchDeleteResponse;
import io.weaviate.client.v1.filters.Operator;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.misc.model.ReplicationConfig;
import io.weaviate.client.v1.misc.model.VectorIndexConfig;
import io.weaviate.client.v1.schema.model.Schema;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "weaviate")
public class WeaviateVectorServiceImpl extends AbstractVectorService implements VectorService {

    private final WeaviateClient weaviateClient;

    @Autowired
    public WeaviateVectorServiceImpl(@Autowired(required = false) RerankService rerankService,
                                     VectorStore vectorStore,
                                     @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                     WeaviateClient weaviateClient) {
        super(rerankService, vectorStore, embeddingModel);
        this.weaviateClient = weaviateClient;
    }

    // ====================================================================
    // §14.2 索引管理 — 基本 CRUD（protected *Impl 子类实现）
    // ====================================================================

    /**
     * 创建 Weaviate class (索引)。
     *
     * <p>基于 {@link VectorProperties.IndexConfig} 构造 {@link WeaviateClass} 并通过
     * {@code weaviateClient.schema().classCreator()} 写入。向量距离度量、索引类型、副本数等
     * 字段均来自 config，未指定时使用默认值 (cosine / hnsw / 1)。</p>
     *
     * @param indexName 索引名称 (即 Weaviate class name)
     * @param config    索引配置
     * @throws RuntimeException 当 Weaviate 返回错误时抛出
     */
    @Override
    protected void createIndexImpl(String indexName, VectorProperties.IndexConfig config) {
        VectorIndexConfig vectorConfig = VectorIndexConfig.builder()
                .distance(config.getMetric() != null ? config.getMetric() : "cosine")
                .efConstruction(128)
                .maxConnections(64)
                .build();

        ReplicationConfig replicationConfig = ReplicationConfig.builder()
                .factor(config.getReplicas() != null ? config.getReplicas() : 1)
                .build();

        WeaviateClass weaviateClass = WeaviateClass.builder()
                .className(indexName)
                .vectorIndexType(config.getIndexType() != null ? config.getIndexType() : "hnsw")
                .vectorizer("none")
                .vectorIndexConfig(vectorConfig)
                .replicationConfig(replicationConfig)
                .build();

        Result<Boolean> result = weaviateClient.schema().classCreator().withClass(weaviateClass).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate createIndex failed: " + result.getError().getMessages());
        }
    }

    /**
     * 删除指定索引 (Weaviate class) 及其全部数据。
     *
     * <p>通过 {@code schema().classDeleter().withClassName(indexName).run()} 调用 schema 删除端点；
     * 调用方需自行承担数据丢失风险。</p>
     *
     * @param indexName 索引名称
     * @throws RuntimeException 当 Weaviate 返回错误时抛出
     */
    @Override
    protected void deleteIndexImpl(String indexName) {
        Result<Boolean> result = weaviateClient.schema().classDeleter().withClassName(indexName).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate deleteIndex failed: " + result.getError().getMessages());
        }
        log.info("Weaviate 索引 [{}] 已删除", indexName);
    }

    /**
     * 判断指定索引 (Weaviate class) 是否存在。
     *
     * @param indexName 索引名称
     * @return true=索引存在，false=不存在或结果为空
     * @throws RuntimeException 当 Weaviate 返回错误时抛出
     */
    @Override
    protected boolean indexExistsImpl(String indexName) {
        Result<Boolean> result = weaviateClient.schema().exists().withClassName(indexName).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate indexExists failed: " + result.getError().getMessages());
        }
        return result.getResult() != null && result.getResult();
    }

    /**
     * 获取索引配置信息。
     *
     * <p>从 Weaviate schema 读取 class 定义并映射为 {@link VectorProperties.IndexConfig}；
     * class 不存在或查询报错时返回 null。</p>
     *
     * @param indexName 索引名称
     * @return 索引配置；索引不存在时返回 null
     */
    @Override
    protected VectorProperties.IndexConfig getIndexConfigImpl(String indexName) {
        Result<WeaviateClass> result = weaviateClient.schema().classGetter().withClassName(indexName).run();
        if (result.hasErrors() || result.getResult() == null) {
            return null;
        }
        WeaviateClass clazz = result.getResult();
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setName(clazz.getClassName());
        config.setIndexType(clazz.getVectorIndexType());
        if (clazz.getVectorIndexConfig() != null) {
            config.setMetric(clazz.getVectorIndexConfig().getDistance());
        }
        if (clazz.getReplicationConfig() != null) {
            config.setReplicas(clazz.getReplicationConfig().getFactor());
        }
        return config;
    }

    /**
     * 统计索引内文档数。
     *
     * <p>通过 GraphQL {@code _additional { id }} 计数；该方式仅用于获取当前 class 下的对象数量。
     * 注意：Weaviate 默认限制单次 GraphQL Get 返回上限，超大规模索引需改用 {@code Aggregate} 端点。</p>
     *
     * @param indexName 索引名称
     * @return 文档数量 (粗略估计；不存在的 class 返回 0)
     * @throws RuntimeException 当 GraphQL 返回错误时抛出
     */
    @Override
    protected long countDocumentsImpl(String indexName) {
        String graphql = "{ Get { " + indexName + " { _additional { id } } } }";
        var result = weaviateClient.graphQL().raw().withQuery(graphql).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate countDocuments failed: " + result.getError().getMessages());
        }
        Map<?, ?> data = (Map<?, ?>) result.getResult().getData();
        if (data == null) {
            return 0;
        }
        Map<?, ?> getResult = (Map<?, ?>) data.get("Get");
        if (getResult == null) {
            return 0;
        }
        List<?> items = (List<?>) getResult.get(indexName);
        return items != null ? items.size() : 0;
    }

    /**
     * 清空索引内全部数据（保留 schema 定义）。
     *
     * <p>通过 {@code _id NotEqual ""} 匹配所有对象后调用 batch deleter 批量删除。
     * 返回值取「成功删除数」与「清空前预估文档数」的较大值，便于上层日志输出。</p>
     *
     * @param indexName 索引名称
     * @return 实际删除的文档数
     * @throws RuntimeException 当 batch delete 失败时抛出
     */
    @Override
    protected long truncateIndexImpl(String indexName) {
        long previousCount = countDocumentsImpl(indexName);
        WhereFilter matchAll = WhereFilter.builder()
                .path("_id")
                .operator(Operator.NotEqual)
                .valueString("")
                .build();
        Result<BatchDeleteResponse> result = weaviateClient.batch().objectsBatchDeleter()
                .withClassName(indexName)
                .withWhere(matchAll)
                .withOutput("minimal")
                .run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate truncateIndex failed: " + result.getError().getMessages());
        }
        long successful = 0L;
        if (result.getResult() != null && result.getResult().getResults() != null
                && result.getResult().getResults().getSuccessful() != null) {
            successful = result.getResult().getResults().getSuccessful();
        }
        log.info("Weaviate 清空 class [{}] 完成，预估={}, 实际成功删除={}", indexName, previousCount, successful);
        return Math.max(successful, previousCount);
    }

    // ====================================================================
    // §14.2 索引管理 — 扩展 (子类可选实现)
    // ====================================================================

    /**
     * 列出所有 Weaviate class (索引)。
     *
     * <p>通过 {@code schema().getter().run()} 获取全量 schema 并提取 classes 列表。
     * 任意 class 字段读取失败时降级返回基础 IndexInfo，确保即使 Weaviate 返回部分残缺数据
     * 也能给出可用清单。</p>
     *
     * @return 索引列表 (按 class name 自然顺序)
     * @throws RuntimeException 当 Weaviate 返回错误时抛出
     */
    @Override
    protected List<IndexInfo> listIndexesImpl() {
        Result<Schema> result = weaviateClient.schema().getter().run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate listIndexes failed: " + result.getError().getMessages());
        }
        Schema schema = result.getResult();
        if (schema == null || schema.getClasses() == null) {
            return List.of();
        }
        List<IndexInfo> indexes = new ArrayList<>();
        for (WeaviateClass clazz : schema.getClasses()) {
            indexes.add(buildIndexInfo(clazz, null));
        }
        log.debug("Weaviate listIndexes: 共发现 {} 个索引", indexes.size());
        return indexes;
    }

    /**
     * 描述单个索引的详细 schema 信息。
     *
     * <p>读取 Weaviate class 定义后返回包含 vectorIndexType / vectorIndexConfig /
     * replicationConfig 的 {@link IndexInfo}；class 不存在或 schema 错误时返回 null。</p>
     *
     * @param indexName 索引名称
     * @return 索引描述信息；class 不存在时返回 null
     */
    @Override
    protected IndexInfo describeIndexImpl(String indexName) {
        Result<WeaviateClass> result = weaviateClient.schema().classGetter().withClassName(indexName).run();
        if (result.hasErrors() || result.getResult() == null) {
            log.warn("Weaviate describeIndex: class [{}] 不存在或读取失败", indexName);
            return null;
        }
        WeaviateClass clazz = result.getResult();
        IndexInfo info = buildIndexInfo(clazz, null);
        Map<String, Object> metadata = info.metadata() == null ? new HashMap<>() : new HashMap<>(info.metadata());
        if (clazz.getVectorIndexConfig() != null) {
            metadata.put("vectorIndexConfig", clazz.getVectorIndexConfig());
        }
        if (clazz.getReplicationConfig() != null) {
            metadata.put("replicationConfig", clazz.getReplicationConfig());
        }
        return new IndexInfo(
                info.name(),
                info.modality(),
                info.dimension(),
                info.metric(),
                info.indexType(),
                info.status(),
                info.documentCount(),
                info.createdAt(),
                info.updatedAt(),
                metadata);
    }

    /**
     * 更新索引配置。
     *
     * <p>Weaviate schema 修改属于破坏性操作 (修改 vectorIndexType / distance 等通常需
     * 重建索引并迁移数据)，本实现未做自动迁移，故直接返回 false 并提示调用方手工处理。</p>
     *
     * @param indexName 索引名称
     * @param config    期望的新配置
     * @return false — 当前实现不支持在线更新
     */
    @Override
    protected boolean updateIndexConfigImpl(String indexName, VectorProperties.IndexConfig config) {
        log.warn("Weaviate updateIndexConfig: 在线修改 schema 不被支持，请手工迁移。index={}, config={}",
                indexName, config);
        return false;
    }

    /**
     * 获取索引运行统计 (文档数 + 索引类型 + 距离度量)。
     *
     * <p>当前实现复用 {@code describeIndexImpl} 的查询路径并叠加 {@code countDocumentsImpl}
     * 的真实文档计数；count 阶段失败时降级为 -1 以便上层区分「未知」与「真为 0」。</p>
     *
     * @param indexName 索引名称
     * @return 索引统计；class 不存在时返回 null
     */
    @Override
    protected IndexInfo getIndexStatsImpl(String indexName) {
        Result<WeaviateClass> result = weaviateClient.schema().classGetter().withClassName(indexName).run();
        if (result.hasErrors() || result.getResult() == null) {
            log.warn("Weaviate getIndexStats: class [{}] 不存在或读取失败", indexName);
            return null;
        }
        WeaviateClass clazz = result.getResult();
        long count;
        try {
            count = countDocumentsImpl(indexName);
        } catch (Exception e) {
            log.warn("Weaviate getIndexStats: count 失败，使用 -1 标记未知。class={}, error={}",
                    indexName, e.getMessage());
            count = -1L;
        }
        return buildIndexInfo(clazz, count);
    }

    // ====================================================================
    // §14.3 高级搜索 — 子类可选实现
    // ====================================================================

    /**
     * BM25 + 向量混合搜索。
     *
     * <p>利用 Weaviate 原生 {@code hybrid} 操作符：{@code alpha} 控制向量权重 (1.0=纯向量，
     * 0.0=纯 BM25)。当 {@code text} 为空时退化为关键字查询；{@code keywordQuery} 为空时
     * 退化为 {@link #searchByText(String, String, int, SearchOptions)}。</p>
     *
     * <p>GraphQL 返回 certainty 而非 hybrid score，统一归一化到 {@code [0, 1]} 区间
     * (Weaviate 默认 certainty 已落在该区间，直接使用)。</p>
     *
     * @param indexName     索引名称
     * @param text          文本查询 (可空；空时使用 keywordQuery)
     * @param keywordQuery  关键字查询 (可空；空时使用 text)
     * @param limit         返回上限
     * @param vectorWeight  向量权重 (0.0-1.0)
     * @param keywordWeight 关键字权重 (0.0-1.0)
     * @param inner         SearchOptions (minScore / filterExpression)
     * @return 搜索结果列表
     */
    @Override
    protected List<VectorSearchResult> hybridSearchImpl(String indexName, String text, String keywordQuery,
                                                        int limit, double vectorWeight, double keywordWeight,
                                                        SearchOptions inner) {
        String effectiveQuery = (text != null && !text.isBlank()) ? text
                : (keywordQuery != null ? keywordQuery : null);
        if (effectiveQuery == null || effectiveQuery.isBlank()) {
            log.warn("Weaviate hybridSearch: text 与 keywordQuery 都为空，降级为 searchByText");
            return searchByText(indexName, text, limit, inner);
        }
        double alpha = Math.max(0.0, Math.min(1.0, vectorWeight));
        String graphql = String.format("""
                {
                  Get {
                    %s(
                      hybrid: {
                        query: "%s"
                        alpha: %s
                      }
                      limit: %d
                    ) {
                      content
                      _additional {
                        id
                        score
                      }
                    }
                  }
                }
                """, indexName, effectiveQuery.replace("\"", "\\\""), alpha, limit);

        var result = weaviateClient.graphQL().raw().withQuery(graphql).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate hybridSearch failed: " + result.getError().getMessages());
        }
        Map<?, ?> data = (Map<?, ?>) result.getResult().getData();
        if (data == null) {
            return List.of();
        }
        Map<?, ?> getResult = (Map<?, ?>) data.get("Get");
        if (getResult == null) {
            return List.of();
        }
        List<?> items = (List<?>) getResult.get(indexName);
        if (items == null) {
            return List.of();
        }

        Double minScore = inner != null ? inner.getMinScore() : null;
        double threshold = minScore != null ? minScore : 0.0;
        List<VectorSearchResult> docs = new ArrayList<>();
        for (Object item : items) {
            Map<String, ?> itemMap = (Map<String, ?>) item;
            Map<String, ?> additional = (Map<String, ?>) itemMap.get("_additional");
            if (additional == null) {
                continue;
            }
            String id = (String) additional.get("id");
            String content = (String) itemMap.get("content");
            double score = 0.0;
            if (additional.get("score") != null) {
                score = ((Number) additional.get("score")).doubleValue();
            }
            if (id != null && score >= threshold) {
                docs.add(VectorSearchResult.of(id, content != null ? content : "", score));
            }
        }
        return docs;
    }

    @Override
    protected boolean optimizeImpl(String indexName) {
        return throwUnsupportedOps("optimize", indexName, "weaviate");
    }

    @Override
    protected boolean createAliasImpl(String indexName, String alias) {
        return throwUnsupportedOps("createAlias", indexName, "weaviate");
    }

    @Override
    protected boolean switchAliasImpl(String oldIndexName, String newIndexName, String alias) {
        return throwUnsupportedOps("switchAlias", newIndexName, "weaviate");
    }

    @Override
    protected boolean backupImpl(String indexName, String targetPath) {
        return throwUnsupportedOps("backup", indexName, "weaviate");
    }

    @Override
    protected boolean restoreImpl(String sourcePath, String indexName) {
        return throwUnsupportedOps("restore", indexName, "weaviate");
    }

    // ====================================================================
    // AbstractVectorService 抽象方法实现 (unchanged)
    // ====================================================================

    @Override
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        String graphql = String.format("""
                {
                  Get {
                    %s(offset: %d, limit: %d) {
                      content
                      _additional {
                        id
                      }
                    }
                  }
                }
                """, indexName, offset, limit);

        var result = weaviateClient.graphQL().raw().withQuery(graphql).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate listDocumentsHandler failed: " + result.getError().getMessages());
        }

        List<VectorRecord> docs = new ArrayList<>();
        Map<?, ?> data = (Map<?, ?>) result.getResult().getData();
        if (data == null) {
            return docs;
        }
        Map<?, ?> getResult = (Map<?, ?>) data.get("Get");
        if (getResult == null) {
            return docs;
        }
        var items = (List<?>) getResult.get(indexName);
        if (items == null) {
            return docs;
        }

        for (Object item : items) {
            Map<String, ?> itemMap = (Map<String, ?>) item;
            Map<String, ?> additional = (Map<String, ?>) itemMap.get("_additional");
            if (additional == null) {
                continue;
            }
            String id = (String) additional.get("id");
            String content = (String) itemMap.get("content");

            VectorRecord record = new VectorRecord()
                    .setId(id)
                    .setIndexName(indexName)
                    .setContent(new VectorContent.TextContent(content != null ? content : "", "text/plain"));
            docs.add(record);
        }
        return docs;
    }

    @Override
    protected List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }

        String vectorStr = vectorToString(vector);

        String graphql = String.format("""
                {
                  Get {
                    %s(
                      nearVector: {
                        vector: %s
                      }
                      limit: %d
                    ) {
                      content
                      _additional {
                        id
                        certainty
                      }
                    }
                  }
                }
                """, indexName, vectorStr, limit);

        var result = weaviateClient.graphQL().raw().withQuery(graphql).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate similaritySearchByVector failed: " + result.getError().getMessages());
        }

        List<Document> docs = new ArrayList<>();
        Map<?, ?> data = (Map<?, ?>) result.getResult().getData();
        if (data == null) {
            return docs;
        }
        Map<?, ?> getResult = (Map<?, ?>) data.get("Get");
        if (getResult == null) {
            return docs;
        }
        var items = (List<?>) getResult.get(indexName);
        if (items == null) {
            return docs;
        }

        for (Object item : items) {
            Map<String, ?> itemMap = (Map<String, ?>) item;
            Map<String, ?> additional = (Map<String, ?>) itemMap.get("_additional");
            if (additional == null) {
                continue;
            }

            String id = (String) additional.get("id");
            String content = (String) itemMap.get("content");
            double certainty = 0.0;
            if (additional.get("certainty") != null) {
                certainty = ((Number) additional.get("certainty")).doubleValue();
            }

            if (id != null && certainty >= minScore) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("score", certainty);
                Document doc = new Document(id, content != null ? content : "", metadata);
                docs.add(doc);
            }
        }
        return docs;
    }

    @Override
    protected void addEmbeddings(String indexName, List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        vectorStore.add(docs);
    }

    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        vectorStore.delete(ids);
    }

    @Override
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<VectorRecord> result = new ArrayList<>();
        for (String id : ids) {
            String graphql = String.format("""
                    {
                      Get {
                        %s(where: {path: ["_id"], operator: Equal, valueString: "%s"}) {
                          content
                          _additional {
                            id
                          }
                        }
                      }
                    }
                    """, indexName, id);

            var queryResult = weaviateClient.graphQL().raw().withQuery(graphql).run();
            if (queryResult.hasErrors()) {
                continue;
            }
            Map<?, ?> data = (Map<?, ?>) queryResult.getResult().getData();
            if (data == null) {
                continue;
            }
            Map<?, ?> getResult = (Map<?, ?>) data.get("Get");
            if (getResult == null) {
                continue;
            }
            var items = (List<?>) getResult.get(indexName);
            if (items == null || items.isEmpty()) {
                continue;
            }
            Map<String, ?> itemMap = (Map<String, ?>) items.get(0);
            String content = (String) itemMap.get("content");
            VectorRecord record = new VectorRecord()
                    .setId(id)
                    .setIndexName(indexName)
                    .setContent(new VectorContent.TextContent(content != null ? content : "", "text/plain"));
            result.add(record);
        }
        return result;
    }

    // ====================================================================
    // 内部工具
    // ====================================================================

    /**
     * 将 {@link WeaviateClass} 转换为统一的 {@link IndexInfo} 描述。
     *
     * <p>当 {@code documentCount} 为 null 时（来自 {@code listIndexesImpl}/{@code describeIndexImpl}），
     * 不填充文档数（写入 null），由上层根据语义解读。</p>
     *
     * @param clazz         Weaviate class 定义
     * @param documentCount 文档数（可为 null）
     * @return IndexInfo 实例
     */
    private IndexInfo buildIndexInfo(WeaviateClass clazz, Long documentCount) {
        String metric = null;
        if (clazz.getVectorIndexConfig() != null) {
            metric = clazz.getVectorIndexConfig().getDistance();
        }
        Map<String, Object> metadata = new HashMap<>();
        if (clazz.getReplicationConfig() != null
                && clazz.getReplicationConfig().getFactor() != null) {
            metadata.put("replicas", clazz.getReplicationConfig().getFactor());
        }
        if (clazz.getVectorizer() != null) {
            metadata.put("vectorizer", clazz.getVectorizer());
        }
        return new IndexInfo(
                clazz.getClassName(),
                Modality.TEXT,
                null,
                metric,
                clazz.getVectorIndexType(),
                IndexStatus.READY,
                documentCount,
                Instant.now(),
                Instant.now(),
                metadata);
    }

    /**
     * 将 float[] 序列化为 GraphQL 向量字面量字符串。
     *
     * <p>仅用于 {@code nearVector} / {@code hybrid} 子句的 inline 注入；
     * 大向量应改用 GraphQL 变量以避免请求体积膨胀。</p>
     *
     * @param vector 输入向量
     * @return 形如 {@code [0.1,0.2,0.3]} 的字符串
     */
    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

}
