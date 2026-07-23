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

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.IndexStatus;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.ai.service.RerankService;
import com.richie.component.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.DeleteResult;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB Atlas 向量数据库实现类.
 *
 * <p>基于 MongoDB 7.0+ 的向量搜索功能提供向量存储和检索能力,
 * 支持向量+标量混合查询,适合需要复杂文档结构和混合查询的场景.
 *
 * <p>该实现通过 Spring Boot 自动配置激活,当配置文件中
 * {@code platform.component.vector.provider=mongodb} 时生效.
 *
 * @author richie696
 * @version 2.0.0
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "mongodb")
@Slf4j
public class MongoDbAtlasVectorServiceImpl extends AbstractVectorService implements VectorService {

    private static final String VECTOR_FIELD = "vector";
    private static final String VECTOR_INDEX = "vector_index";

    private final MongoTemplate mongoTemplate;

    /**
     * 构造方法.
     *
     * <p>通过依赖注入获取 VectorStore、EmbeddingModel 和 MongoTemplate 实例.
     *
     * @param rerankService 重排序服务（可选）
     * @param vectorStore     Spring AI 向量存储接口,用于通用向量操作
     * @param embeddingModel  嵌入模型,用于文本向量化
     * @param mongoTemplate   MongoDB 模板类,用于执行原生 MongoDB 操作
     */
    @Autowired
    public MongoDbAtlasVectorServiceImpl(@Autowired(required = false) RerankService rerankService,
                                         VectorStore vectorStore,
                                         @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                         MongoTemplate mongoTemplate) {
        super(rerankService, vectorStore, embeddingModel);
        this.mongoTemplate = mongoTemplate;
    }

    // ==================== 索引管理（保留 v1 公开方法重写，多态分发仍生效） ====================

    /** MongoDB Atlas 默认向量维度（OpenAI text-embedding-3-small 等） */
    private static final int DEFAULT_DIMENSION = 1536;

    /**
     * 创建 MongoDB Atlas Vector Search 索引（Phase B 真实实现）。
     *
     * <p>使用 {@code createSearchIndexes} 命令在目标集合上创建
     * {@code type: "vectorSearch"} 类型的 Atlas Search 索引。</p>
     *
     * <p>维度取自 config（默认 1536），similarity 通过 metric 字段映射：
     * cosine / euclidean / dot。索引已存在时静默跳过。</p>
     *
     * @param indexName 集合名称
     * @param config    索引配置（dimension / metric）
     */
    @Override
    protected void createIndexImpl(String indexName, VectorProperties.IndexConfig config) {
        int dimension = config != null && config.getDimension() != null ? config.getDimension() : DEFAULT_DIMENSION;
        String metric = config != null && config.getMetric() != null ? config.getMetric() : "cosine";
        String similarity = mapSimilarity(metric);

        // Atlas Vector Search 索引定义：单字段 knnVector
        Document knnField = new Document()
                .append("type", "knnVector")
                .append("path", VECTOR_FIELD)
                .append("numDimensions", dimension)
                .append("similarity", similarity);

        Document searchIndex = new Document()
                .append("name", VECTOR_INDEX)
                .append("type", "vectorSearch")
                .append("definition", new Document().append("fields", List.of(knnField)));

        Document createCmd = new Document()
                .append("createSearchIndexes", indexName)
                .append("indexes", List.of(searchIndex));

        try {
            mongoTemplate.getDb().runCommand(createCmd);
            log.info("MongoDB Atlas Vector Search 索引创建完成: collection={}, dim={}, similarity={}",
                    indexName, dimension, similarity);
        } catch (Exception e) {
            // 索引已存在 / collection 不存在等场景不阻断调用方
            log.warn("MongoDB Atlas Vector Search 索引跳过（可能已存在）: collection={}, cause={}",
                    indexName, e.getMessage());
        }
    }

    /**
     * 将 metric 字符串映射为 Atlas Vector Search similarity 支持的值。
     * 未识别值默认 {@code cosine}。
     */
    private static String mapSimilarity(String metric) {
        if (metric == null) {
            return "cosine";
        }
        return switch (metric.toLowerCase()) {
            case "l2", "euclidean" -> "euclidean";
            case "ip", "dot" -> "dot";
            default -> "cosine";
        };
    }

