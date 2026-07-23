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
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.ai.service.RerankService;
import com.richie.component.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 向量数据库服务实现类.
 *
 * <p>基于 Neo4j 图数据库的向量检索能力，提供统一的向量存储和检索接口.
 * Neo4j 作为图数据库，天然支持关系型和图结构数据，适合需要向量检索与图算法结合的场景，
 * 如知识图谱、社交网络、推荐系统等复杂关系分析业务。</p>
 *
 * <p>该实现通过 Neo4j 的向量索引（Vector Index）功能实现高效的向量相似度搜索，
 * 使用 Cypher 查询语言进行向量检索操作。</p>
 *
 * @author 王锦阳
 * @version 2.0.0
 * @since 2.0.0
 * @see VectorService
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "neo4j")
public class Neo4jVectorServiceImpl extends AbstractVectorService implements VectorService {

    private static final String VECTOR_LABEL_PREFIX = "VectorDocument_";

    /**
     * Neo4j 数据库驱动程序.
     *
     * <p>用于创建会话并执行 Cypher 查询语句.</p>
     */
    private final Driver driver;

    /**
     * 构造函数.
     *
     * <p>通过构造器注入所需的依赖组件，包括向量存储、嵌入模型和 Neo4j 驱动.</p>
     *
     * @param rerankService 重排序服务（可选）
     * @param vectorStore     Spring AI 向量存储接口，用于文档的添加和删除
     * @param embeddingModel  嵌入模型，用于将文本转换为向量表示
     * @param driver          Neo4j 数据库驱动程序，用于执行 Cypher 查询
     */
    @Autowired
    public Neo4jVectorServiceImpl(@Autowired(required = false) RerankService rerankService,
                                  VectorStore vectorStore,
                                  @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                  Driver driver) {
        super(rerankService, vectorStore, embeddingModel);
        this.driver = driver;
    }

    // ====================================================================
    // §14 索引管理 — createIndexImpl / deleteIndexImpl / indexExistsImpl
    //               getIndexConfigImpl / countDocumentsImpl / truncateIndexImpl
    // ====================================================================

    /** Neo4j 默认向量维度（OpenAI text-embedding-3-small 等） */
    private static final int DEFAULT_DIMENSION = 1536;

    /**
     * 创建向量索引（Phase B 真实实现 — Neo4j Vector Index）。
     *
     * <p>分两步执行：</p>
     * <ol>
     *   <li>创建 UNIQUE CONSTRAINT 保证 id 唯一性（沿用 Phase A 行为）</li>
     *   <li>创建 VECTOR INDEX，维度取自 config（默认 1536），
     *       similarity_function 根据 metric 字段映射：cosine / euclidean / dot</li>
     * </ol>
     *
     * <p>使用 {@code IF NOT EXISTS} 保证可重复执行；Neo4j 5.11+ 支持向量索引。</p>
     *
     * @param indexName 索引名称（用于构建节点标签 VectorDocument_{indexName}）
     * @param config    索引配置（dimension / metric）
     */
    @Override
    protected void createIndexImpl(String indexName, VectorProperties.IndexConfig config) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        int dimension = config != null && config.getDimension() != null ? config.getDimension() : DEFAULT_DIMENSION;
        String metric = config != null && config.getMetric() != null ? config.getMetric() : "cosine";
        String similarity = mapMetric(metric);

        String constraintCypher = "CREATE CONSTRAINT IF NOT EXISTS FOR (n:" + label + ") REQUIRE n.id IS UNIQUE";
        // 字段名为 embedding；Neo4j 5.11+ vector.similarity_function 支持 cosine/euclidean/dot
        String vectorIndexCypher = "CREATE VECTOR INDEX `" + indexName + "_idx` IF NOT EXISTS "
                + "FOR (n:" + label + ") ON (n.embedding) "
                + "OPTIONS {indexConfig: {`vector.dimensions`: " + dimension
                + ", `vector.similarity_function`: '" + similarity + "'}}";

