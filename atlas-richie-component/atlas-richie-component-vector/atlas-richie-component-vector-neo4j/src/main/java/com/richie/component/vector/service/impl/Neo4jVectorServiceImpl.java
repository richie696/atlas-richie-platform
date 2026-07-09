/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
 * @version 1.0.0
 * @since 1.0.0
 * @see VectorService
 */
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "neo4j")
public class Neo4jVectorServiceImpl extends VectorServiceImpl implements VectorService {

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
     * @param vectorStore     Spring AI 向量存储接口，用于文档的添加和删除
     * @param embeddingModel  嵌入模型，用于将文本转换为向量表示
     * @param driver          Neo4j 数据库驱动程序，用于执行 Cypher 查询
     */
    @Autowired
    public Neo4jVectorServiceImpl(VectorStore vectorStore,
                                  @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                  Driver driver) {
        super(vectorStore, embeddingModel);
        this.driver = driver;
    }

    /**
     * 创建向量索引.
     *
     * <p>在 Neo4j 中创建向量索引，用于加速向量相似度搜索操作.
     * 索引名称会转换为特定的节点标签格式（VectorDocument_{indexName}）。</p>
     *
     * <p>该方法创建的是一个唯一性约束（UNIQUE CONSTRAINT），确保每个索引下的
     * 文档 ID 唯一，这比普通索引更适合向量检索场景。</p>
     *
     * @param indexName 索引名称，用于构建节点标签
     * @param config    索引配置信息（当前实现未使用，仅保持接口签名兼容）
     */
    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "CREATE CONSTRAINT IF NOT EXISTS FOR (n:" + label + ") REQUIRE n.id IS UNIQUE";
        try (Session session = driver.session()) {
            session.run(cypher);
        }
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
    public void deleteIndex(String indexName) {
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
    public boolean indexExists(String indexName) {
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
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
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
    public long countDocuments(String indexName) {
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

    /**
     * 分页列出索引中的文档.
     *
     * <p>支持分页查询，返回指定偏移量和限制数量的文档列表.
     * 结果按文档 ID 升序排列，确保分页结果的一致性。</p>
     *
     * <p>每个文档包含 ID、向量和元数据信息.
     * 向量数据从 Neo4j 返回的 List 格式转换为 float[] 数组。</p>
     *
     * @param indexName 索引名称
     * @param offset     起始偏移量（从 0 开始）
     * @param limit      返回的最大文档数量
     * @return 文档列表，如果无结果返回空列表
     */
    @Override
    protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
        String label = VECTOR_LABEL_PREFIX + indexName;
        String cypher = "MATCH (n:" + label + ") RETURN n ORDER BY n.id SKIP $offset LIMIT $limit";
        List<VectorDocument> docs = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Values.parameters("offset", offset, "limit", limit));
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> props = record.get("n").asNode().asMap();
                VectorDocument doc = new VectorDocument();
                doc.setId((String) props.get("id"));
                doc.setVector(new float[0]);
                if (props.get("metadata") instanceof Map) {
                    //noinspection unchecked
                    doc.setMetadata((Map<String, Object>) props.get("metadata"));
                }
                docs.add(doc);
            }
        }
        return docs;
    }

    /**
     * 根据向量进行相似度搜索.
     *
     * <p>使用 Neo4j 的向量索引（db.index.vector.queryNodes）进行高效的
     * 向量相似度搜索，返回与查询向量最相似的文档列表。</p>
     *
     * <p>搜索过程中会验证输入参数的合法性，确保查询向量的有效性
     * 和返回结果数量的合理性。</p>
     *
     * <p><b>注意：</b>当前实现使用硬编码的索引名称 "documents"，
     * 如需支持多索引应扩展此方法。</p>
     *
     * @param queryVector 查询向量，不能为空且长度必须大于 0
     * @param limit       返回结果的最大数量，必须大于 0
     * @return 按相似度降序排列的搜索结果列表
     * @throws IllegalArgumentException 如果查询向量为空或 limit 不合法
     */
    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] queryVector, int limit) {
        if (queryVector == null || queryVector.length == 0) {
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
                    "queryVector", queryVector
                ));

            List<VectorSearchResult> results = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                String id = record.get("id").asString();
                String content = record.get("content").asString();
                double score = record.get("score").asDouble();
                results.add(VectorSearchResult.of(id, content, score, new float[0]));
            }
            return results;
        }
    }

    /**
     * 将 Neo4j 返回的向量对象转换为 float[] 数组.
     *
     * <p>Neo4j 存储的向量数据类型为 List&lt;Number&gt;，而业务层使用 float[]
     * 表示向量。此方法负责类型转换并确保每个元素都能正确转换为 float。</p>
     *
     * <p>转换过程中的异常处理策略：
     * <ul>
     *   <li>如果输入值不是 List 类型，返回空数组</li>
     *   <li>如果 List 中的元素无法转换为 Number，返回空数组</li>
     * </ul>
     * </p>
     *
     * @param value Neo4j 返回的向量对象（期望为 List&lt;Number&gt;）
     * @return float[] 数组，转换失败时返回空数组而非 null
     */
    private float[] convertToFloatArray(Object value) {
        if (!(value instanceof List<?> list)) {
            return new float[0];
        }
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Number num) {
                result[i] = num.floatValue();
            } else {
                return new float[0];
            }
        }
        return result;
    }

}