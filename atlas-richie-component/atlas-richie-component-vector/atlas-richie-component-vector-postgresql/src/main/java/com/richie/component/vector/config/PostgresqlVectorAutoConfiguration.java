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
package com.richie.component.vector.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 向量数据库自动配置类
 * 只注入唯一的DefaultVectorServiceImpl，自动适配所有Spring AI支持的向量数据库
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PostgresqlConfig.class)
public class PostgresqlVectorAutoConfiguration {

    /**
     * PostgreSQL/PGVector 向量数据库自动装配
     * 依赖：spring-ai-starter-vector-store-pgvector
     * 典型配置：spring.datasource.* + spring.ai.vectorstore.pgvector.*
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.vector", name="provider", havingValue = "postgresql")
    public VectorStore postgresVectorStore(@Qualifier("postgresqlJdbcTemplate") JdbcTemplate jdbcTemplate,
                                           EmbeddingModel embeddingModel, PostgresqlConfig config) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(config.getEmbeddingDimension() != null ? config.getEmbeddingDimension() : 1536)
                .build();
    }

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