        try (Session session = driver.session()) {
            session.run(constraintCypher);
            session.run(vectorIndexCypher);
            log.info("Neo4j VECTOR INDEX 创建完成: label={}, dim={}, similarity={}", label, dimension, similarity);
        }
    }

    /**
     * 将 metric 字符串映射为 Neo4j {@code vector.similarity_function} 支持的值。
     * 未识别值默认 {@code cosine}，与大多数 embedding 模型（OpenAI）默认对齐。
     */
    private static String mapMetric(String metric) {
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
     * 删除向量索引.
     *
     * <p>删除指定的向量索引及其关联的唯一性约束.
     * 使用 IF EXISTS 确保即使索引不存在也不会抛出异常。</p>
     *
     * @param indexName 索引名称
     */
    @Override
    protected void deleteIndexImpl(String indexName) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "DROP CONSTRAINT IF EXISTS FOR (n:" + label + ") REQUIRE n.id IS UNIQUE";
        try (Session session = driver.session()) {
            session.run(cypher);
        }
    }

    /**
     * 检查索引是否存在.
     *
     * <p>通过查询 Neo4j 的约束列表来验证指定索引是否存在.
     * 检查逻辑基于约束描述（description）中是否包含索引标签和唯一性标识。</p>
     *
     * @param indexName 索引名称
     * @return 如果索引存在返回 true，否则返回 false
     */
    @Override
    protected boolean indexExistsImpl(String indexName) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "SHOW CONSTRAINTS";
        try (Session session = driver.session()) {
            Result result = session.run(cypher);
            while (result.hasNext()) {
                Record record = result.next();
                // description 字段格式示例：:id IS UNIQUE FOR (n:VectorDocument_documents)
                String description = record.get("description").asString("");
                // 需要同时匹配标签名和唯一性约束类型
                if (description.contains(label) && description.contains("id IS UNIQUE")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取索引配置信息.
     *
     * <p>返回指定索引的配置详情. 由于 Neo4j 向量索引配置相对简单，
     * 当前实现仅返回索引名称。</p>
     *
     * @param indexName 索引名称
     * @return 索引配置对象，包含索引的基本信息
     * @throws UnsupportedOperationException 如果索引不存在
     */
    @Override
    protected VectorProperties.IndexConfig getIndexConfigImpl(String indexName) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "SHOW CONSTRAINTS";
        try (Session session = driver.session()) {
            Result result = session.run(cypher);
            while (result.hasNext()) {
                Record record = result.next();
                String description = record.get("description").asString("");
                if (description.contains(label) && description.contains("id IS UNIQUE")) {
                    VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
                    config.setName(indexName);
                    return config;
                }
            }
        }
        throw new UnsupportedOperationException("索引不存在: %s".formatted(indexName));
    }

    /**
     * 统计索引中的文档数量.
     *
     * <p>使用 MATCH 语句统计指定标签下的所有节点数量.
     * 这是获取向量库规模的基础方法。</p>
     *
     * @param indexName 索引名称
     * @return 文档数量，如果查询失败或无结果返回 0
     */
    @Override
    protected long countDocumentsImpl(String indexName) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "MATCH (n:" + label + ") RETURN count(n) as count";
        try (Session session = driver.session()) {
            Result result = session.run(cypher);
            if (result.hasNext()) {
                return result.next().get("count").asLong();
            }
        }
        return 0;
    }

    // ====================================================================
    // §14.2 索引信息查询 — listIndexesImpl / describeIndexImpl
    //                     getIndexStatsImpl / updateIndexConfigImpl
    // ====================================================================

    /**
     * 列出当前数据库中所有 Neo4j Vector Index.
     *
     * <p>通过 {@code SHOW INDEXES} 列出全部 {@code type = 'VECTOR'} 的索引，
     * 反向解析节点标签 {@code VectorDocument_<indexName>} 得到业务索引名；
     * 其他业务的 VECTOR 索引会被前缀过滤掉。每个 {@link IndexInfo} 的
     * {@code metric} / {@code dimension} 字段尽量从
     * {@code options.indexConfig.vector.*} 字段提取，文档数通过
     * {@link #countDocuments} 拿到。</p>
     *
     * @return Vector Index 信息列表；无索引时返回空列表
     */
    @Override
    protected List<IndexInfo> listIndexesImpl() {
        String cypher = "SHOW INDEXES YIELD name, type, labelsOrTypes, options WHERE type = 'VECTOR'";
        List<IndexInfo> indexes = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher);
            while (result.hasNext()) {
                Record record = result.next();
                List<Object> labels = record.get("labelsOrTypes").asList();
                if (labels == null || labels.isEmpty()) {
                    continue;
                }
                String label = labels.get(0).toString();
                if (label == null || !label.startsWith(VECTOR_LABEL_PREFIX)) {
                    continue;
                }
                String indexName = label.substring(VECTOR_LABEL_PREFIX.length());
                String vectorIndexName = record.get("name").asString();

                Integer dimension = null;
                String similarity = null;
                Value optionsValue = record.get("options");
                if (!optionsValue.isNull()) {
                    Map<String, Object> options = optionsValue.asMap();
                    Object indexConfigObj = options.get("indexConfig");
                    if (indexConfigObj instanceof Map<?, ?> indexConfig) {
                        Object dims = indexConfig.get("vector.dimensions");
                        if (dims instanceof Number n) {
                            dimension = n.intValue();
                        }
                        Object sim = indexConfig.get("vector.similarity_function");
                        if (sim != null) {
                            similarity = sim.toString();
                        }
                    }
                }

                long count = countDocuments(indexName);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("vectorIndexName", vectorIndexName);
                metadata.put("label", label);

                IndexInfo info = new IndexInfo(
                        indexName,
                        Modality.TEXT,
                        dimension,
                        similarity,
                        "vector",
                        IndexStatus.READY,
                        count,
                        null,
                        null,
                        metadata
                );
                indexes.add(info);
            }
        }
        return indexes;
    }

    /**
     * 描述指定索引的完整信息.
     *
     * <p>从 {@code SHOW INDEXES} 中定位目标 {@code VectorDocument_<indexName>} 标签对应的向量索引，
     * 提取 {@code vector.dimensions} / {@code vector.similarity_function} 作为 dimension / metric，
     * 并复用 {@link #countDocuments} 获取文档数量。
     * 若该索引在数据库中不存在，dimension / metric 为空且 vectorIndexName 为空。</p>
     *
     * @param indexName 索引名称
     * @return 索引完整描述信息
     */
    @Override
    protected IndexInfo describeIndexImpl(String indexName) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "SHOW INDEXES YIELD name, type, labelsOrTypes, options WHERE type = 'VECTOR'";

        String vectorIndexName = null;
        Integer dimension = null;
        String similarity = null;
        try (Session session = driver.session()) {
            Result result = session.run(cypher);
            while (result.hasNext()) {
                Record record = result.next();
                List<Object> labels = record.get("labelsOrTypes").asList();
                if (labels == null || labels.isEmpty()) {
                    continue;
                }
                String currentLabel = labels.get(0).toString();
                if (!label.equals(currentLabel)) {
                    continue;
                }
                vectorIndexName = record.get("name").asString();
                Value optionsValue = record.get("options");
                if (!optionsValue.isNull()) {
                    Map<String, Object> options = optionsValue.asMap();
                    Object indexConfigObj = options.get("indexConfig");
                    if (indexConfigObj instanceof Map<?, ?> indexConfig) {
                        Object dims = indexConfig.get("vector.dimensions");
                        if (dims instanceof Number n) {
                            dimension = n.intValue();
                        }
                        Object sim = indexConfig.get("vector.similarity_function");
                        if (sim != null) {
                            similarity = sim.toString();
                        }
                    }
                }
                break;
            }
        }

        long count = countDocuments(indexName);

        Map<String, Object> metadata = new HashMap<>();
        if (vectorIndexName != null) {
            metadata.put("vectorIndexName", vectorIndexName);
        }
        metadata.put("label", label);

        return new IndexInfo(
                indexName,
                Modality.TEXT,
                dimension,
                similarity,
                "vector",
                IndexStatus.READY,
                count,
                null,
                null,
                metadata
        );
    }

    /**
     * 更新索引配置.
     *
     * <p>Neo4j 5.x 暂不支持 {@code ALTER VECTOR INDEX ...} 语法，本实现走
     * {@code DROP INDEX ... IF EXISTS + 重新 CREATE} 的等价语义重建索引，
     * 同时清理原先的 UNIQUE CONSTRAINT（重建时由 {@link #createIndexImpl}
     * 重新创建）。重建不会迁移已有数据；如有需要请先调用
     * {@link #truncateIndex} 或导出备份。</p>
     *
     * @param indexName 索引名称
     * @param config    新配置（dimension / metric）
     * @return true=重建成功，false=清理或重建任一阶段抛异常
     */
    @Override
    protected boolean updateIndexConfigImpl(String indexName, VectorProperties.IndexConfig config) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String vectorIdxName = indexName + "_idx";
        String dropIdxCypher = "DROP INDEX `" + vectorIdxName + "` IF EXISTS";
        String dropConstrCypher = "DROP CONSTRAINT IF EXISTS FOR (n:" + label + ") REQUIRE n.id IS UNIQUE";

        try (Session session = driver.session()) {
            session.run(dropIdxCypher);
            session.run(dropConstrCypher);
            log.info("Neo4j 索引配置更新前清理完成: label={}, vectorIdx={}", label, vectorIdxName);
        } catch (Exception e) {
            log.warn("Neo4j 索引配置更新失败（清理阶段）[{}]: {}", indexName, e.getMessage());
            return false;
        }

        try {
            createIndexImpl(indexName, config);
            log.info("Neo4j 索引配置更新完成: indexName={}", indexName);
            return true;
        } catch (Exception e) {
            log.warn("Neo4j 索引配置更新失败（重建阶段）[{}]: {}", indexName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取索引统计信息.
     *
     * <p>当前实现返回 {@code IndexInfo(name, count, vectorIndexName)}：
     * 索引名称、文档数量（{@link #countDocuments}）和底层 Neo4j
     * {@code VECTOR INDEX} 真实名称 (即 {@code <indexName>_idx})。
     * 更丰富的指标需要 Neo4j Enterprise 监控能力，本实现不暴露。</p>
     *
     * @param indexName 索引名称
     * @return 索引统计信息
     */
    @Override
    protected IndexInfo getIndexStatsImpl(String indexName) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        long count = countDocuments(indexName);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("vectorIndexName", indexName + "_idx");
        metadata.put("label", label);
        return new IndexInfo(
                indexName,
                Modality.TEXT,
                null,
                null,
                "vector",
                IndexStatus.READY,
                count,
                null,
                null,
                metadata
        );
    }

    // ====================================================================
    // 抽象方法实现 — truncateIndexImpl / similaritySearchByVector
    //               addEmbeddings / deleteByIds / getByIds / listDocumentsImpl
    // ====================================================================

    /**
     * 清空索引中的全部节点.
     *
     * <p>使用 {@code MATCH ... DETACH DELETE} 删除索引标签下所有节点，
     * 借助 {@link SummaryCounters#nodesDeleted()} 拿到真实删除数。
     * 删除前先调用一次 {@link #countDocuments} 取预估计数，便于在
     * 日志中对比 {@code 预估 vs 实际} 删除数差距。</p>
     *
     * @param indexName 索引名称
     * @return 实际删除的节点数（基于 Cypher 执行计数）
     */
    @Override
    protected long truncateIndexImpl(String indexName) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        long previousCount = countDocuments(indexName);
        String cypher = "MATCH (n:" + label + ") DETACH DELETE n";
        try (Session session = driver.session()) {
            ResultSummary summary = session.run(cypher).consume();
            SummaryCounters counters = summary.counters();
            long deleted = counters.nodesDeleted();
            log.info("Neo4j 清空索引 [{}] 完成，预估删除={}, 实际删除={}", indexName, previousCount, deleted);
            return Math.max(deleted, previousCount);
        }
    }

    /**
     * 根据向量进行相似度搜索.
     *
     * <p>使用 Neo4j 的向量索引（{@code db.index.vector.queryNodes}）进行高效的
     * 向量相似度搜索，返回与查询向量最相似的 {@link Document} 列表。</p>
     *
     * @param indexName 目标索引
     * @param vector    查询向量，不能为空且长度必须大于 0
     * @param limit     返回结果的最大数量，必须大于 0
     * @param minScore  相似度阈值，低于该分的结果将被过滤
     * @return 按相似度降序排列的 Spring AI Document 列表
     * @throws IllegalArgumentException 如果查询向量为空或 limit 不合法
     */
    @Override
    protected List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }

        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "CALL db.index.vector.queryNodes($indexName, $limit, $queryVector) YIELD node, score " +
                "RETURN node.id AS id, node.content AS content, score";

        try (Session session = driver.session()) {
            Result result = session.run(cypher,
                    Values.parameters(
                            "indexName", label,
                            "limit", limit,
                            "queryVector", vector
                    ));

            List<Document> docs = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                String id = record.get("id").asString();
                String content = record.get("content").asString("");
                double score = record.get("score").asDouble();
                if (score < minScore) {
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("score", score);
                Document doc = Document.builder()
                        .id(id)
                        .text(content)
                        .metadata(metadata)
                        .score(score)
                        .build();
                docs.add(doc);
            }
            return docs;
        }
    }

    /**
     * 批量写入已嵌入的 Spring AI 文档.
     *
     * <p>复用 Spring AI VectorStore 的标准 {@code add(List<Document>)} API，
     * 由其 Neo4jVectorStore 实现负责将 embedding 写入向量索引。</p>
     *
     * @param indexName 目标索引
     * @param docs      待写入的 Spring AI 文档列表（已嵌入完成）
     */
    @Override
    protected void addEmbeddings(String indexName, List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        vectorStore.add(docs);
    }

    /**
     * 按 ID 列表删除索引内文档.
     *
     * <p>使用 Cypher MATCH ... WHERE id IN $ids DELETE 执行批量删除，
     * 与原始 Cypher 路径所使用的 {@code VectorDocument_{indexName}} 标签保持一致。</p>
     *
     * @param indexName 目标索引
     * @param ids       待删除的文档 ID 列表
     */
    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "MATCH (n:" + label + ") WHERE n.id IN $ids DELETE n";
        try (Session session = driver.session()) {
            session.run(cypher, Values.parameters("ids", ids));
        }
    }

    /**
     * 按 ID 列表批量获取 {@link VectorRecord}.
     *
     * <p>使用 Cypher MATCH ... WHERE id IN $ids RETURN 批量读取，
     * 并将节点属性映射为 v2 模型。文本内容用 {@link VectorContent.TextContent} 包装，
     * metadata 直接透传节点上的 metadata 属性。</p>
     *
     * @param indexName 目标索引
     * @param ids       待获取的文档 ID 列表
     * @return 查询到的 VectorRecord 列表（顺序与输入 ids 一致，缺失条目跳过）
     */
    @Override
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "MATCH (n:" + label + ") WHERE n.id IN $ids RETURN n";
        return queryToRecords(label, cypher, Values.parameters("ids", ids));
    }

    /**
     * 分页列出索引中的文档（{@link AbstractVectorService#listDocuments} 的实现）。
     *
     * <p>支持分页查询，返回指定偏移量和限制数量的 {@link VectorRecord} 列表.
     * 结果按文档 ID 升序排列，确保分页结果的一致性。</p>
     *
     * @param indexName 索引名称
     * @param offset    起始偏移量（从 0 开始）
     * @param limit     返回的最大文档数量
     * @return VectorRecord 列表，如果无结果返回空列表
     */
    @Override
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "MATCH (n:" + label + ") RETURN n ORDER BY n.id SKIP $offset LIMIT $limit";
        return queryToRecords(label, cypher, Values.parameters("offset", offset, "limit", limit));
    }

    // ====================================================================
    // §14.3 运维 API — optimizeImpl / createAliasImpl / switchAliasImpl
    //                    backupImpl / restoreImpl
    //
    // Neo4j 在向量索引领域缺乏 ES/Milvus 那种开箱即用的运维 API：
    //   - 索引构建后 Neo4j 自动维护，无需 optimize；
    //   - alias 语义由 Cypher view / label 抽象承担，无原生别名；
    //   - 备份/恢复需要 neo4j-admin CLI（out-of-process）。
    // 因此 5 个方法统一抛 UnsupportedOperationException，附 provider-specific 提示。
    // ====================================================================

    /**
     * 优化向量索引（Neo4j 不支持）。
     *
     * <p>Neo4j 的 {@code VECTOR INDEX} 在构建完成后由数据库自动维护，无需也无法通过
     * Java driver 主动触发重建；若需调优，建议通过 {@code dbms.index.vector.*} 配置
     * 或 Cypher 重建，参见 Neo4j 官方运维指南。本方法直接抛
     * {@link UnsupportedOperationException} 暴露此能力缺失。</p>
     *
     * @param indexName 索引名称（仅用于诊断信息）
     * @throws UnsupportedOperationException Neo4j 无 vector index optimize API
     */
    @Override
    protected boolean optimizeImpl(String indexName) {
        return throwUnsupportedOps("optimize", indexName, "neo4j");
    }

    /**
     * 为索引创建别名（Neo4j 不支持）。
     *
     * <p>Neo4j 的 {@code VECTOR INDEX} 没有 ES 那种 {@code _aliases} 别名机制；
     * 业务层可通过共享节点 label 或 Cypher view 抽象达到等价效果，
     * 因此本方法抛 {@link UnsupportedOperationException}。</p>
     *
     * @param indexName 目标索引名称
     * @param alias     期望的别名（仅用于诊断信息）
     * @throws UnsupportedOperationException Neo4j 无 vector index alias
     */
    @Override
    protected boolean createAliasImpl(String indexName, String alias) {
        return throwUnsupportedOps("createAlias", indexName, "neo4j");
    }

    /**
     * 切换别名指向新索引（Neo4j 不支持）。
     *
     * <p>因 Neo4j 不存在原生 vector index alias（见 {@link #createAliasImpl}），
     * 切换语义同样不可用。建议业务层自维护 alias → indexName 映射，
     * 需要原子切换时改写查询使用的 label/view。</p>
     *
     * @param oldIndexName 原索引名称
     * @param newIndexName 新索引名称
     * @param alias        待切换的别名
     * @throws UnsupportedOperationException Neo4j 无 vector index alias
     */
    @Override
    protected boolean switchAliasImpl(String oldIndexName, String newIndexName, String alias) {
        return throwUnsupportedOps("switchAlias", newIndexName, "neo4j");
    }

    /**
     * 备份索引到目标路径（Neo4j 不支持 in-process）。
     *
     * <p>Neo4j 备份依赖 {@code neo4j-admin database dump}（out-of-process CLI），
     * Java driver 无法直接触发。VectorService 层不暴露 in-process 备份能力，
     * 建议由运维侧调度 {@code neo4j-admin} 完成整库 dump 后再回灌数据。</p>
     *
     * @param indexName 索引名称（仅用于诊断信息）
     * @param targetPath 备份目标路径
     * @throws UnsupportedOperationException Neo4j backup 需 out-of-process 工具
     */
    @Override
    protected boolean backupImpl(String indexName, String targetPath) {
        return throwUnsupportedOps("backup", indexName, "neo4j");
    }

    /**
     * 从源路径恢复索引（Neo4j 不支持 in-process）。
     *
     * <p>与 {@link #backupImpl} 对称，Neo4j 恢复依赖 {@code neo4j-admin database load}
     * （out-of-process CLI），Java driver 无法直接驱动。</p>
     *
     * @param sourcePath 备份源文件路径
     * @param indexName  索引名称（仅用于诊断信息）
     * @throws UnsupportedOperationException Neo4j restore 需 out-of-process 工具
     */
    @Override
    protected boolean restoreImpl(String sourcePath, String indexName) {
        return throwUnsupportedOps("restore", indexName, "neo4j");
    }

    // ====================================================================
    // 内部工具
    // ====================================================================

    /**
     * 把 Cypher MATCH n RETURN n 的查询结果统一映射为 {@link VectorRecord} 列表。
     *
     * <p>文本字段从节点的 {@code content} 属性读取（缺失时使用单空格占位，
     * 因为 {@link VectorContent.TextContent} 紧凑构造器拒绝空字符串）。</p>
     */
    @SuppressWarnings("unchecked")
    private List<VectorRecord> queryToRecords(String label, String cypher, Value parameters) {
        List<VectorRecord> records = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher, parameters);
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> props = record.get("n").asNode().asMap();
                VectorRecord vr = new VectorRecord()
                        .setId((String) props.get("id"))
                        .setIndexName(label.substring(VECTOR_LABEL_PREFIX.length()));
                String content = props.get("content") instanceof String s ? s : " ";
                vr.setContent(new VectorContent.TextContent(content, "text/plain"));
                if (props.get("metadata") instanceof Map) {
                    vr.setMetadata(new HashMap<>((Map<String, Object>) props.get("metadata")));
                }
                records.add(vr);
            }
        }
        return records;
    }
}
