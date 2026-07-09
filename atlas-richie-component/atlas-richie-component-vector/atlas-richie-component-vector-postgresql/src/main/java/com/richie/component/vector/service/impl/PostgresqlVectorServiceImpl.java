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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL向量数据库服务实现类，基于pgvector扩展提供向量存储和检索能力。
 *
 * <p>该实现类封装了PostgreSQL数据库中向量表的管理操作，包括索引配置查询、
 * 文档计数、分页查询和向量相似度搜索等功能。通过JDBC直连PostgreSQL执行
 * 原生SQL语句，实现对pgvector扩展存储的向量数据的操作。
 *
 * <p><b>设计说明：</b>该实现类专注于pgvector的原生SQL操作能力，提供比
 * Spring AI VectorStore更精细的控制，用于需要直接操作底层表结构的场景。
 * 对于常规的向量存取操作，建议使用父类VectorServiceImpl中基于VectorStore的实现。
 *
 * @author jinyang.wang
 * @version 1.0.0
 * @since 1.0.0
 * @see VectorServiceImpl
 * @see <a href="https://github.com/pgvector/pgvector">pgvector扩展</a>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "postgresql")
public class PostgresqlVectorServiceImpl extends VectorServiceImpl implements VectorService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造PostgreSQL向量服务实例。
     *
     * <p>通过构造函数注入所需的依赖组件，包括Spring AI的VectorStore接口
     * 用于文档存取、EmbeddingModel用于生成文本嵌入向量，以及JdbcTemplate
     * 用于执行原生SQL操作。
     *
     * @param vectorStore Spring AI向量存储接口，用于文档的添加和搜索
     * @param embeddingModel 嵌入模型接口，用于将文本转换为向量表示
     * @param jdbcTemplate JDBC模板，用于执行PostgreSQL原生SQL语句
     */
    @Autowired
    public PostgresqlVectorServiceImpl(VectorStore vectorStore,
                                       @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                       JdbcTemplate jdbcTemplate) {
        super(vectorStore, embeddingModel);
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 创建向量索引。
     *
     * <p><b>当前实现说明：</b>PostgreSQL的pgvector扩展索引创建较为复杂，
     * 需要考虑索引类型(HNSW/IVFFlat)、表结构、列类型等多方面因素，
     * 因此暂不支持通过代码自动创建索引。建议通过数据库迁移脚本或
     * DBA手动执行CREATE INDEX语句来创建索引。
     *
     * @param indexName 索引名称
     * @param config 索引配置信息（当前未使用）
     * @throws UnsupportedOperationException 始终抛出，表示不支持此操作
     */
    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        // PostgreSQL pgvector索引创建涉及多种索引类型选择、表结构设计等复杂因素
        // 建议通过数据库迁移脚本手动创建索引，而不是通过代码自动创建
        log.info("不支持通过代码创建索引");
    }

    /**
     * 删除向量索引。
     *
     * <p><b>当前实现说明：</b>删除PostgreSQL中的向量索引涉及DROP INDEX语句，
     * 但由于索引可能与其他业务表存在关联，删除操作存在一定的风险，
     * 因此暂不支持通过代码删除索引。建议通过数据库迁移脚本或DBA
     * 手动执行DROP INDEX语句来删除索引。
     *
     * @param indexName 索引名称
     * @throws UnsupportedOperationException 始终抛出，表示不支持此操作
     */
    @Override
    public void deleteIndex(String indexName) {
        // 删除索引是危险操作，可能影响依赖该索引的业务
        // 建议通过数据库迁移脚本手动删除，而不是通过代码
        log.info("不支持通过代码删除索引");
    }

    /**
     * 检查向量索引是否存在。
     *
     * <p>通过查询PostgreSQL的information_schema.tables系统表来判断
     * 指定名称的向量表是否存在。向量表命名规范为"vector_{indexName}"。
     *
     * <p><b>表命名约定：</b>所有向量表的前缀为"vector_"，例如
     * indexName为"documents"时，对应的表名为"vector_documents"。
     *
     * @param indexName 索引名称（即表名前缀）
     * @return 如果表存在返回true，否则返回false
     */
    @Override
    public boolean indexExists(String indexName) {
        // PostgreSQL的information_schema.tables是标准系统表，存储所有表信息
        // 通过查询该表可以判断向量表是否存在，这是一种标准且可靠的方式
        String table = "vector_%s".formatted(indexName);
        String sql = "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, table);
        return exists != null && exists;
    }

    /**
     * 获取向量索引的配置信息。
     *
     * <p>查询PostgreSQL系统表pg_attribute获取向量列的维度信息，
     * 并构建IndexConfig对象返回。PostgreSQL中pgvector存储的向量维度
     * 通过attndims字段获取。
     *
     * <p><b>距离度量说明：</b>当前实现固定返回"l2"（欧氏距离）作为
     * 默认距离度量方式，因为pgvector默认使用l2距离，且该信息未存储
     * 在系统表中可查询。
     *
     * @param indexName 索引名称
     * @return 索引配置对象，包含名称、维度等基本信息
     * @throws UnsupportedOperationException 如果索引不存在则抛出异常
     * @throws IllegalArgumentException 如果无法获取维度信息
     */
    @Override
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
        // 先检查索引是否存在，避免查询不存在的表导致异常
        if (!indexExists(indexName)) {
            throw new UnsupportedOperationException("索引不存在: %s".formatted(indexName));
        }
        String table = "vector_%s".formatted(indexName);
        // pg_attribute系统表存储表中所有列的元数据，attndims字段表示向量维度
        // attrelid使用::regclass转换以便进行表名比较
        String sql = "SELECT attndims FROM pg_attribute WHERE attrelid = ?::regclass AND attname = 'vector'";
        Integer dim = jdbcTemplate.queryForObject(sql, Integer.class, table);
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setName(indexName);
        // attndims为null表示该列不是向量类型，或表结构不符合预期
        config.setDimension(dim != null ? dim : 0);
        // pgvector默认使用L2距离（欧氏距离），这是最常用的距离度量方式
        config.setMetric("l2");
        return config;
    }

    /**
     * 统计向量索引中的文档数量。
     *
     * <p>执行SELECT COUNT(*)语句统计指定向量表中的总记录数。
     * 使用Try-Catch捕获异常，因为表可能不存在或查询可能失败。
     *
     * <p><b>性能说明：</b>COUNT(*)在PostgreSQL中会扫描全表，
     * 对于大规模数据可能较慢。如果只需要判断是否为空，建议使用
     * EXISTS语句替代。
     *
     * @param indexName 索引名称
     * @return 文档数量，如果查询失败则返回0
     */
    @Override
    public long countDocuments(String indexName) {
        String table = "vector_%s".formatted(indexName);
        String sql = "SELECT COUNT(*) FROM %s".formatted(table);
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            // COUNT(*)可能返回null（如表为空但查询成功），需要处理
            count = count == null ? 0L : count;
            return count;
        } catch (Exception e) {
            // 表不存在或其他查询错误，返回0而不是抛出异常
            // 调用方可根据需要判断是返回0还是抛出异常
            return 0;
        }
    }

    /**
     * 分页查询向量文档列表。
     *
     * <p>从指定向量表中查询文档的ID、向量和元数据信息，
     * 支持分页查询。使用PostgreSQL的OFFSET/LIMIT语法实现分页。
     *
     * <p><b>查询说明：</b>查询结果按ID排序，确保分页结果的稳定性。
     * 向量数据从List类型转换为float[]数组，因为pgvector返回的
     * 向量是数组形式。
     *
     * @param indexName 索引名称
     * @param offset 起始偏移量，从0开始
     * @param limit 最大返回数量
     * @return 文档列表，按ID升序排列
     */
    @SuppressWarnings("unchecked")
    @Override
    protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
        String table = "vector_%s".formatted(indexName);
        // 使用String.format构建SQL，注意OFFSET和LIMIT使用占位符防止SQL注入
        // ORDER BY id确保分页结果稳定，避免因数据变更导致的分页不一致
        String sql = String.format("SELECT id, vector, metadata FROM %s ORDER BY id OFFSET ? LIMIT ?", table);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, offset, limit);
        List<VectorDocument> docs = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            VectorDocument doc = new VectorDocument();
            doc.setId((String) row.get("id"));
            // pgvector返回的向量是List<Float>或List<Double>类型，需要手动转换为float[]
            Object vectorObj = row.get("vector");
            if (vectorObj instanceof List<?> vectorList) {
                float[] arr = new float[vectorList.size()];
                for (int i = 0; i < vectorList.size(); i++) {
                    arr[i] = ((Number) vectorList.get(i)).floatValue();
                }
                doc.setVector(arr);
            }
            // metadata在PostgreSQL中存储为JSONB，返回时是Map类型
            doc.setMetadata((row.get("metadata") instanceof Map) ? (Map<String, Object>) row.get("metadata") : null);
            docs.add(doc);
        }
        return docs;
    }

    /**
     * 通过向量相似度搜索文档。
     *
     * <p>使用PostgreSQL的l2距离运算符(&lt;-&gt;)计算查询向量与存储向量
     * 之间的欧氏距离，并按距离升序排列返回最近的文档。
     *
     * <p><b>距离计算说明：</b>使用&lt;-&gt;运算符计算L2距离（欧氏距离），
     * 这是pgvector最常用的距离度量方式。距离越小表示相似度越高。
     *
     * <p><b>性能优化：</b>对于大规模数据，建议在vector列上创建
     * HNSW或IVFFlat索引以提升搜索性能。索引创建后可显著加速
     * 近似最近邻搜索。
     *
     * <p><b>得分转换说明：</b>将L2距离转换为相似度得分，使用公式
     * score = 1.0 / (1.0 + distance)进行转换。距离为0时得分接近1.0，
     * 距离越大得分越接近0。这是一种常见的距离转相似度映射方式。
     *
     * @param queryVector 查询向量，不应为空
     * @param limit 最大返回结果数量
     * @return 搜索结果列表，按相似度降序排列，每个结果包含ID、内容、得分和向量
     */
    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] queryVector, int limit) {
        String table = "vector_%s".formatted(indexName);
        // pgvector向量字面量使用方括号格式：[1.0, 2.0, 3.0]
        StringBuilder vectorSb = new StringBuilder("[");
        for (int i = 0; i < queryVector.length; i++) {
            vectorSb.append(queryVector[i]);
            if (i < queryVector.length - 1) {
                vectorSb.append(",");
            }
        }
        vectorSb.append("]");
        String vectorStr = vectorSb.toString();
        // 使用<->运算符计算L2距离，ORDER BY距离升序即按相似度降序
        // 使用参数化查询(LIMIT ?)防止SQL注入
        String sql = String.format(
                "SELECT id, content, metadata, vector, vector <-> '%s'::vector as distance FROM %s ORDER BY vector <-> '%s'::vector LIMIT ?",
                vectorStr, table, vectorStr);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, limit);
        List<VectorSearchResult> results = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("id");
            String content = (String) row.get("content");
            // pgvector返回的向量可能是List类型，需要转换为float[]
            // 这是因为JDBC返回的数组类型可能被解析为List
            float[] vector = null;
            Object vectorObj = row.get("vector");
            if (vectorObj instanceof List<?> vectorList) {
                vector = new float[vectorList.size()];
                for (int i = 0; i < vectorList.size(); i++) {
                    // 将Number类型（包括Double、Float等）转换为float
                    vector[i] = ((Number) vectorList.get(i)).floatValue();
                }
            }
            // 将L2距离转换为相似度得分：距离越小，得分越高
            // 使用1.0/(1.0+distance)公式，距离为0时得分接近1.0
            Double distance = (Double) row.get("distance");
            Double score = distance != null ? 1.0 / (1.0 + distance) : null;
            results.add(VectorSearchResult.of(id, content, score, vector));
        }
        return results;
    }

}
