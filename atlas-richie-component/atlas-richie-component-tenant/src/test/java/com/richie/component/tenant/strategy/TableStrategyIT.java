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
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TableSuffixHolder;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.contract.exception.BusinessException;
import com.richie.component.tenant.interceptor.DynamicTableNameInnerInterceptor;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.component.tenant.support.PostgresTestSupport;
import com.richie.component.tenant.support.TenantIntegrationTest;
import com.richie.contract.model.TenantPrincipal;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TableStrategy 集成测试 — 表级后缀上下文真跑 PostgreSQL。
 *
 * <p>Table 模式下 {@link TableStrategy#beforeSqlExecute} 将租户的表后缀写入
 * {@link TableSuffixHolder}，由下游拦截器读取并改写 SQL 中的表名。</p>
 *
 * <p>本测试使用真实 PostgreSQL 实例（用于验证代码路径在真实环境下无异常），
 * 重点验证 {@link TableSuffixHolder} 在多租户上下文中的隔离行为：</p>
 * <ul>
 *   <li>不同租户上下文对应不同的表后缀（互不干扰）</li>
 *   <li>租户上下文结束后表后缀被清理</li>
 *   <li>策略校验：未绑定租户 / 非法租户 ID 时抛 {@link BusinessException}</li>
 *   <li>{@link IsolationMode#TABLE} 路由正确性</li>
 * </ul>
 *
 * <p>注：当前 codebase 未提供 {@code DynamicTableNameInnerInterceptor}
 * （仅在 docs 中提及），因此本 IT 不验证 SQL 表名改写本身，
 * 仅验证策略正确填充 {@link TableSuffixHolder}。</p>
 */
@TenantIntegrationTest
@DisplayName("TableStrategy 集成测试 — 表级后缀上下文 (PostgreSQL)")
class TableStrategyIT {

    private static final long TENANT_100 = 100L;
    private static final long TENANT_200 = 200L;

    private Connection connection;
    private MultiTenancyProperties properties;
    private TableStrategy tableStrategy;
    private DynamicTableNameInnerInterceptor tableNameInterceptor;
    private TenantInfoProvider noopProvider;

    @BeforeAll
    static void initContext() {
        TenantContext.init(new ThreadLocalHolder());
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(PostgresTestSupport.isEnabled(), "PostgreSQL not available");

        PostgresTestSupport pg = PostgresTestSupport.getInstance();
        connection = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        connection.setAutoCommit(false);

        properties = new MultiTenancyProperties();
        properties.setTableNameSuffix("_${tenant}");

        noopProvider = new TenantInfoProvider() {
            @Override
            public TenantInfo getTenantInfo(Long tenantId) {
                return null;
            }

            @Override
            public boolean exists(Long tenantId) {
                return false;
            }
        };

        tableStrategy = new TableStrategy(properties, noopProvider);
        tableNameInterceptor = new DynamicTableNameInnerInterceptor(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.rollback();
            connection.close();
        }
        TenantContext.clear();
        TableSuffixHolder.clear();
    }

    private TenantInfo tenantInfo(long tenantId, String tableSuffix) {
        return new TenantInfo()
                .setTenantId(tenantId)
                .setMode(IsolationMode.TABLE)
                .setTableSuffix(tableSuffix)
                .setStatus(TenantStatus.ACTIVE);
    }

    // ==================== 路由判断 ====================

    @Nested
    @DisplayName("路由判断")
    class Routing {

        @Test
        @DisplayName("TableStrategy.supports 仅 TABLE 模式返回 true")
        void supportsOnlyTableMode() {
            assertThat(tableStrategy.supports(IsolationMode.TABLE)).isTrue();
            assertThat(tableStrategy.supports(IsolationMode.COLUMN)).isFalse();
            assertThat(tableStrategy.supports(IsolationMode.SCHEMA)).isFalse();
            assertThat(tableStrategy.supports(IsolationMode.DATABASE)).isFalse();
            assertThat(tableStrategy.supports(IsolationMode.HYBRID)).isFalse();
        }
    }

    // ==================== 表后缀上下文填充 ====================

    @Nested
    @DisplayName("表后缀上下文填充")
    class TableSuffixBinding {

        @Test
        @DisplayName("beforeSqlExecute 将 TenantInfo.tableSuffix 写入 TableSuffixHolder")
        void beforeSqlExecuteSetsSuffix() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "_100"));
                assertThat(TableSuffixHolder.get()).isEqualTo("_100");
            });
        }

        @Test
        @DisplayName("同一线程连续调用以最后一次为准")
        void overwritesPreviousSuffix() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "_100"));
                assertThat(TableSuffixHolder.get()).isEqualTo("_100");

                tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "_100_v2"));
                assertThat(TableSuffixHolder.get()).isEqualTo("_100_v2");
            });
        }

        @Test
        @DisplayName("TableSuffixHolder 是独立 ThreadLocal，runWithTenant 不会自动清理（需下游清理）")
        void runWithTenantDoesNotAutoClearSuffix() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "_100"));
                assertThat(TableSuffixHolder.get()).isEqualTo("_100");
            });
            // TenantContext 已清理（ThreadLocalHolder.remove），但 TableSuffixHolder 仍持有
            // 当前实现：下游拦截器/TenantIdentityFilter 需在 finally 显式清理
            assertThat(TableSuffixHolder.get()).isEqualTo("_100");
            TableSuffixHolder.clear();
        }
    }

    // ==================== 多租户后缀隔离 ====================

    @Nested
    @DisplayName("多租户后缀隔离")
    class TenantSuffixIsolation {

        @Test
        @DisplayName("不同租户 ID 对应不同表后缀（独立赋值）")
        void differentTenantsGetDifferentSuffixes() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "_100"));
                assertThat(TableSuffixHolder.get()).isEqualTo("_100");
            });

            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_200), () -> {
                tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_200, "_200"));
                assertThat(TableSuffixHolder.get()).isEqualTo("_200");
            });
        }

        @Test
        @DisplayName("嵌套作用域：TableSuffixHolder 是独立的，最后一次 set 生效（last-write-wins）")
        void nestedScopesLastWriteWins() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "_outer_100"));
                assertThat(TableSuffixHolder.get()).isEqualTo("_outer_100");

                TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_200), () -> {
                    tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_200, "_inner_200"));
                    assertThat(TableSuffixHolder.get()).isEqualTo("_inner_200");
                });

                // TenantContext 嵌套正确恢复了外层租户 100，但 TableSuffixHolder 是独立 ThreadLocal
                // 仍保留内层的最后写入值 _inner_200
                assertThat(TenantContext.getTenantId()).isEqualTo(TENANT_100);
                assertThat(TableSuffixHolder.get()).isEqualTo("_inner_200");
            });
            TableSuffixHolder.clear();
        }
    }

    // ==================== 校验路径 ====================

    @Nested
    @DisplayName("校验路径")
    class Validation {

        @Test
        @DisplayName("未绑定租户上下文时 beforeSqlExecute 抛 BusinessException")
        void rejectsWithoutTenantContext() {
            assertThatThrownBy(() ->
                    tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "_100")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Tenant not bound");
        }

        @Test
        @DisplayName("租户 ID 为负数时抛 BusinessException")
        void rejectsNegativeTenantId() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(-1L), () -> {
                assertThatThrownBy(() ->
                        tableStrategy.beforeSqlExecute(null, tenantInfo(-1L, "_neg")))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("Invalid tenant ID");
            });
        }

        @Test
        @DisplayName("租户 ID 为 0 时抛 BusinessException")
        void rejectsZeroTenantId() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(0L), () -> {
                assertThatThrownBy(() ->
                        tableStrategy.beforeSqlExecute(null, tenantInfo(0L, "_zero")))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("Invalid tenant ID");
            });
        }
    }

    // ==================== 真跑 PostgreSQL 上下文 ====================

    @Nested
    @DisplayName("真实 PostgreSQL 环境下代码路径无异常")
    class RealPostgresContext {

        @Test
        @DisplayName("租户 100 上下文 + 表后缀设置 + JDBC ping 不抛异常")
        void tenantContextAndJdbcPingWorks() throws Exception {
            // 设置租户上下文与表后缀
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                tableStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "_100"));
            });
            // 退出 runWithTenant 后 TableSuffixHolder 仍持有 _100（独立 ThreadLocal）
            assertThat(TableSuffixHolder.get()).isEqualTo("_100");
            TableSuffixHolder.clear(); // 模拟下游清理

            // 验证真实 PG 连接可用
            try (Statement stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    // ==================== DynamicTableNameInnerInterceptor 真跑 PostgreSQL ====================

    @Nested
    @DisplayName("DynamicTableNameInnerInterceptor — SQL 表名改写真跑 PG")
    class DynamicTableNameRewrite {

        private static final String TABLE = "it_products";

        @BeforeEach
        void createTables() throws Exception {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TABLE + "_100");
                stmt.execute("DROP TABLE IF EXISTS " + TABLE + "_200");
                stmt.execute("CREATE TABLE " + TABLE + "_100 ("
                        + "id BIGINT PRIMARY KEY, name VARCHAR(50), price INT)");
                stmt.execute("CREATE TABLE " + TABLE + "_200 ("
                        + "id BIGINT PRIMARY KEY, name VARCHAR(50), price INT)");
                stmt.execute("INSERT INTO " + TABLE + "_100 VALUES (1, 'widget_100', 100)");
                stmt.execute("INSERT INTO " + TABLE + "_100 VALUES (2, 'gadget_100', 200)");
                stmt.execute("INSERT INTO " + TABLE + "_200 VALUES (3, 'widget_200', 300)");
                stmt.execute("INSERT INTO " + TABLE + "_200 VALUES (4, 'gadget_200', 400)");
            }
        }

        @AfterEach
        void dropTables() throws Exception {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TABLE + "_100");
                stmt.execute("DROP TABLE IF EXISTS " + TABLE + "_200");
            }
        }

        @Test
        @DisplayName("suffix=_100 时 SELECT 改写到 it_products_100，验证只看到 100 租户数据")
        void selectRewrittenToSuffixedTable() throws Exception {
            TableSuffixHolder.set("_100");
            try {
                String rewritten = applyTableRewrite("SELECT * FROM it_products");
                assertThat(rewritten.toLowerCase()).contains("from it_products_100");

                try (PreparedStatement ps = connection.prepareStatement(rewritten);
                     ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        assertThat(rs.getString("name")).endsWith("_100");
                        count++;
                    }
                    assertThat(count).isEqualTo(2);
                }
            } finally {
                TableSuffixHolder.clear();
            }
        }

        @Test
        @DisplayName("suffix=_200 时 SELECT 改写到 it_products_200")
        void selectRewrittenToDifferentSuffix() throws Exception {
            TableSuffixHolder.set("_200");
            try {
                String rewritten = applyTableRewrite("SELECT * FROM it_products");
                assertThat(rewritten.toLowerCase()).contains("from it_products_200");

                try (PreparedStatement ps = connection.prepareStatement(rewritten);
                     ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        assertThat(rs.getString("name")).endsWith("_200");
                        count++;
                    }
                    assertThat(count).isEqualTo(2);
                }
            } finally {
                TableSuffixHolder.clear();
            }
        }

        @Test
        @DisplayName("INSERT 改写表名后真跑 PG 写入正确表")
        void insertRewrittenAndPersisted() throws Exception {
            TableSuffixHolder.set("_100");
            try {
                String rewritten = applyTableRewrite(
                        "INSERT INTO it_products (id, name, price) VALUES (99, 'new_100', 999)");
                assertThat(rewritten.toLowerCase()).contains("into it_products_100");

                try (PreparedStatement ps = connection.prepareStatement(rewritten)) {
                    ps.executeUpdate();
                }

                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT name FROM it_products_100 WHERE id = 99")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("name")).isEqualTo("new_100");
                }
            } finally {
                TableSuffixHolder.clear();
            }
        }

        @Test
        @DisplayName("UPDATE 改写表名后只更新带后缀的表")
        void updateRewrittenTouchesOnlySuffixedTable() throws Exception {
            TableSuffixHolder.set("_100");
            try {
                String rewritten = applyTableRewrite("UPDATE it_products SET price = 999");
                assertThat(rewritten.toLowerCase()).contains("update it_products_100");

                try (PreparedStatement ps = connection.prepareStatement(rewritten)) {
                    assertThat(ps.executeUpdate()).isEqualTo(2);
                }

                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT MAX(price) FROM it_products_100")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(999);
                }
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT MAX(price) FROM it_products_200")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(400);
                }
            } finally {
                TableSuffixHolder.clear();
            }
        }

        @Test
        @DisplayName("DELETE 改写表名后只删带后缀的表")
        void deleteRewrittenTouchesOnlySuffixedTable() throws Exception {
            TableSuffixHolder.set("_100");
            try {
                String rewritten = applyTableRewrite("DELETE FROM it_products");
                assertThat(rewritten.toLowerCase()).contains("from it_products_100");

                try (PreparedStatement ps = connection.prepareStatement(rewritten)) {
                    assertThat(ps.executeUpdate()).isEqualTo(2);
                }

                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM it_products_100")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isZero();
                }
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM it_products_200")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            } finally {
                TableSuffixHolder.clear();
            }
        }

        @Test
        @DisplayName("未设置 suffix 时不改写 SQL")
        void noSuffixNotRewritten() throws Exception {
            String rewritten = applyTableRewrite("SELECT * FROM it_products");
            assertThat(rewritten).isEqualToIgnoringCase("SELECT * FROM it_products");
        }

        @Test
        @DisplayName("enabled=false 时即使设置 suffix 也不改写")
        void disabledDoesNotRewrite() throws Exception {
            properties.setEnabled(false);
            TableSuffixHolder.set("_100");
            try {
                String rewritten = applyTableRewrite("SELECT * FROM it_products");
                assertThat(rewritten).isEqualToIgnoringCase("SELECT * FROM it_products");
            } finally {
                TableSuffixHolder.clear();
            }
        }

        @Test
        @DisplayName("ignoreTables 中的表不改写（即使设置了 suffix）")
        void ignoredTableNotRewritten() throws Exception {
            properties.setIgnoreTables(List.of(TABLE));
            TableSuffixHolder.set("_100");
            try {
                String rewritten = applyTableRewrite("SELECT * FROM it_products");
                assertThat(rewritten).isEqualToIgnoringCase("SELECT * FROM it_products");
            } finally {
                TableSuffixHolder.clear();
            }
        }
    }

    /**
     * 调用 {@link DynamicTableNameInnerInterceptor#intercept} 触发表名改写并返回改写后 SQL。
     * 当前线程需已通过 {@link TableSuffixHolder#set} 设置后缀。
     */
    private String applyTableRewrite(String originalSql) throws Exception {
        StatementHandler handler = mock(StatementHandler.class);
        BoundSql boundSql = new BoundSql(
                new Configuration(),
                originalSql,
                Collections.<ParameterMapping>emptyList(),
                null);
        when(handler.getBoundSql()).thenReturn(boundSql);

        Method prepare = StatementHandler.class.getMethod(
                "prepare", Connection.class, Integer.class);
        Invocation invocation = new Invocation(handler, prepare, new Object[]{connection, 1});

        try {
            tableNameInterceptor.intercept(invocation);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        return boundSql.getSql();
    }
}