    /**
     * 删除指定集合的所有索引.
     *
     * <p>该方法仅删除索引,不删除集合本身和数据.
     * 删除索引后需要重新创建才能进行向量搜索.
     *
     * @param indexName 集合名称
     */
    @Override
    protected void deleteIndexImpl(String indexName) {
        // 删除所有索引（不删除集合本身）
        // dropIndexes() 会删除该集合上的所有索引,不会影响文档数据
        mongoTemplate.getCollection(indexName).dropIndexes();
        log.info("已删除集合 [{}] 的所有索引", indexName);
    }

    /**
     * 检查集合是否存在.
     *
     * <p>通过检查集合是否存在来判断索引是否已创建.
     *
     * @param indexName 集合/索引名称
     * @return 集合存在返回 true,否则返回 false
     */
    @Override
    protected boolean indexExistsImpl(String indexName) {
        boolean exists = mongoTemplate.collectionExists(indexName);
        log.debug("集合 [{}] 是否存在: {}", indexName, exists);
        return exists;
    }

    /**
     * 获取索引配置信息.
     *
     * <p>当前实现仅返回基础信息(名称),
     * 如需更详细的配置(维度、索引类型等)需要结合业务元数据存储扩展.
     *
     * @param indexName 集合/索引名称
     * @return 索引配置对象,如果集合不存在则返回 null
     */
    @Override
    protected VectorProperties.IndexConfig getIndexConfigImpl(String indexName) {
        if (!mongoTemplate.collectionExists(indexName)) return null;
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setName(indexName);
        log.debug("获取集合 [{}] 的索引配置: {}", indexName, config);
        return config;
    }

    /**
     * 统计集合中的文档总数.
     *
     * @param indexName 集合名称
     * @return 文档数量
     */
    @Override
    protected long countDocumentsImpl(String indexName) {
        long count = mongoTemplate.getCollection(indexName).countDocuments();
        log.debug("集合 [{}] 文档总数: {}", indexName, count);
        return count;
    }

    // ==================== v2 抽象方法实现 ====================

    /**
     * 分页列出索引内文档.
     *
     * <p>使用 MongoDB 的 skip 和 limit 实现分页查询.
     *
     * @param indexName 集合名称
     * @param offset    跳过的文档数量(从 0 开始)
     * @param limit     返回的最大文档数量
     * @return 文档列表
     */
    @Override
    protected long truncateIndexImpl(String indexName) {
        DeleteResult result = mongoTemplate.getCollection(indexName).deleteMany(new Document());
        log.info("MongoDB Atlas 清空集合 [{}]，删除文档数={}", indexName, result.getDeletedCount());
        return result.getDeletedCount();
    }

    /**
     * 向量集合前缀 — 仅 {@code vector_} 开头的集合被视为向量索引.
     * <p>
     * 该约定允许同一 MongoDB 数据库中并存业务集合与向量集合,
     * {@link #listIndexesImpl()} 通过此前缀过滤业务集合.
     */
    private static final String VECTOR_COLLECTION_PREFIX = "vector_";

    /**
     * 列出当前数据库中所有向量索引（{@code vector_} 前缀集合）.
     *
     * <p>遍历 {@link MongoTemplate#getCollectionNames()} 过滤出
     * {@code vector_} 前缀的集合,并附带每个集合的文档总数返回.
     * 在 MongoDB Atlas 实现里"索引名 = 集合名",因此集合列表即索引列表.</p>
     *
     * @return 索引信息列表（无匹配前缀时返回空列表）
     */
    @Override
    protected List<IndexInfo> listIndexesImpl() {
        List<IndexInfo> indexes = new ArrayList<>();
        for (String name : mongoTemplate.getCollectionNames()) {
            if (name == null || !name.startsWith(VECTOR_COLLECTION_PREFIX)) {
                continue;
            }
            long count = mongoTemplate.getCollection(name).countDocuments();
            indexes.add(new IndexInfo(name, Modality.TEXT, null, null,
                    "vectorSearch", IndexStatus.READY, count, null, null, null));
        }
        log.debug("MongoDB Atlas 列出 vector_ 前缀向量集合: count={}", indexes.size());
        return indexes;
    }

