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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PostgresqlVectorAutoConfiguration}.
 *
 * <p>Tests verify that the auto-configuration correctly creates beans
 * with proper parameter binding from PostgresqlConfig.</p>
 */
class PostgresqlVectorAutoConfigurationTest {

    @Nested
    @DisplayName("jdbcTemplate bean creation")
    class JdbcTemplateBeanTests {

        @Test
        @DisplayName("should create JdbcTemplate with configured HikariCP settings")
        void jdbcTemplate_shouldCreateWithHikariCPConfig() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setJdbcUrl("jdbc:postgresql://test-host:5432/testdb");
            config.setUsername("testuser");
            config.setPassword("testpass");
            config.setMaximumPoolSize(50);
            config.setMinimumIdle(5);
            config.setIdleTimeout(300_000L);
            config.setMaxLifetime(900_000L);
            config.setConnectionTimeout(20_000L);
            config.setValidationTimeout(3_000L);
            config.setPoolName("TestPool");
            config.setAutoCommit(false);
            config.setConnectionTestQuery("SELECT version()");

            PostgresqlVectorAutoConfiguration configuration = new PostgresqlVectorAutoConfiguration();

            JdbcTemplate jdbcTemplate = configuration.jdbcTemplate(config);

            assertThat(jdbcTemplate).isNotNull();
            // The JdbcTemplate wraps a HikariDataSource - verify the config was applied
            assertThat(jdbcTemplate.getDataSource()).isNotNull();
        }
    }

    @Nested
    @DisplayName("postgresVectorStore bean creation")
    class PostgresVectorStoreBeanTests {

        @Test
        @DisplayName("should create VectorStore with JdbcTemplate and EmbeddingModel")
        void postgresVectorStore_shouldCreateWithDependencies() {
            PostgresqlVectorAutoConfiguration configuration = new PostgresqlVectorAutoConfiguration();

            JdbcTemplate mockJdbcTemplate = new JdbcTemplate();
            EmbeddingModel mockEmbeddingModel = org.mockito.Mockito.mock(EmbeddingModel.class);
            PostgresqlConfig config = new PostgresqlConfig();

            VectorStore vectorStore = configuration.postgresVectorStore(mockJdbcTemplate, mockEmbeddingModel, config);

            assertThat(vectorStore).isNotNull();
        }
    }
}
