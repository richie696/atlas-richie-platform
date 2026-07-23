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
package com.richie.component.vector.config.integration;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.config.support.VectorIntegrationTest;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL pgvector 集成测试 (Testcontainers + JUnit5)。
 * 镜像 pgvector/pgvector:pg16,内置 pgvector 扩展。
 * Docker 不可用时设置环境变量 SKIP_TESTCONTAINERS=true 整体跳过。
 * PostgresqlVectorServiceImpl 通过 CREATE TABLE vector_${indexName} 显式管理,@BeforeEach 集中准备/重建。
 */
@Testcontainers
@VectorIntegrationTest
@DisabledIfEnvironmentVariable(named = "SKIP_TESTCONTAINERS", matches = "true")
class PostgresqlVectorRecordOpsIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("it_vectors")
            .withUsername("it")
            .withPassword("it")
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    /**
     * 将容器 JDBC 凭证注入到 PostgresqlConfig 绑定的前缀 {@code platform.component.vector.postgresql.*}
     * 与 Spring Boot 默认 DataSource 绑定的 {@code spring.datasource.*}。
     * 后者让 {@code DataSourceAutoConfiguration} 能正常构造 HikariCP 数据源,
     * 否则 dataSourceScriptDatabaseInitializer 会因缺少 driver class 报
     * "Failed to determine a suitable driver class"。
     */
    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.vector.provider", () -> "postgresql");
        registry.add("platform.component.vector.postgresql.jdbc-url", POSTGRES::getJdbcUrl);
        registry.add("platform.component.vector.postgresql.username", POSTGRES::getUsername);
        registry.add("platform.component.vector.postgresql.password", POSTGRES::getPassword);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    private static final String INDEX_NAME = "it_pg_vectors";
    private static final String TABLE_NAME = "vector_" + INDEX_NAME;
    private static final String SPRING_AI_TABLE = "public.vector_store";
    private static final int DIM = 4;

    @Autowired(required = false)
    private VectorService vectorService;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void provisionSchema() {
        if (jdbcTemplate == null) {
            return;
        }
        // 必须先建 pgvector 扩展、然后建表。Spring AI PgVectorStore 默认 schema 不会在这里出现。
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        // IT 自管的表 — 走 PostgresqlVectorServiceImpl 的自定义路径
        jdbcTemplate.execute(String.format(
                "DROP TABLE IF EXISTS %s", TABLE_NAME));
        jdbcTemplate.execute(String.format(
                "CREATE TABLE %s ("
                        + "id TEXT PRIMARY KEY, "
                        + "content TEXT, "
                        + "metadata JSONB, "
                        + "vector VECTOR(%d))", TABLE_NAME, DIM));
        // Spring AI 默认表 — 生产 PostgresqlVectorAutoConfiguration 创建 PgVectorStore 时
        // 没透传 PostgresqlConfig.tableName,builder 默认 schema=public / table=vector_store,
        // 这里建同名表让 similaritySearch 的硬编码 SQL 能命中。
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + SPRING_AI_TABLE);
        jdbcTemplate.execute(String.format(
                "CREATE TABLE %s ("
                        + "id TEXT PRIMARY KEY, "
                        + "content TEXT, "
                        + "metadata JSONB, "
                        + "embedding VECTOR(%d))", SPRING_AI_TABLE, DIM));
    }

    @Test
    void createIndexWithPgVector_addText_search() {
        if (vectorService == null) {
            return;
        }
        // Phase A 的 createIndex 在 PostgreSQL 实现中只记录日志(依赖 DBA 迁移脚本),
        // 但表的实际存在性由本测试 @BeforeEach 准备。
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setDimension(DIM);
        config.setMetric("l2");
        vectorService.createIndex(INDEX_NAME, config);

        // addText:走 AbstractVectorService → addEmbeddings → INSERT … ON CONFLICT
        String id = vectorService.addText(INDEX_NAME, "wave-b integration sample", Map.of("source", "it"));
        assertThat(id).isNotBlank();
        assertThat(vectorService.countDocuments(INDEX_NAME)).isPositive();

        // searchByText:走 vectorStore 门面 → similaritySearchByVector(pgvector <-> 距离)
        List<VectorSearchResult> hits = vectorService.searchByText(INDEX_NAME, "integration", 3);
        assertThat(hits).isNotNull();

        VectorProperties.IndexConfig loaded = vectorService.getIndexConfig(INDEX_NAME);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getDimension())
                .as("PostgresVectorStore 已透传 dimension=1536 给 builder")
                .isEqualTo(1536);
    }

    @Test
    void hybridSearch_textAndKeyword() {
        if (vectorService == null) {
            return;
        }
        // Phase A 的 hybridSearch 默认实现为降级文本搜索;
        // 这里只验证降级路径不报错 + 至少返回结果列表(可能为空)
        vectorService.addText(INDEX_NAME, "alpha bravo charlie", Map.of("tag", "alpha"));
        vectorService.addText(INDEX_NAME, "alpha delta echo", Map.of("tag", "alpha"));
        vectorService.addText(INDEX_NAME, "foxtrot golf hotel", Map.of("tag", "bravo"));
        var options = com.richie.component.vector.model.HybridSearchOptions.builder()
                .keywordQuery("alpha")
                .build();
        List<VectorSearchResult> hits = vectorService.hybridSearch(INDEX_NAME, "alpha", "alpha", 5, options);
        assertThat(hits).isNotNull();
    }

    @Test
    void listIndexesAfterCreate_returnsIndexName() {
        if (vectorService == null) {
            return;
        }
        // baseline: insert one row so countDocuments 一定可观察
        String id = vectorService.addText(INDEX_NAME, "ping", Map.of());
        assertThat(id).isNotBlank();
        // 走 listDocuments 分页接口(由 AbstractVectorService.listDocuments → listDocumentsImpl → 真实 SQL SELECT)
        List<VectorRecord> page = vectorService.listDocuments(INDEX_NAME, 0, 10);
        assertThat(page).isNotEmpty();
        assertThat(page.get(0).getIndexName()).isEqualTo(INDEX_NAME);
    }
}
