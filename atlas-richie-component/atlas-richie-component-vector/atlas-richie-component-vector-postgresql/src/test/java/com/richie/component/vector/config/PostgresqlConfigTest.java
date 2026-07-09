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
package com.richie.component.vector.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PostgresqlConfig}.
 *
 * <p>Tests verify all configuration properties including HikariCP pool settings,
 * connection parameters, and default values. This ensures the configuration
 * can be properly bound from application properties.</p>
 */
class PostgresqlConfigTest {

    @Nested
    @DisplayName("Default values")
    class DefaultValuesTests {

        @Test
        @DisplayName("should have correct default JDBC URL")
        void shouldHaveCorrectDefaultJdbcUrl() {
            PostgresqlConfig config = new PostgresqlConfig();
            assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/yourdb");
        }

        @Test
        @DisplayName("should have correct default username")
        void shouldHaveCorrectDefaultUsername() {
            PostgresqlConfig config = new PostgresqlConfig();
            assertThat(config.getUsername()).isEqualTo("youruser");
        }

        @Test
        @DisplayName("should have correct default password")
        void shouldHaveCorrectDefaultPassword() {
            PostgresqlConfig config = new PostgresqlConfig();
            assertThat(config.getPassword()).isEqualTo("yourpassword");
        }

        @Test
        @DisplayName("should have correct default pool size settings")
        void shouldHaveCorrectDefaultPoolSettings() {
            PostgresqlConfig config = new PostgresqlConfig();
            assertThat(config.getMaximumPoolSize()).isEqualTo(30);
            assertThat(config.getMinimumIdle()).isEqualTo(10);
        }

        @Test
        @DisplayName("should have correct default timeout settings")
        void shouldHaveCorrectDefaultTimeoutSettings() {
            PostgresqlConfig config = new PostgresqlConfig();
            assertThat(config.getIdleTimeout()).isEqualTo(600_000L);
            assertThat(config.getMaxLifetime()).isEqualTo(1_800_000L);
            assertThat(config.getConnectionTimeout()).isEqualTo(30_000L);
            assertThat(config.getValidationTimeout()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("should have correct default pool name")
        void shouldHaveCorrectDefaultPoolName() {
            PostgresqlConfig config = new PostgresqlConfig();
            assertThat(config.getPoolName()).isEqualTo("HikariCP");
        }

        @Test
        @DisplayName("should have correct default auto commit")
        void shouldHaveCorrectDefaultAutoCommit() {
            PostgresqlConfig config = new PostgresqlConfig();
            assertThat(config.getAutoCommit()).isTrue();
        }

        @Test
        @DisplayName("should have correct default connection test query")
        void shouldHaveCorrectDefaultConnectionTestQuery() {
            PostgresqlConfig config = new PostgresqlConfig();
            assertThat(config.getConnectionTestQuery()).isEqualTo("SELECT 1");
        }
    }

    @Nested
    @DisplayName("Setters")
    class SettersTests {

        @Test
        @DisplayName("should allow setting custom JDBC URL")
        void shouldAllowSettingCustomJdbcUrl() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setJdbcUrl("jdbc:postgresql://custom-host:5432/mydb");
            assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://custom-host:5432/mydb");
        }

        @Test
        @DisplayName("should allow setting custom username")
        void shouldAllowSettingCustomUsername() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setUsername("customuser");
            assertThat(config.getUsername()).isEqualTo("customuser");
        }

        @Test
        @DisplayName("should allow setting custom password")
        void shouldAllowSettingCustomPassword() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setPassword("custompassword");
            assertThat(config.getPassword()).isEqualTo("custompassword");
        }

        @Test
        @DisplayName("should allow setting custom pool size")
        void shouldAllowSettingCustomPoolSize() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setMaximumPoolSize(50);
            config.setMinimumIdle(20);
            assertThat(config.getMaximumPoolSize()).isEqualTo(50);
            assertThat(config.getMinimumIdle()).isEqualTo(20);
        }

        @Test
        @DisplayName("should allow setting custom timeout values")
        void shouldAllowSettingCustomTimeoutValues() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setIdleTimeout(300_000L);
            config.setMaxLifetime(900_000L);
            config.setConnectionTimeout(15_000L);
            config.setValidationTimeout(3_000L);
            assertThat(config.getIdleTimeout()).isEqualTo(300_000L);
            assertThat(config.getMaxLifetime()).isEqualTo(900_000L);
            assertThat(config.getConnectionTimeout()).isEqualTo(15_000L);
            assertThat(config.getValidationTimeout()).isEqualTo(3_000L);
        }

        @Test
        @DisplayName("should allow setting custom pool name")
        void shouldAllowSettingCustomPoolName() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setPoolName("PostgreSQLPool");
            assertThat(config.getPoolName()).isEqualTo("PostgreSQLPool");
        }

        @Test
        @DisplayName("should allow setting auto commit to false")
        void shouldAllowSettingAutoCommitFalse() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setAutoCommit(false);
            assertThat(config.getAutoCommit()).isFalse();
        }

        @Test
        @DisplayName("should allow setting custom connection test query")
        void shouldAllowSettingCustomConnectionTestQuery() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setConnectionTestQuery("SELECT version()");
            assertThat(config.getConnectionTestQuery()).isEqualTo("SELECT version()");
        }
    }

    @Nested
    @DisplayName("Setters return void (Lombok @Data without @Accessors(chain = true))")
    class SettersReturnVoidTests {

        @Test
        @DisplayName("should allow setting multiple properties sequentially")
        void shouldAllowSettingMultiplePropertiesSequentially() {
            PostgresqlConfig config = new PostgresqlConfig();
            config.setJdbcUrl("jdbc:postgresql://chain:5432/db");
            config.setUsername("chained");
            config.setPassword("chained123");
            config.setMaximumPoolSize(100);
            config.setMinimumIdle(25);
            config.setIdleTimeout(500_000L);
            config.setMaxLifetime(1_200_000L);
            config.setConnectionTimeout(20_000L);
            config.setValidationTimeout(4_000L);
            config.setPoolName("ChainedPool");
            config.setAutoCommit(false);
            config.setConnectionTestQuery("SELECT 2");

            assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://chain:5432/db");
            assertThat(config.getUsername()).isEqualTo("chained");
            assertThat(config.getPassword()).isEqualTo("chained123");
            assertThat(config.getMaximumPoolSize()).isEqualTo(100);
            assertThat(config.getMinimumIdle()).isEqualTo(25);
            assertThat(config.getIdleTimeout()).isEqualTo(500_000L);
            assertThat(config.getMaxLifetime()).isEqualTo(1_200_000L);
            assertThat(config.getConnectionTimeout()).isEqualTo(20_000L);
            assertThat(config.getValidationTimeout()).isEqualTo(4_000L);
            assertThat(config.getPoolName()).isEqualTo("ChainedPool");
            assertThat(config.getAutoCommit()).isFalse();
            assertThat(config.getConnectionTestQuery()).isEqualTo("SELECT 2");
        }
    }
}