    /**
     * 描述指定索引（集合）的元信息.
     *
     * <p>返回的 {@link IndexInfo} 包含:
     * <ul>
     *   <li>name — 索引/集合名</li>
     *   <li>documentCount — 当前文档数</li>
     *   <li>metadata — provider 私有元信息（engine / searchIndex 等）</li>
     * </ul>
     * 集合不存在时返回 status=UNKNOWN 的占位对象,便于上层调用方统一处理.</p>
     *
     * @param indexName 集合/索引名称
     * @return 索引描述信息;集合不存在时返回 UNKNOWN 状态的占位 IndexInfo
     */
    @Override
    protected IndexInfo describeIndexImpl(String indexName) {
        if (indexName == null || !mongoTemplate.collectionExists(indexName)) {
            log.warn("MongoDB Atlas describeIndex: collection [{}] 不存在", indexName);
            return new IndexInfo(indexName, null, null, null, null,
                    IndexStatus.UNKNOWN, 0L, null, null, null);
        }
        long count = mongoTemplate.getCollection(indexName).countDocuments();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engine", "mongodb-atlas");
        metadata.put("searchIndex", VECTOR_INDEX);
        metadata.put("vectorField", VECTOR_FIELD);
        IndexInfo info = new IndexInfo(indexName, Modality.TEXT, null, null,
                "vectorSearch", IndexStatus.READY, count, null, null, metadata);
        log.debug("MongoDB Atlas describeIndex: collection={}, count={}", indexName, count);
        return info;
    }

    /**
     * 更新指定索引的配置（维度 / 度量方式）.
     *
     * <p>MongoDB Atlas Vector Search 索引不支持原地修改,
     * 因此采用 <strong>drop + 重建</strong> 的策略:先 {@code dropSearchIndex}
     * 删除旧的 search index,再调用 {@link #createIndexImpl(String, VectorProperties.IndexConfig)} 重建.</p>
     *
     * <p>集合不存在时直接返回 false;
     * drop 失败（索引可能不存在）会被捕获并继续执行 create,以幂等方式更新.</p>
     *
     * @param indexName 集合/索引名称
     * @param config    新索引配置
     * @return true=集合存在且重建流程已发起,false=集合不存在
     */
    @Override
    protected boolean updateIndexConfigImpl(String indexName, VectorProperties.IndexConfig config) {
        if (indexName == null || !mongoTemplate.collectionExists(indexName)) {
            log.warn("MongoDB Atlas updateIndexConfig 失败: collection [{}] 不存在", indexName);
            return false;
        }
        // step 1: drop 旧的 Vector Search 索引
        try {
            Document dropCmd = new Document()
                    .append("dropSearchIndex", indexName)
                    .append("name", VECTOR_INDEX);
            mongoTemplate.getDb().runCommand(dropCmd);
            log.info("MongoDB Atlas 已 drop 旧 Vector Search 索引: collection={}, searchIndex={}",
                    indexName, VECTOR_INDEX);
        } catch (Exception e) {
            // 索引可能不存在 —— 不阻断,继续重建以达到幂等更新效果
            log.warn("MongoDB Atlas drop 旧 Vector Search 索引跳过（可能不存在）: collection={}, cause={}",
                    indexName, e.getMessage());
        }
        // step 2: 重建
        createIndexImpl(indexName, config);
        log.info("MongoDB Atlas 索引配置更新完成（drop+rebuild）: collection={}", indexName);
        return true;
    }

