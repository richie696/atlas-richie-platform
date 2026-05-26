package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Atlas 向量数据库实现类.
 *
 * <p>基于 MongoDB 7.0+ 的向量搜索功能提供向量存储和检索能力,
 * 支持向量+标量混合查询,适合需要复杂文档结构和混合查询的场景.
 *
 * <p>该实现通过 Spring Boot 自动配置激活,当配置文件中
 * {@code platform.component.vector.provider=mongodb} 时生效.
 *
 * @author Rydeen Platform Team
 * @version 5.0.0
 * @since 5.0.0
 */
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "mongodb")
@Slf4j
public class MongoDbAtlasVectorServiceImpl extends VectorServiceImpl implements VectorService {

    private static final String VECTOR_FIELD = "vector";
    private static final String VECTOR_INDEX = "vector_index";

    private final MongoTemplate mongoTemplate;

    /**
     * 构造方法.
     *
     * <p>通过依赖注入获取 VectorStore、EmbeddingModel 和 MongoTemplate 实例.
     *
     * @param vectorStore     Spring AI 向量存储接口,用于通用向量操作
     * @param embeddingModel  嵌入模型,用于文本向量化
     * @param mongoTemplate   MongoDB 模板类,用于执行原生 MongoDB 操作
     */
    @Autowired
    public MongoDbAtlasVectorServiceImpl(VectorStore vectorStore,
                                         @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                         MongoTemplate mongoTemplate) {
        super(vectorStore, embeddingModel);
        this.mongoTemplate = mongoTemplate;
    }


    /**
     * 为指定集合创建向量字段索引.
     *
     * <p>在 MongoDB Atlas 中,向量搜索需要先创建 {@code vectorSearch} 类型的索引.
     * 该方法创建一个基于向量路径的升序索引,是进行向量搜索的基础.
     *
     * <p>注意:实际的 vectorSearch 索引需要在 MongoDB Atlas UI 或通过 Atlas API 创建,
     * 该方法仅创建 MongoDB 原生的基础索引.
     *
     * @param indexName 集合名称,也作为索引名称使用
     * @param config    索引配置信息(当前实现中未使用完整配置,仅用集合名)
     */
    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        mongoTemplate.indexOps(indexName).createIndex(
                new Index().on(VECTOR_FIELD, Sort.Direction.ASC)
        );
        log.debug("为集合 [{}] 创建向量字段索引完成", indexName);
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
    public void deleteIndex(String indexName) {
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
    public boolean indexExists(String indexName) {
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
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
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
    public long countDocuments(String indexName) {
        long count = mongoTemplate.getCollection(indexName).countDocuments();
        log.debug("集合 [{}] 文档总数: {}", indexName, count);
        return count;
    }

    /**
     * 分页查询文档列表.
     *
     * <p>使用 MongoDB 的 skip 和 limit 实现分页查询.
     *
     * @param indexName 集合名称
     * @param offset    跳过的文档数量(从 0 开始)
     * @param limit     返回的最大文档数量
     * @return 文档列表
     */
    @Override
    protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
        Query query = new Query()
                .skip(offset)
                .limit(limit);
        // 只返回必要字段，排除 vector 节省带宽
        query.fields()
                .include("content")
                .include("metadata")
                .include("id")
                .exclude(VECTOR_FIELD);
        List<VectorDocument> docs = mongoTemplate.find(query, VectorDocument.class, indexName);
        log.debug("分页查询集合 [{}]，offset={}, limit={}, 返回{}条", indexName, offset, limit, docs.size());
        return docs;
    }

    /**
     * 通过向量进行相似度搜索.
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
     *   <li>执行聚合查询并转换结果</li>
     * </ol>
     *
     * <p>注意: 该方法硬编码使用 "documents" 集合和 "vector_index" 索引名,
     * 如需支持多集合/多索引,需要扩展该方法。</p>
     *
     * @param queryVector 查询向量,不能为空
     * @param limit       返回结果数量,必须大于 0
     * @return 搜索结果列表,按相似度降序排列
     * @throws IllegalArgumentException 当 queryVector 为空或 limit 小于等于 0 时抛出
     */
    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] queryVector, int limit) {
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }

        List<Double> queryVectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
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

        List<VectorSearchResult> searchResults = new ArrayList<>();
        for (Document doc : aggResults.getMappedResults()) {
            String id = doc.getString("id");
            String content = doc.getString("content");
            Double score = doc.getDouble("score");
            searchResults.add(VectorSearchResult.of(id, content, score, new float[0]));
        }

        return searchResults;
    }

}