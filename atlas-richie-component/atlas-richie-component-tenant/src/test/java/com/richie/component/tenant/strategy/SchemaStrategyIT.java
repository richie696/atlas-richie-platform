/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.contract.exception.BusinessException;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.component.tenant.support.PostgresTestSupport;
import com.richie.component.tenant.support.TenantIntegrationTest;
import com.richie.contract.model.TenantPrincipal;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * SchemaStrategy 集成测试 — PostgreSQL Schema 创建与切换。
 *
 * <p>使用 Testcontainers PostgreSQL 实例验证：
 * <ul>
 *   <li>Schema 自动创建（CREATE SCHEMA IF NOT EXISTS）</li>
 *   <li>SET LOCAL search_path 切换 Schema</li>
 *   <li>跨 Schema 数据隔离</li>
 *   <li>Schema 名称安全校验（正则白名单）</li>
 * </ul>
 */
@TenantIntegrationTest
class SchemaStrategyIT {

    private static ThreadLocalHolder holder;
    private Connection connection;
    private SchemaStrategy schemaStrategy;

    @BeforeAll
    static void initContext() {
        holder = new ThreadLocalHolder();
        TenantContext.init(holder);
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(PostgresTestSupport.isEnabled(), "PostgreSQL not available");

        PostgresTestSupport pg = PostgresTestSupport.getInstance();
        connection = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        connection.setAutoCommit(false);

        MultiTenancyProperties properties = new MultiTenancyProperties();
        properties.setSchemaAutoCreate(true);

        TenantInfoProvider noopProvider = new TenantInfoProvider() {
            @Override
            public TenantInfo getTenantInfo(Long tenantId) {
                return null;
            }

            @Override
            public boolean exists(Long tenantId) {
                return false;
            }
        };

        schemaStrategy = new SchemaStrategy(properties, noopProvider);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.rollback();
            // 清理测试 Schema
            cleanupSchema("it_tenant_a");
            cleanupSchema("it_tenant_b");
            cleanupSchema("it_schema_test");
            connection.commit();
            connection.close();
        }
        TenantContext.clear();
    }

    // ==================== Schema 自动创建 ====================

    @Test
    void beforeSqlExecute_shouldAutoCreateSchema() throws Exception {
        TenantInfo tenantInfo = new TenantInfo()
                .setTenantId(1001L)
                .setMode(IsolationMode.SCHEMA)
                .setSchemaName("it_tenant_a")
                .setStatus(TenantStatus.ACTIVE);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);

        TenantContext.runWithTenant(principal, () -> {
            Invocation invocation = createInvocation();
            schemaStrategy.beforeSqlExecute(invocation, tenantInfo);
        });

        // 验证 Schema 已创建
        assertThat(schemaExists("it_tenant_a")).isTrue();
    }

    // ==================== SET LOCAL search_path ====================

    @Test
    void beforeSqlExecute_shouldSetSearchPath() throws Exception {
        // 先创建 Schema
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS it_tenant_a");
        }

        TenantInfo tenantInfo = new TenantInfo()
                .setTenantId(1001L)
                .setMode(IsolationMode.SCHEMA)
                .setSchemaName("it_tenant_a")
                .setStatus(TenantStatus.ACTIVE);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);

        TenantContext.runWithTenant(principal, () -> {
            Invocation invocation = createInvocation();
            schemaStrategy.beforeSqlExecute(invocation, tenantInfo);
        });

        // 在同一事务中验证 search_path 已切换（SET LOCAL 仅在当前事务内生效）
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW search_path")) {
            rs.next();
            String searchPath = rs.getString(1);
            assertThat(searchPath).contains("it_tenant_a");
        }
    }

    // ==================== 数据隔离 ====================

    @Test
    void schemaIsolation_shouldKeepDataSeparate() throws Exception {
        // 创建两个 Schema，各建一张同名表
        String[] schemas = {"it_tenant_a", "it_tenant_b"};
        for (String schema : schemas) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
                stmt.execute("CREATE TABLE IF NOT EXISTS " + schema + ".test_data "
                        + "(id SERIAL PRIMARY KEY, value VARCHAR(50))");
            }
        }

        // Schema A 插入数据
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL search_path TO it_tenant_a");
            stmt.execute("INSERT INTO test_data (value) VALUES ('from_a')");
        }

        // Schema B 插入数据
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL search_path TO it_tenant_b");
            stmt.execute("INSERT INTO test_data (value) VALUES ('from_b')");
        }

        // 验证 Schema A 仅看到自己的数据
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL search_path TO it_tenant_a");
            ResultSet rs = stmt.executeQuery("SELECT value FROM test_data");
            rs.next();
            assertThat(rs.getString("value")).isEqualTo("from_a");
            assertThat(rs.next()).isFalse();
        }

        // 验证 Schema B 仅看到自己的数据
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL search_path TO it_tenant_b");
            ResultSet rs = stmt.executeQuery("SELECT value FROM test_data");
            rs.next();
            assertThat(rs.getString("value")).isEqualTo("from_b");
            assertThat(rs.next()).isFalse();
        }
    }

    // ==================== Schema 名称校验 ====================

    @Test
    void beforeSqlExecute_shouldRejectInvalidSchemaName_withSpecialChars() {
        TenantInfo tenantInfo = new TenantInfo()
                .setTenantId(1001L)
                .setMode(IsolationMode.SCHEMA)
                .setSchemaName("invalid; DROP TABLE users;--")
                .setStatus(TenantStatus.ACTIVE);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);

        TenantContext.runWithTenant(principal, () -> {
            Invocation invocation = createInvocation();
            assertThatThrownBy(() -> schemaStrategy.beforeSqlExecute(invocation, tenantInfo))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("schemaName");
        });
    }

    @Test
    void beforeSqlExecute_shouldRejectNullSchemaName() {
        TenantInfo tenantInfo = new TenantInfo()
                .setTenantId(1001L)
                .setMode(IsolationMode.SCHEMA)
                .setSchemaName(null)
                .setStatus(TenantStatus.ACTIVE);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);

        TenantContext.runWithTenant(principal, () -> {
            Invocation invocation = createInvocation();
            assertThatThrownBy(() -> schemaStrategy.beforeSqlExecute(invocation, tenantInfo))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("schemaName");
        });
    }

    @Test
    void beforeSqlExecute_shouldRejectSchemaNameWithDots() {
        TenantInfo tenantInfo = new TenantInfo()
                .setTenantId(1001L)
                .setMode(IsolationMode.SCHEMA)
                .setSchemaName("tenant.evil")
                .setStatus(TenantStatus.ACTIVE);

        TenantPrincipal principal = new TenantPrincipal();
        principal.setTenantId(1001L);

        TenantContext.runWithTenant(principal, () -> {
            Invocation invocation = createInvocation();
            assertThatThrownBy(() -> schemaStrategy.beforeSqlExecute(invocation, tenantInfo))
                    .isInstanceOf(BusinessException.class);
        });
    }

    // ==================== 非事务连接 fail-fast ====================

    @Test
    void beforeSqlExecute_shouldRejectNonTransactionalConnection() throws Exception {
        try (Connection nonTxConn = DriverManager.getConnection(
                PostgresTestSupport.getInstance().getJdbcUrl(),
                PostgresTestSupport.getInstance().getUsername(),
                PostgresTestSupport.getInstance().getPassword())) {
            assertThat(nonTxConn.getAutoCommit())
                    .as("DriverManager default autoCommit must be true")
                    .isTrue();

            TenantInfo tenantInfo = new TenantInfo()
                    .setTenantId(1001L)
                    .setMode(IsolationMode.SCHEMA)
                    .setSchemaName("it_schema_test")
                    .setStatus(TenantStatus.ACTIVE);

            TenantPrincipal principal = new TenantPrincipal();
            principal.setTenantId(1001L);

            // 关键断言：autoCommit=true 时必须抛错阻断 silent failure
            TenantContext.runWithTenant(principal, () -> {
                Invocation invocation = createInvocation(nonTxConn);
                assertThatThrownBy(() -> schemaStrategy.beforeSqlExecute(invocation, tenantInfo))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("autoCommit=true")
                        .extracting("code").isEqualTo(TenantErrorCode.TENANT_SCHEMA_REQUIRES_TRANSACTION.name());
            });
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建指向当前 Connection 的 MyBatis Invocation。
     * SchemaStrategy 从 {@code invocation.getArgs()[0]} 读取 Connection。
     */
    private Invocation createInvocation() {
        return createInvocation(connection);
    }

    private Invocation createInvocation(Connection target) {
        try {
            StatementHandler handler = mock(StatementHandler.class);
            Method method = StatementHandler.class.getMethod(
                    "prepare", Connection.class, Integer.class);
            return new Invocation(handler, method, new Object[]{target, 1});
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("StatementHandler.prepare signature not found", e);
        }
    }

    private boolean schemaExists(String schemaName) throws Exception {
        try (ResultSet rs = connection.getMetaData().getSchemas()) {
            while (rs.next()) {
                if (schemaName.equals(rs.getString("TABLE_SCHEM"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void cleanupSchema(String schemaName) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        } catch (Exception ignored) {
            // 清理失败不影响测试结果
        }
    }
}
