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
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.service.impl.PostgresqlVectorServiceImpl;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL + pgvector provider 的 Spring Boot 自动配置入口。
 *
 * <p>当 {@code platform.component.vector.provider=postgresql} 时激活，
 * 负责构造一个面向 pgvector 的 {@link JdbcTemplate} 连接池（命名隔离以避免
 * 与业务其它 JDBC Bean 冲突）以及一个 Spring AI {@link PgVectorStore}。
 * {@link PostgresqlConfig} 集中管理 JDBC URL / 凭证 / Hikari 池参数；
 * 通用索引意图由 {@link cn.richie696.component.vector.config.VectorProperties}
 * 表达，pgvector DDL 中不直接持有的字段（如 cosine / l2 / ip 度量映射）落在
 * {@link PostgresqlVectorServiceImpl}。</p>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(PostgresqlConfig.class)
@Import(PostgresqlVectorServiceImpl.class)
public class PostgresqlVectorAutoConfiguration {

    /**
     * 构建 pgvector Spring AI {@link VectorStore}，作为 core 模块与
     * {@link PostgresqlVectorServiceImpl} 的桥接点。
     *
     * <p>维度直接来自 {@link PostgresqlConfig#getEmbeddingDimension()}（缺省
     * 1536，对齐 OpenAI text-embedding-3-small），由 {@link PgVectorStore} 在
     * 建表时透传给 {@code vector(N)} 列。其它通用索引字段（metric / indexType）
     * 由 {@link cn.richie696.component.vector.config.VectorProperties.IndexConfig}
     * 通过 {@link PostgresqlVectorServiceImpl#createIndexImpl(String, cn.richie696.component.vector.config.VectorProperties.IndexConfig)}
     * 在 native SQL 层落表，不在 Spring AI builder 上显式设置。</p>
     *
     * @param jdbcTemplate    pgvector 专用的 {@link JdbcTemplate}
     * @param embeddingModel  文本嵌入模型，用于 add/search 时的向量化
     * @param config          PostgreSQL 连接配置
     * @return Spring AI 统一的 {@link VectorStore} 视图
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "postgresql")
    public VectorStore postgresVectorStore(@Qualifier("postgresqlJdbcTemplate") JdbcTemplate jdbcTemplate,
                                           EmbeddingModel embeddingModel, PostgresqlConfig config) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(config.getEmbeddingDimension() != null ? config.getEmbeddingDimension() : 1536)
                .build();
    }

    /**
     * 构建供本 provider 独占使用的 {@link JdbcTemplate}，并显式限定 Bean 名为
     * {@code postgresqlJdbcTemplate}，避免与业务中其它 JDBC Bean 冲突。
     *
     * <p>底层使用 HikariDataSource 直接接收 {@link PostgresqlConfig} 中的
     * URL / 凭证 / poolSize / idleTimeout / 连接测试 SQL 等，与 Spring Boot
     * 默认数据源隔离开，使本 provider 可以独立配置连接参数、不影响主业务库。</p>
     *
     * @param config PostgreSQL 连接配置（URL / 用户 / 密码 / Hikari 池参数）
     * @return 绑定到 pgvector 数据源的 {@link JdbcTemplate}
     */
    @Bean("postgresqlJdbcTemplate")
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "postgresql")
    public JdbcTemplate jdbcTemplate(PostgresqlConfig config) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(config.getJdbcUrl());
        dataSource.setUsername(config.getUsername());
        dataSource.setPassword(config.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(config.getMaximumPoolSize());
        dataSource.setMinimumIdle(config.getMinimumIdle());
        dataSource.setIdleTimeout(config.getIdleTimeout());
        dataSource.setMaxLifetime(config.getMaxLifetime());
        dataSource.setConnectionTimeout(config.getConnectionTimeout());
        dataSource.setValidationTimeout(config.getValidationTimeout());
        dataSource.setPoolName(config.getPoolName());
        dataSource.setAutoCommit(config.getAutoCommit());
        dataSource.setConnectionTestQuery(config.getConnectionTestQuery());
        return new JdbcTemplate(dataSource);
    }

}