    /**
     * 获取指定索引的运行期统计信息.
     *
     * <p>返回的 {@link IndexInfo} 字段:
     * <ul>
     *   <li>documentCount — 当前文档数（粗略估计,provider-specific）</li>
     *   <li>status — READY / UNKNOWN</li>
     *   <li>updatedAt — 当前查询时间戳</li>
     * </ul>
     * 集合不存在时返回 status=UNKNOWN 占位对象.</p>
     *
     * @param indexName 集合/索引名称
     * @return 索引统计信息;集合不存在时返回 UNKNOWN 状态的占位 IndexInfo
     */
    @Override
    protected IndexInfo getIndexStatsImpl(String indexName) {
        if (indexName == null || !mongoTemplate.collectionExists(indexName)) {
            log.warn("MongoDB Atlas getIndexStats: collection [{}] 不存在", indexName);
            return new IndexInfo(indexName, null, null, null, null,
                    IndexStatus.UNKNOWN, 0L, null, null, null);
        }
        long count = mongoTemplate.getCollection(indexName).countDocuments();
        IndexInfo info = new IndexInfo(indexName, Modality.TEXT, null, null,
                "vectorSearch", IndexStatus.READY, count, null, Instant.now(), null);
        log.debug("MongoDB Atlas getIndexStats: collection={}, count={}", indexName, count);
        return info;
    }

