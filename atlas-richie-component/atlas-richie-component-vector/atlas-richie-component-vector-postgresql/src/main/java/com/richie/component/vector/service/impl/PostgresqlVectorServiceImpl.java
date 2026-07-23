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
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * PostgreSQL向量数据库服务实现类，基于pgvector扩展提供向量存储和检索能力。
 *
 * <p>该实现类封装了PostgreSQL数据库中向量表的管理操作，包括索引配置查询、
 * 文档计数、分页查询和向量相似度搜索等功能。通过JDBC直连PostgreSQL执行
 * 原生SQL语句，实现对pgvector扩展存储的向量数据的操作。
 *
 * <p><b>设计说明：</b>该实现类专注于pgvector的原生SQL操作能力，提供比
 * Spring AI VectorStore更精细的控制，用于需要直接操作底层表结构的场景。
 * 对于常规的向量存取操作，建议使用父类AbstractVectorService中基于VectorStore的实现。
 *
 * @author jinyang.wang
 * @version 1.0.0
 * @since 1.0.0
 * @see AbstractVectorService
 * @see <a href="https://github.com/pgvector/pgvector">pgvector扩展</a>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "postgresql")
public class PostgresqlVectorServiceImpl extends AbstractVectorService implements VectorService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造PostgreSQL向量服务实例。
     *
     * <p>通过构造函数注入所需的依赖组件，包括Spring AI的VectorStore接口
     * 用于文档存取、EmbeddingModel用于生成文本嵌入向量，以及JdbcTemplate
     * 用于执行原生SQL操作。
     *
     * @param rerankService 重排序服务（可选）
     * @param vectorStore Spring AI向量存储接口，用于文档的添加和搜索
     * @param embeddingModel 嵌入模型接口，用于将文本转换为向量表示
     * @param jdbcTemplate JDBC模板，用于执行PostgreSQL原生SQL语句
     */
    @Autowired
    public PostgresqlVectorServiceImpl(@Autowired(required = false) RerankService rerankService,
                                       VectorStore vectorStore,
                                       @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                       JdbcTemplate jdbcTemplate) {
        super(rerankService, vectorStore, embeddingModel);
        this.jdbcTemplate = jdbcTemplate;
    }

    /** pgvector 默认向量维度（OpenAI text-embedding-3-small 等） */
    private static final int DEFAULT_DIMENSION = 1536;

    /**
     * 创建向量索引（Phase B 真实实现 — pgvector HNSW）。
     *
     * <p>使用 pgvector 原生 {@code vector(N)} 列类型建表，并基于
     * {@code hnsw} 图索引创建 ANN 索引。metric 通过 ops 类映射：
     * <ul>
     *   <li>{@code cosine} → {@code vector_cosine_ops}</li>
     *   <li>{@code l2} / {@code euclidean} → {@code vector_l2_ops}</li>
     *   <li>{@code ip} / {@code dot} → {@code vector_ip_ops}</li>
     *   <li>其它（含 null） → {@code vector_cosine_ops}</li>
     * </ul>
     *
     * <p>所有 DDL 使用 {@code IF NOT EXISTS}，可重复执行；HNSW 索引若已存在
     * 仅记录警告，不抛出异常。
     *
     * @param indexName 索引名称（表名以 {@code vector_} 为前缀）
     * @param config    索引配置（dimension / metric）
     */
    @Override
    protected void createIndexImpl(String indexName, VectorProperties.IndexConfig config) {
        String table = "vector_%s".formatted(indexName);
        int dimension = config != null && config.getDimension() != null ? config.getDimension() : DEFAULT_DIMENSION;
        String metric = config != null && config.getMetric() != null ? config.getMetric() : "cosine";
        String ops = mapMetricToOps(metric);

        String createTable = String.format(
                "CREATE TABLE IF NOT EXISTS %s ("
                        + "id TEXT PRIMARY KEY, "
                        + "content TEXT, "
                        + "metadata JSONB, "
                        + "vector vector(%d)"
                        + ")",
                table, dimension);
        String createIndex = String.format(
                "CREATE INDEX IF NOT EXISTS %s_vector_idx ON %s USING hnsw (vector %s)",
                table, table, ops);

        try {
            jdbcTemplate.execute(createTable);
        } catch (Exception e) {
            log.error("pgvector CREATE TABLE 失败: indexName={}, table={}, error={}",
                    indexName, table, e.getMessage());
            throw e;
        }

        try {
            jdbcTemplate.execute(createIndex);
            log.info("pgvector HNSW 索引创建完成: indexName={}, table={}, dim={}, metric={}",
                    indexName, table, dimension, metric);
        } catch (Exception e) {
            // HNSW 索引已存在 / 已存在同名索引等场景不阻断调用方
            log.warn("pgvector CREATE INDEX 跳过（可能已存在）: indexName={}, table={}, cause={}",
                    indexName, table, e.getMessage());
        }
    }

    /**
     * 将 metric 字符串映射为 pgvector ops 类。
     */
    private static String mapMetricToOps(String metric) {
        if (metric == null) {
            return "vector_cosine_ops";
        }
        return switch (metric.toLowerCase()) {
            case "l2", "euclidean" -> "vector_l2_ops";
            case "ip", "dot" -> "vector_ip_ops";
            default -> "vector_cosine_ops";
        };
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
     */
    @Override
    protected void deleteIndexImpl(String indexName) {
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
    protected boolean indexExistsImpl(String indexName) {
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
     */
    @Override
    protected VectorProperties.IndexConfig getIndexConfigImpl(String indexName) {
        // 先检查索引是否存在，避免查询不存在的表导致异常
        if (!indexExistsImpl(indexName)) {
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
        // 从 pg_indexes.indexdef 反查 HNSW ops 类，回填到业务 metric 字段
        config.setMetric(readMetricFromPgIndex(table));
        return config;
    }

    /**
     * 从 pg_indexes 读取 HNSW 索引的 ops 类，反向映射回业务 metric 字符串。
     * 查询失败 / 不存在 HNSW 索引时回退到 {@code l2}，保持向后兼容。
     */
    private String readMetricFromPgIndex(String table) {
        try {
            String indexdef = jdbcTemplate.queryForObject(
                    "SELECT indexdef FROM pg_indexes WHERE tablename = ? AND indexdef LIKE '%USING hnsw%' LIMIT 1",
                    String.class, table);
            if (indexdef == null) {
                return "l2";
            }
            if (indexdef.contains("vector_cosine_ops")) {
                return "cosine";
            }
            if (indexdef.contains("vector_l2_ops")) {
                return "l2";
            }
            if (indexdef.contains("vector_ip_ops")) {
                return "ip";
            }
            return "l2";
        } catch (Exception e) {
            log.debug("pgvector 索引 ops 类反查失败: table={}, cause={}", table, e.getMessage());
            return "l2";
        }
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
    protected long countDocumentsImpl(String indexName) {
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

    // ==================== AbstractVectorService 抽象方法实现 ====================

    @Override
    protected long truncateIndexImpl(String indexName) {
        long previousCount = countDocumentsImpl(indexName);
        String table = "vector_%s".formatted(indexName);
        String sql = "DELETE FROM %s".formatted(table);
        try {
            return jdbcTemplate.update(sql);
        } catch (Exception e) {
            throw new RuntimeException("truncateIndex 失败: " + indexName, e);
        }
    }

    // ====================================================================
    // §14.4 运维 / 别名 / 备份 — pgvector 实现（optimize / createAlias / switchAlias / backup / restore）
    // ====================================================================

    /**
     * 优化向量索引（§14.4 optimize — pgvector VACUUM ANALYZE）。
     *
     * <p>pgvector HNSW 索引不需要周期性 rebuild，但执行 {@code VACUUM ANALYZE}
     * 可回收死元组空间并刷新查询规划器统计信息，对大表持续写入场景下的查询
     * 性能有正向作用。HNSW 索引本身通常无需 {@code REINDEX}（pgvector 在写入
     * 阶段即增量维护图结构），因此本方法仅执行 VACUUM ANALYZE。
     *
     * <p>对不存在的索引名执行 VACUUM 会抛错（如 {@code relation does not exist}），
     * 此处捕获后返回 {@code false}，由调用方决定是否重试或忽略。
     *
     * @param indexName 索引名称（对应表名 {@code vector_<indexName>}）
     * @return 成功返回 {@code true}，执行异常返回 {@code false}
     */
    @Override
    protected boolean optimizeImpl(String indexName) {
        String table = "vector_%s".formatted(indexName);
        String sql = "VACUUM ANALYZE \"%s\"".formatted(table);
        try {
            // VACUUM ANALYZE 回收空间 + 更新统计；pgvector HNSW 索引无需 REINDEX（写入即增量维护图）
            jdbcTemplate.execute(sql);
            log.info("pgvector VACUUM ANALYZE 完成: indexName={}, table={}", indexName, table);
            return true;
        } catch (Exception e) {
            log.warn("pgvector optimize 失败: indexName={}, table={}, cause={}",
                    indexName, table, e.getMessage());
            return false;
        }
    }

    /**
     * 创建索引别名（§14.4 createAlias — pgvector 不支持）。
     *
     * <p>pgvector 没有 Elasticsearch-style alias 概念。若需以别名访问向量表，
     * 可通过 {@code CREATE VIEW vector_<alias> AS SELECT * FROM vector_<indexName>}
     * 或在业务侧维护 indexName → tableName 映射实现。VectorService 层不直接
     * 支持此操作。
     *
     * @param indexName 索引名称
     * @param alias     期望创建的别名
     * @throws UnsupportedOperationException pgvector 不支持原生 alias
     */
    @Override
    protected boolean createAliasImpl(String indexName, String alias) {
        return throwUnsupportedOps("createAlias", indexName, "postgresql");
    }

    /**
     * 切换索引别名（§14.4 switchAlias — pgvector 不支持）。
     *
     * <p>pgvector 不支持原子 alias 切换。若需实现零停机索引切换，推荐
     * "双写 + 视图切换"模式：新建向量表 → 业务双写 → {@code CREATE OR REPLACE VIEW}
     * 原子指向新表 → 业务切读 → 旧表下线。VectorService 层不直接支持此操作。
     *
     * @param oldIndexName 旧索引名称
     * @param newIndexName 新索引名称
     * @param alias        别名
     * @throws UnsupportedOperationException pgvector 不支持原生 alias 切换
     */
    @Override
    protected boolean switchAliasImpl(String oldIndexName, String newIndexName, String alias) {
        return throwUnsupportedOps("switchAlias", newIndexName, "postgresql");
    }

    /**
     * 备份向量索引（§14.4 backup — pgvector 通过 pg_dump）。
     *
     * <p>PostgreSQL 备份需通过 {@code pg_dump}（out-of-process 工具）执行，
     * VectorService 层仅持有 JDBC 连接，无法直接 fork 外部进程。建议
     * 在运维侧通过 cron / k8s CronJob 调度 {@code pg_dump} 工具生成
     * {@code .sql} 或 {@code .dump} 文件落盘。VectorService 层不直接支持。
     *
     * @param indexName 索引名称（对应表名 {@code vector_<indexName>}）
     * @param targetPath 备份文件目标路径
     * @throws UnsupportedOperationException PostgreSQL 备份需走 pg_dump 工具
     */
    @Override
    protected boolean backupImpl(String indexName, String targetPath) {
        return throwUnsupportedOps("backup", indexName, "postgresql");
    }

    /**
     * 恢复向量索引（§14.4 restore — pgvector 通过 pg_restore / psql）。
     *
     * <p>PostgreSQL 恢复需通过 {@code pg_restore}（custom format）或
     * {@code psql}（plain SQL）执行，VectorService 层仅持有 JDBC 连接，
     * 无法直接 fork 外部进程。建议在运维侧通过 {@code pg_restore} / {@code psql}
     * 加载备份文件。VectorService 层不直接支持。
     *
     * @param sourcePath 备份文件源路径
     * @param indexName  目标索引名称
     * @throws UnsupportedOperationException PostgreSQL 恢复需走 pg_restore 工具
     */
    @Override
    protected boolean restoreImpl(String sourcePath, String indexName) {
        return throwUnsupportedOps("restore", indexName, "postgresql");
    }

    // ====================================================================
    // §14.2 索引管理 — pgvector 实现（listIndexes / describeIndex / updateIndexConfig / getIndexStats）
    // ====================================================================

    /**
     * 列出当前数据库中所有由本服务管理的向量索引。
     *
     * <p>通过查询 {@code information_schema.tables} 中以 {@code vector_} 为前缀的
     * 表名反推索引名（剥离前缀后即为业务侧的 {@code indexName}）。每个返回的
     * {@link IndexInfo} 仅包含基础字段（name / modality / indexType / status），
     * 维度 / 距离度量 / 文档数等详细信息请通过 {@link #describeIndexImpl(String)} 获取。
     *
     * @return 索引信息列表；若当前 schema 不存在任何 {@code vector_*} 表则返回空列表
     */
    @Override
    protected List<IndexInfo> listIndexesImpl() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_name LIKE 'vector_%'";
        List<String> tables;
        try {
            tables = jdbcTemplate.queryForList(sql, String.class);
        } catch (Exception e) {
            log.warn("pgvector 索引列表查询失败: cause={}", e.getMessage());
            return List.of();
        }
        List<IndexInfo> result = new ArrayList<>(tables.size());
        String prefix = "vector_";
        for (String table : tables) {
            String indexName = table.startsWith(prefix) ? table.substring(prefix.length()) : table;
            result.add(new IndexInfo(
                    indexName,
                    Modality.TEXT,
                    null,
                    null,
                    "hnsw",
                    IndexStatus.READY,
                    null,
                    null,
                    null,
                    Map.of()));
        }
        return result;
    }

    /**
     * 获取指定索引的完整描述信息（§14.2 describeIndex）。
     *
     * <p>组合 {@link #getIndexConfigImpl(String)} 与 {@link #countDocumentsImpl(String)}
     * 的结果构造 {@link IndexInfo}，包含 name / dimension / metric / documentCount
     * 以及模态（{@link Modality#TEXT}）、索引类型（{@code hnsw}）、状态（{@link IndexStatus#READY}）
     * 等元数据。updatedAt 取当前时间，createdAt 由 pgvector 不直接存储因此留空。
     *
     * @param indexName 索引名称
     * @return 索引完整描述信息
     * @throws UnsupportedOperationException 当索引不存在时（由 {@link #getIndexConfigImpl(String)} 抛出）
     */
    @Override
    protected IndexInfo describeIndexImpl(String indexName) {
        VectorProperties.IndexConfig config = getIndexConfigImpl(indexName);
        long count = countDocumentsImpl(indexName);
        return new IndexInfo(
                indexName,
                Modality.TEXT,
                config.getDimension(),
                config.getMetric(),
                "hnsw",
                IndexStatus.READY,
                count,
                null,
                Instant.now(),
                Map.of());
    }

    /**
     * 更新指定索引的 HNSW 配置（§14.2 updateIndexConfig — 通过 DROP/CREATE 重建索引）。
     *
     * <p>pgvector 不支持原地修改 HNSW 索引的 ops 类，因此采用 {@code DROP INDEX IF EXISTS}
     * + {@code CREATE INDEX IF NOT EXISTS} 的方式重建。metric 通过 {@link #mapMetricToOps(String)}
     * 映射为对应 ops 类（cosine / l2 / ip）。dimension 仅在新列未创建时由
     * {@link #createIndexImpl(String, VectorProperties.IndexConfig)} 控制；本方法
     * 不修改 vector 列定义，若需变更维度请先 ALTER TABLE 重建列后再调用本方法。
     *
     * @param indexName 索引名称
     * @param config    新的索引配置（dimension / metric；null 字段回退到 cosine + DEFAULT_DIMENSION）
     * @return true 表示重建成功，false 表示执行过程出现异常
     */
    @Override
    protected boolean updateIndexConfigImpl(String indexName, VectorProperties.IndexConfig config) {
        if (!indexExistsImpl(indexName)) {
            throw new UnsupportedOperationException("索引不存在: %s".formatted(indexName));
        }
        String table = "vector_%s".formatted(indexName);
        int dimension = config != null && config.getDimension() != null ? config.getDimension() : DEFAULT_DIMENSION;
        String metric = config != null && config.getMetric() != null ? config.getMetric() : "cosine";
        String ops = mapMetricToOps(metric);

        String pgIndex = "%s_vector_idx".formatted(table);
        String dropSql = "DROP INDEX IF EXISTS %s".formatted(pgIndex);
        String createSql = "CREATE INDEX IF NOT EXISTS %s ON %s USING hnsw (vector %s)"
                .formatted(pgIndex, table, ops);

        try {
            jdbcTemplate.execute(dropSql);
            jdbcTemplate.execute(createSql);
            log.info("pgvector HNSW 索引配置重建完成: indexName={}, table={}, dim={}, metric={}",
                    indexName, table, dimension, metric);
            return true;
        } catch (Exception e) {
            log.error("pgvector HNSW 索引配置重建失败: indexName={}, table={}, cause={}",
                    indexName, table, e.getMessage());
            return false;
        }
    }

    /**
     * 获取指定索引的运行统计（§14.2 getIndexStats — name + dimension + metric + count）。
     *
     * <p>与 {@link #describeIndexImpl(String)} 不同，本方法聚焦于可观测运行数据：
     * 仅返回 name / dimension / metric / documentCount 四个核心字段，不设置模态、
     * 索引类型、状态、时间戳等元信息（保持轻量）。
     *
     * @param indexName 索引名称
     * @return 索引运行统计
     * @throws UnsupportedOperationException 当索引不存在时（由 {@link #getIndexConfigImpl(String)} 抛出）
     */
    @Override
    protected IndexInfo getIndexStatsImpl(String indexName) {
        VectorProperties.IndexConfig config = getIndexConfigImpl(indexName);
        long count = countDocumentsImpl(indexName);
        return new IndexInfo(
                indexName,
                null,
                config.getDimension(),
                config.getMetric(),
                null,
                null,
                count,
                null,
                null,
                Map.of());
    }

    @Override
    protected List<Document> similaritySearchByVector(String indexName, float[] vector, int limit, double minScore) {
        String table = "vector_%s".formatted(indexName);
        String vectorStr = toVectorLiteral(vector);
        // 使用<->运算符计算L2距离，ORDER BY距离升序即按相似度降序
        String sql = String.format(
                "SELECT id, content, metadata, vector <-> '%s'::vector as distance FROM %s ORDER BY vector <-> '%s'::vector LIMIT ?",
                vectorStr, table, vectorStr);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, limit);
        List<Document> docs = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String id = (String) row.get("id");
            String content = (String) row.get("content");
            Double distance = (Double) row.get("distance");
            Double score = distance != null ? 1.0 / (1.0 + distance) : null;
            // 低于阈值的结果直接跳过
            if (score != null && score < minScore) {
                continue;
            }
            Map<String, Object> meta = (row.get("metadata") instanceof Map)
                    ? new HashMap<>((Map<String, Object>) row.get("metadata"))
                    : new HashMap<>();
            // 同步保存原始向量，便于后续 chain 调用
            Object vectorObj = row.get("vector");
            if (vectorObj instanceof List<?> vectorList) {
                float[] arr = new float[vectorList.size()];
                for (int i = 0; i < vectorList.size(); i++) {
                    arr[i] = ((Number) vectorList.get(i)).floatValue();
                }
                meta.put("embedding", arr);
            }
            Document doc = Document.builder()
                    .id(id != null ? id : "")
                    .text(content != null ? content : "")
                    .metadata(meta)
                    .score(score != null ? score : 0.0)
                    .build();
            docs.add(doc);
        }
        return docs;
    }

    /**
     * 批量写入已嵌入的文档。
     *
     * <p>使用 {@code INSERT ... ON CONFLICT (id) DO UPDATE} 实现 upsert：
     * 存在则覆盖 content / metadata / vector，不存在则插入。
     * 文档中的 embedding 必须放在 {@code metadata["embedding"]} 中，
     * 由 AbstractVectorService.toAiDocument 负责写入。
     *
     * @param indexName 索引名称
     * @param docs 已嵌入完成的 Spring AI Document 列表
     */
    @Override
    protected void addEmbeddings(String indexName, List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        String table = "vector_%s".formatted(indexName);
        StringBuilder placeholders = new StringBuilder();
        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            if (i > 0) {
                placeholders.append(",");
            }
            placeholders.append("(?, ?, ?::jsonb, ?::vector)");
            Document d = docs.get(i);
            float[] embedding = (float[]) d.getMetadata().get("embedding");
            batchArgs.add(new Object[]{
                    d.getId(),
                    d.getText() != null ? d.getText() : "",
                    toJsonString(d.getMetadata()),
                    toVectorLiteral(embedding)
            });
        }
        String sql = "INSERT INTO " + table + " (id, content, metadata, vector) VALUES " + placeholders
                + " ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, metadata = EXCLUDED.metadata, vector = EXCLUDED.vector";
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    /**
     * 按 ID 列表批量删除记录。
     *
     * @param indexName 索引名称
     * @param ids 待删除的记录 ID 列表（空或 null 则直接返回）
     */
    @Override
    protected void deleteByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        String table = "vector_%s".formatted(indexName);
        String inClause = ids.stream().map(s -> "?").collect(Collectors.joining(","));
        String sql = String.format("DELETE FROM %s WHERE id IN (%s)", table, inClause);
        jdbcTemplate.update(sql, ids.toArray());
    }

    /**
     * 按 ID 列表批量读取记录。
     *
     * <p>保持输入顺序，跳过数据库中不存在的 ID。content 缺失或为空白时，
     * 返回的 VectorRecord 不设置 {@code content} 字段（避免
     * {@link VectorContent.TextContent} 紧凑构造器抛错）。
     *
     * @param indexName 索引名称
     * @param ids 待查询的 ID 列表
     * @return 命中记录的 VectorRecord 列表
     */
    @Override
    protected List<VectorRecord> getByIds(String indexName, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String table = "vector_%s".formatted(indexName);
        String inClause = ids.stream().map(s -> "?").collect(Collectors.joining(","));
        String sql = String.format("SELECT id, content, metadata FROM %s WHERE id IN (%s)", table, inClause);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ids.toArray());
        List<VectorRecord> records = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            records.add(toVectorRecord(row, indexName));
        }
        return records;
    }

    /**
     * 分页查询向量文档列表。
     *
     * <p>从指定向量表中查询文档的 ID、content 和 metadata，
     * 支持分页查询。使用PostgreSQL的OFFSET/LIMIT语法实现分页。
     *
     * @param indexName 索引名称
     * @param offset 起始偏移量，从0开始
     * @param limit 最大返回数量
     * @return 记录列表，按ID升序排列
     */
    @Override
    protected List<VectorRecord> listDocumentsImpl(String indexName, int offset, int limit) {
        String table = "vector_%s".formatted(indexName);
        // 使用String.format构建SQL，注意OFFSET和LIMIT使用占位符防止SQL注入
        // ORDER BY id确保分页结果稳定，避免因数据变更导致的分页不一致
        String sql = String.format("SELECT id, content, metadata FROM %s ORDER BY id OFFSET ? LIMIT ?", table);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, offset, limit);
        List<VectorRecord> docs = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            docs.add(toVectorRecord(row, indexName));
        }
        return docs;
    }

    // ==================== 内部工具 ====================

    /**
     * 将 JDBC 行映射为 VectorRecord。content 缺失/空白时不设置 content，
     * 避免触发 TextContent 紧凑构造器的非空校验。
     */
    private VectorRecord toVectorRecord(Map<String, Object> row, String indexName) {
        VectorRecord r = new VectorRecord();
        r.setId((String) row.get("id"));
        r.setIndexName(indexName);
        String content = (String) row.get("content");
        if (content != null && !content.isBlank()) {
            r.setContent(new VectorContent.TextContent(content, "text/plain"));
        }
        Object metadata = row.get("metadata");
        if (metadata instanceof Map) {
            r.setMetadata((Map<String, Object>) metadata);
        }
        return r;
    }

    /**
     * 将 float[] 转换为 pgvector 字面量格式 {@code [v1,v2,v3]}。
     * null 输入返回 {@code []}。
     */
    private String toVectorLiteral(float[] vector) {
        if (vector == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        return sb.append("]").toString();
    }

    /**
     * 将 Map 序列化为 JSON 字符串，写入 jsonb 列。Map 为 null 时返回 {@code {}}。
     */
    private String toJsonString(Map<String, Object> map) {
        if (map == null) {
            return "{}";
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}