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
package com.richie.component.tenant.healthcheck;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.contract.exception.BusinessException;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.IsolationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("StartupSchemaValidator 启动期 Schema 校验")
class StartupSchemaValidatorTest {

    private final ApplicationArguments args = mock(ApplicationArguments.class);
    private DataSource dataSource;
    private Connection connection;
    private DatabaseMetaData metaData;

    @BeforeEach
    void setUp() throws SQLException {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
    }

    private MultiTenancyProperties columnProps() {
        MultiTenancyProperties p = new MultiTenancyProperties();
        p.setEnabled(true);
        p.setMode(IsolationMode.COLUMN);
        return p;
    }

    @Nested
    @DisplayName("总开关场景")
    class ToggleScenarios {

        @Test
        @DisplayName("多租户关闭时跳过校验")
        void disabled() {
            MultiTenancyProperties p = new MultiTenancyProperties();
            p.setEnabled(false);
            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatCode(() -> v.run(args)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("非 COLUMN 模式时跳过校验")
        void nonColumnModeSkipped() {
            MultiTenancyProperties p = new MultiTenancyProperties();
            p.setEnabled(true);
            p.setMode(IsolationMode.SCHEMA);
            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatCode(() -> v.run(args)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("ignoreTables 校验")
    class IgnoreTablesValidation {

        @Test
        @DisplayName("空配置跳过")
        void emptyIgnoreTablesPass() throws SQLException {
            ResultSet emptyRs = emptyResultSet();
            when(metaData.getTables(any(), any(), eq("%"), any())).thenReturn(emptyRs);
            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, columnProps());
            assertThatCode(() -> v.run(args)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ignoreTables 配置的表存在则通过")
        void ignoreTableExistsPass() throws SQLException {
            MultiTenancyProperties p = columnProps();
            p.setIgnoreTables(new ArrayList<>(Arrays.asList("orders", "products")));

            ResultSet rs = resultSetWithNames("orders", "products", "users");
            when(metaData.getTables(any(), any(), eq("%"), any())).thenReturn(rs);

            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatCode(() -> v.run(args)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("ignoreTables 中包含不存在的表名 — fail-fast")
        void ignoreTableNotFoundThrows() throws SQLException {
            MultiTenancyProperties p = columnProps();
            p.setIgnoreTables(new ArrayList<>(Arrays.asList("orders", "ordrs")));

            ResultSet rs = resultSetWithNames("orders", "users");
            when(metaData.getTables(any(), any(), eq("%"), any())).thenReturn(rs);

            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatThrownBy(() -> v.run(args))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(TenantErrorCode.TENANT_IGNORE_TABLE_NOT_FOUND.name());
        }

        @Test
        @DisplayName("ignoreTables 表名大小写不敏感")
        void ignoreTableCaseInsensitive() throws SQLException {
            MultiTenancyProperties p = columnProps();
            p.setIgnoreTables(new ArrayList<>(Arrays.asList("ORDERS")));

            ResultSet rs = resultSetWithNames("orders");
            when(metaData.getTables(any(), any(), eq("%"), any())).thenReturn(rs);

            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatCode(() -> v.run(args)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("schemaTables 校验(tenant_id 列存在性)")
    class SchemaTablesValidation {

        @Test
        @DisplayName("空 schemaTables 配置跳过列校验")
        void emptySchemaTablesSkip() {
            MultiTenancyProperties p = columnProps();
            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatCode(() -> v.run(args)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("schemaTables 中所有表都有 tenant_id 列 — 通过")
        void allTablesHaveTenantIdColumn() throws SQLException {
            MultiTenancyProperties p = columnProps();
            p.getStartupValidation().setSchemaTables(
                new ArrayList<>(Arrays.asList("orders", "products")));

            ResultSet ordersCols = resultSetWithNames("id", "tenant_id", "name");
            ResultSet productsCols = resultSetWithNames("id", "tenant_id", "price");
            when(metaData.getColumns(any(), any(), eq("orders"), any())).thenReturn(ordersCols);
            when(metaData.getColumns(any(), any(), eq("products"), any())).thenReturn(productsCols);

            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatCode(() -> v.run(args)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("业务表缺少 tenant_id 列 — fail-fast")
        void missingTenantIdColumnThrows() throws SQLException {
            MultiTenancyProperties p = columnProps();
            p.getStartupValidation().setSchemaTables(
                new ArrayList<>(Arrays.asList("orders")));

            ResultSet ordersCols = resultSetWithNames("id", "name");
            when(metaData.getColumns(any(), any(), eq("orders"), any())).thenReturn(ordersCols);

            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatThrownBy(() -> v.run(args))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(TenantErrorCode.TENANT_TENANT_ID_COLUMN_MISSING.name());
        }

        @Test
        @DisplayName("自定义 tenant_id 列名生效")
        void customTenantIdColumn() throws SQLException {
            MultiTenancyProperties p = columnProps();
            p.setTenantIdColumn("org_id");
            p.getStartupValidation().setSchemaTables(
                new ArrayList<>(Arrays.asList("orders")));

            ResultSet ordersCols = resultSetWithNames("id", "org_id");
            when(metaData.getColumns(any(), any(), eq("orders"), any())).thenReturn(ordersCols);

            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatCode(() -> v.run(args)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Database 异常传播")
    class ExceptionPropagation {

        @Test
        @DisplayName("SQLException 应包装为 BusinessException")
        void sqlExceptionWrapped() throws SQLException {
            when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));
            MultiTenancyProperties p = columnProps();
            StartupSchemaValidator v = new StartupSchemaValidator(dataSource, p);
            assertThatThrownBy(() -> v.run(args))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to query database metadata");
        }
    }

    // ==================== helpers ====================

    private ResultSet emptyResultSet() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
        return rs;
    }

    private ResultSet resultSetWithNames(String... names) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        LinkedList<String> queue = new LinkedList<>(Arrays.asList(names));
        when(rs.next()).thenAnswer(inv -> !queue.isEmpty());
        when(rs.getString("TABLE_NAME")).thenAnswer(inv -> queue.poll());
        when(rs.getString("COLUMN_NAME")).thenAnswer(inv -> queue.poll());
        return rs;
    }
}