    @Override
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        Query query = new Query()
                .skip(offset)
                .limit(limit);
        // 只返回必要字段，排除 vector 节省带宽
        query.fields()
                .include("content")
                .include("metadata")
                .include("id")
                .exclude(VECTOR_FIELD);
        List<VectorRecord> docs = mongoTemplate.find(query, VectorRecord.class, indexName);
        log.debug("分页查询集合 [{}]，offset={}, limit={}, 返回{}条", indexName, offset, limit, docs.size());
        return docs;
    }

    /**
     * 按 ID 列表批量读取文档.
     *
     * @param indexName 集合名称
     * @param ids       ID 列表
     * @return 命中的文档列表（保持顺序）
     */
    @Override
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(ids));
        return mongoTemplate.find(query, VectorRecord.class, indexName);
    }

    /**
     * 按 ID 列表批量删除文档.
     *
     * @param indexName 集合名称
     * @param ids       待删除的 ID 列表
     */
    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(ids));
        mongoTemplate.remove(query, indexName);
        log.debug("从集合 [{}] 批量删除 {} 条文档", indexName, ids.size());
    }

    /**
     * 批量写入已嵌入的 Spring AI 文档.
     *
     * <p>委托给 {@link VectorStore#add(java.util.List)} 完成持久化，
     * 由 Spring AI MongoDB Atlas VectorStore 处理实际入库逻辑。
     *
     * @param indexName 目标索引名称
     * @param docs      Spring AI Document 列表
     */
    @Override
    protected void addEmbeddings(String indexName, List<org.springframework.ai.document.Document> docs) {
        if (docs == null || docs.isEmpty()) return;
        vectorStore.add(docs);
    }

    /**
     * 通过向量进行相似度搜索（含 minScore 阈值过滤）.
     *
     * <p>使用 MongoDB Atlas 的 {@code $vectorSearch} 聚合管道执行向量相似度搜索.
     * 该方法使用余弦相似度作为距离度量.
     *
     * <p>搜索流程:
     * <ol>
     *   <li>参数校验(向量非空、limit 大于 0)</li>
     *   <li>将 float 数组转换为 Double 列表( MongoDB 驱动需要)</li>
     *   <li>构建 $vectorSearch 聚合阶段,使用预定义的 vector_index</li>
     *   <li>构建 $project 聚合阶段,提取所需字段和搜索分数</li>
     *   <li>执行聚合查询，转换为 Spring AI Document</li>
     *   <li>按 minScore 阈值过滤</li>
     * </ol>
     *
     * @param indexName 目标索引名称
     * @param vector    查询向量,不能为空
     * @param limit     返回结果数量,必须大于 0
     * @param minScore  最小相似度阈值（小于此分数的结果会被过滤掉）
     * @return 命中文档列表（按相似度降序）
     */
    @Override
    protected List<org.springframework.ai.document.Document> similaritySearchByVector(
            String indexName, float[] vector, int limit, double minScore) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }

        List<Double> queryVectorList = new ArrayList<>(vector.length);
        for (float v : vector) {
            queryVectorList.add((double) v);
        }

        // 构建 $vectorSearch 聚合阶段
        AggregationOperation vectorSearchStage = context -> new Document("$vectorSearch",
            new Document("index", VECTOR_INDEX)
                .append("path", VECTOR_FIELD)
                .append("queryVector", queryVectorList)
                .append("numCandidates", Math.max(limit * 10, 100))
                .append("limit", limit)
        );

        // 构建 $project 聚合阶段 - 不返回 vector 字段节省带宽
        AggregationOperation projectStage = context -> new Document("$project",
            new Document("id", "$_id")
                .append("content", "$content")
                .append("score", new Document("$meta", "vectorSearchScore"))
        );

        Aggregation aggregation = Aggregation.newAggregation(
            vectorSearchStage, projectStage
        );

        AggregationResults<Document> aggResults = mongoTemplate.aggregate(aggregation, indexName, Document.class);

        List<org.springframework.ai.document.Document> results = new ArrayList<>();
        for (Document doc : aggResults.getMappedResults()) {
            String id = doc.getString("id");
            String content = doc.getString("content");
            Double score = doc.getDouble("score");
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("content", content != null ? content : "");
            org.springframework.ai.document.Document aiDoc = org.springframework.ai.document.Document.builder()
                    .id(id)
                    .text(content != null ? content : "")
                    .metadata(metadata)
                    .score(score)
                    .build();
            results.add(aiDoc);
        }

        if (minScore > 0.0) {
            results.removeIf(d -> d.getScore() == null || d.getScore() < minScore);
        }
        return results;
    }

    // ==================== §14.4 运维/别名/备份 — Atlas Vector Search 托管语义均不直接支持 ====================

    /**
     * Atlas Vector Search 是 MongoDB 托管服务,无 SDK 级 optimize API.
     *
     * <p>索引调优通过 Atlas 控制台 / Admin API 完成(例如调整 efConstruction、
     * quantization 等 Vector Search 选项需 drop + 重建索引,见
     * {@link #updateIndexConfigImpl(String, VectorProperties.IndexConfig)}).</p>
     *
     * @param indexName 集合/索引名称
     * @throws UnsupportedOperationException Atlas 不支持 SDK 级 optimize
     */
    @Override
    protected boolean optimizeImpl(String indexName) {
        return throwUnsupportedOps("optimize", indexName, "mongodb");
    }

    /**
     * MongoDB / Atlas Vector Search 不存在 vector index alias 概念.
     *
     * <p>MongoDB 的普通集合支持 {@code db.collection.renameCollection(...)} 用于"重命名",
     * 但 Atlas Vector Search 索引维度没有 alias 语义;若需做版本切换,推荐
     * drop + rebuild 或写入多集合后由应用层路由.</p>
     *
     * @param indexName 集合/索引名称
     * @param alias     别名
     * @throws UnsupportedOperationException Atlas 无 vector index alias 概念
     */
    @Override
    protected boolean createAliasImpl(String indexName, String alias) {
        return throwUnsupportedOps("createAlias", indexName, "mongodb");
    }

    /**
     * 同 {@link #createAliasImpl} — MongoDB Atlas 不支持 vector index alias 切换.
     *
     * @param oldIndexName 旧索引名称
     * @param newIndexName 新索引名称
     * @param alias        别名
     * @throws UnsupportedOperationException Atlas 无 vector index alias 概念
     */
    @Override
    protected boolean switchAliasImpl(String oldIndexName, String newIndexName, String alias) {
        return throwUnsupportedOps("switchAlias", newIndexName, "mongodb");
    }

    /**
     * Atlas backup 由 MongoDB 托管(continuous backup / on-demand snapshots),
     * 通过 Atlas 控制台 / Cloud Manager / Ops Manager API 调用,
     * 不在 VectorService SDK 抽象层支持范围内.
     *
     * @param indexName   集合/索引名称
     * @param targetPath  备份目标路径
     * @throws UnsupportedOperationException Atlas backup 为 managed service
     */
    @Override
    protected boolean backupImpl(String indexName, String targetPath) {
        return throwUnsupportedOps("backup", indexName, "mongodb");
    }

    /**
     * 同 {@link #backupImpl} — Atlas restore 同样由托管服务提供.
     *
     * @param sourcePath 备份源路径
     * @param indexName  集合/索引名称
     * @throws UnsupportedOperationException Atlas restore 为 managed service
     */
    @Override
    protected boolean restoreImpl(String sourcePath, String indexName) {
        return throwUnsupportedOps("restore", indexName, "mongodb");
    }

}