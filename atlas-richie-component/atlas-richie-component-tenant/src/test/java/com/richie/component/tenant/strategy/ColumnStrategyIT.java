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
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.component.tenant.interceptor.TenantLineInnerInterceptor;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ColumnStrategy 集成测试 — 行级隔离真跑 PostgreSQL。
 *
 * <p>与 {@link TenantLineInnerInterceptorTest} 不同，本测试使用真实 PostgreSQL 实例
 * 验证：</p>
 * <ul>
 *   <li>JSqlParser 改写后的 SQL 可被 PostgreSQL 正确执行</li>
 *   <li>INSERT 自动追加 tenant_id 列和值后落库</li>
 *   <li>SELECT 改写 WHERE 条件后多租户行级数据隔离</li>
 *   <li>UPDATE / DELETE 改写 WHERE 后不会越界影响其他租户</li>
 *   <li>配置开关（enabled / ignoreTables / tenantIdColumn）端到端生效</li>
 * </ul>
 *
 * <p>不需要 Spring 上下文，直接构造 {@link TenantLineInnerInterceptor}，
 * 通过 Mock 注入 {@link StatementHandler} 触发 {@code intercept()} 调用，
 * 再用真实 JDBC 连接执行改写后的 SQL。</p>
 */
@TenantIntegrationTest
@DisplayName("ColumnStrategy 集成测试 — 行级隔离 (PostgreSQL)")
class ColumnStrategyIT {

    private static final String TABLE = "it_users";
    private static final long TENANT_100 = 100L;
    private static final long TENANT_200 = 200L;

    private Connection connection;
    private MultiTenancyProperties properties;
    private TenantLineInnerInterceptor interceptor;

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
        properties.setEnabled(true);
        properties.setMode(IsolationMode.COLUMN);
        properties.setTenantIdColumn("tenant_id");
        properties.setIgnoreTables(Collections.emptyList());

        interceptor = new TenantLineInnerInterceptor(properties, new TenantInfoProvider() {
            @Override
            public TenantInfo getTenantInfo(Long tenantId) {
                return null;
            }

            @Override
            public boolean exists(Long tenantId) {
                return false;
            }
        });

        createUsersTable();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS " + TABLE);
            }
            connection.rollback();
            connection.close();
        }
        TenantContext.clear();
    }

    private void createUsersTable() throws Exception {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE TABLE " + TABLE + " ("
                    + "id BIGINT PRIMARY KEY, "
                    + "tenant_id BIGINT NOT NULL, "
                    + "name VARCHAR(50) NOT NULL)");
        }
    }

    // ==================== INSERT 改写 ====================

    @Nested
    @DisplayName("INSERT 自动追加 tenant_id")
    class InsertRewrite {

        @Test
        @DisplayName("绑定租户后 INSERT 自动追加 tenant_id 列和值，真跑 PostgreSQL 验证落库 tenant_id 正确")
        void insertAutoAppendsTenantIdAndPersists() throws Exception {
            String originalSql = "INSERT INTO it_users (id, name) VALUES (1, 'alice')";

            runInTenant(TENANT_100, () -> {
                String rewritten = applyRewrite(originalSql);
                assertThat(rewritten.toLowerCase()).contains("tenant_id");
                assertThat(rewritten).contains("100");

                try (PreparedStatement ps = connection.prepareStatement(rewritten)) {
                    ps.executeUpdate();
                }
                return null;
            });

            // 验证 PostgreSQL 中实际写入的 tenant_id
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT id, tenant_id, name FROM it_users WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(1L);
                assertThat(rs.getLong("tenant_id")).isEqualTo(TENANT_100);
                assertThat(rs.getString("name")).isEqualTo("alice");
            }
        }

        @Test
        @DisplayName("未绑定租户 INSERT 不改写，SQL 原样返回")
        void insertWithoutTenantNotRewritten() throws Exception {
            String originalSql = "INSERT INTO it_users (id, tenant_id, name) VALUES (2, 100, 'bob')";
            String rewritten = applyRewrite(originalSql);

            assertThat(rewritten).isEqualTo(originalSql);
        }

        @Test
        @DisplayName("INSERT 已含 tenant_id 列时不重复追加（保留调用方值）")
        void insertWithExistingTenantIdColumnSkipped() throws Exception {
            String originalSql = "INSERT INTO it_users (id, tenant_id, name) VALUES (3, 999, 'carol')";

            runInTenant(TENANT_100, () -> {
                String rewritten = applyRewrite(originalSql);
                assertThat(rewritten).contains("999");
                int countOf100 = rewritten.split("100", -1).length - 1;
                assertThat(countOf100).isZero();

                try (PreparedStatement ps = connection.prepareStatement(rewritten)) {
                    ps.executeUpdate();
                }
                return null;
            });

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT tenant_id FROM it_users WHERE id = 3")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("tenant_id")).isEqualTo(999L);
            }
        }
    }

    // ==================== SELECT 多租户隔离 ====================

    @Nested
    @DisplayName("SELECT 多租户行级隔离")
    class SelectIsolation {

        @Test
        @DisplayName("租户 100 上下文 SELECT 仅返回 tenant_id=100 的行")
        void selectFiltersByTenant() throws Exception {
            insertRaw(10L, TENANT_100, "alice_100");
            insertRaw(11L, TENANT_200, "bob_200");
            insertRaw(12L, TENANT_100, "carol_100");

            String rewritten = runInTenant(TENANT_100, () -> {
                String r = applyRewrite("SELECT id, tenant_id, name FROM it_users");
                assertThat(r.toLowerCase()).contains("where");
                assertThat(r.toLowerCase()).contains("tenant_id = 100");
                return r;
            });

            try (PreparedStatement ps = connection.prepareStatement(rewritten);
                 ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    assertThat(rs.getLong("tenant_id")).isEqualTo(TENANT_100);
                    count++;
                }
                assertThat(count).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("租户 200 上下文 SELECT 看不到 tenant 100 的数据")
        void selectDifferentTenantSeesOwnDataOnly() throws Exception {
            insertRaw(20L, TENANT_100, "alice_100");

            String rewritten = runInTenant(TENANT_200, () -> {
                String r = applyRewrite("SELECT id, name FROM it_users");
                assertThat(r.toLowerCase()).contains("tenant_id = 200");
                return r;
            });

            try (PreparedStatement ps = connection.prepareStatement(rewritten);
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isFalse();
            }
        }

        @Test
        @DisplayName("带 WHERE 的 SELECT 追加 AND tenant_id = ? 而非覆盖")
        void selectWithExistingWhereAppendsAnd() throws Exception {
            insertRaw(30L, TENANT_100, "alice_100");
            insertRaw(31L, TENANT_200, "bob_200");

            String rewritten = runInTenant(TENANT_100, () -> {
                String r = applyRewrite("SELECT id, name FROM it_users WHERE id > 25");
                assertThat(r.toLowerCase()).contains("id > 25");
                assertThat(r.toLowerCase()).contains("tenant_id = 100");
                return r;
            });

            try (PreparedStatement ps = connection.prepareStatement(rewritten);
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("id")).isEqualTo(30L);
                assertThat(rs.getString("name")).isEqualTo("alice_100");
                assertThat(rs.next()).isFalse();
            }
        }
    }

    // ==================== UPDATE 越权保护 ====================

    @Nested
    @DisplayName("UPDATE 不影响其他租户")
    class UpdateIsolation {

        @Test
        @DisplayName("租户 100 上下文 UPDATE 仅修改 tenant_id=100 的行，不动 tenant_id=200")
        void updateDoesNotTouchOtherTenant() throws Exception {
            insertRaw(40L, TENANT_100, "alice_100");
            insertRaw(41L, TENANT_200, "bob_200");

            Integer affected = runInTenant(TENANT_100, () -> {
                String r = applyRewrite("UPDATE it_users SET name = 'alice_updated'");
                assertThat(r.toLowerCase()).contains("tenant_id = 100");
                try (PreparedStatement ps = connection.prepareStatement(r)) {
                    return ps.executeUpdate();
                }
            });

            assertThat(affected).isEqualTo(1);
            assertThat(queryName(40L)).isEqualTo("alice_updated");
            assertThat(queryName(41L)).isEqualTo("bob_200");
        }
    }

    // ==================== DELETE 越权保护 ====================

    @Nested
    @DisplayName("DELETE 不影响其他租户")
    class DeleteIsolation {

        @Test
        @DisplayName("租户 100 上下文 DELETE 仅删除 tenant_id=100 的行，不动 tenant_id=200")
        void deleteDoesNotTouchOtherTenant() throws Exception {
            insertRaw(50L, TENANT_100, "alice_100");
            insertRaw(51L, TENANT_200, "bob_200");

            Integer affected = runInTenant(TENANT_100, () -> {
                String r = applyRewrite("DELETE FROM it_users");
                assertThat(r.toLowerCase()).contains("tenant_id = 100");
                try (PreparedStatement ps = connection.prepareStatement(r)) {
                    return ps.executeUpdate();
                }
            });

            assertThat(affected).isEqualTo(1);
            assertThat(exists(50L)).isFalse();
            assertThat(exists(51L)).isTrue();
        }
    }

    // ==================== 功能开关 ====================

    @Nested
    @DisplayName("配置开关")
    class FeatureToggle {

        @Test
        @DisplayName("enabled=false 时即使绑定租户也不改写 SQL")
        void disabledDoesNotRewrite() throws Exception {
            properties.setEnabled(false);

            String rewritten = runInTenant(TENANT_100, () -> {
                String r = applyRewrite("SELECT * FROM it_users");
                assertThat(r).isEqualTo("SELECT * FROM it_users");
                return r;
            });
            assertThat(rewritten).isEqualTo("SELECT * FROM it_users");
        }

        @Test
        @DisplayName("未绑定租户时即使 enabled=true 也不改写 SQL")
        void noTenantContextDoesNotRewrite() throws Exception {
            String original = "SELECT * FROM it_users";
            String rewritten = applyRewrite(original);
            assertThat(rewritten).isEqualTo(original);
        }
    }

    // ==================== ignoreTables ====================

    @Nested
    @DisplayName("ignoreTables 排除")
    class IgnoreTables {

        @Test
        @DisplayName("ignoreTables 中的表不被改写")
        void ignoredTableNotRewritten() throws Exception {
            properties.setIgnoreTables(List.of(TABLE));

            String rewritten = runInTenant(TENANT_100, () -> {
                String r = applyRewrite("SELECT * FROM it_users");
                assertThat(r).isEqualTo("SELECT * FROM it_users");
                return r;
            });
            assertThat(rewritten).isEqualTo("SELECT * FROM it_users");
        }

        @Test
        @DisplayName("ignoreTables 大小写不敏感（lowerCase 比较）")
        void ignoredTableCaseInsensitive() throws Exception {
            properties.setIgnoreTables(List.of("IT_USERS"));

            String rewritten = runInTenant(TENANT_100, () -> {
                String r = applyRewrite("SELECT * FROM it_users");
                assertThat(r).isEqualTo("SELECT * FROM it_users");
                return r;
            });
            assertThat(rewritten).isEqualTo("SELECT * FROM it_users");
        }
    }

    // ==================== 自定义列名 ====================

    @Nested
    @DisplayName("自定义 tenantIdColumn")
    class CustomColumn {

        @Test
        @DisplayName("tenantIdColumn=org_id 时改写使用 org_id 列")
        void customColumnApplied() throws Exception {
            properties.setTenantIdColumn("org_id");

            String rewritten = runInTenant(TENANT_100, () -> applyRewrite("SELECT * FROM it_users"));
            assertThat(rewritten.toLowerCase()).contains("org_id = 100");
            assertThat(rewritten.toLowerCase()).doesNotContain("tenant_id =");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 在指定租户上下文中执行可抛出受检异常的 action。
     *
     * <p>由于 {@link TenantContext#runWithTenant} 接受 {@link Runnable}（不允许受检异常），
     * 此处通过 Supplier + RuntimeException 包装解决。</p>
     */
    private <T> T runInTenant(long tenantId, ThrowingSupplier<T> action) {
        TenantPrincipal principal = new TenantPrincipal().setTenantId(tenantId);
        Object[] result = new Object[1];
        TenantContext.runWithTenant(principal, () -> {
            try {
                result[0] = action.get();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        @SuppressWarnings("unchecked")
        T casted = (T) result[0];
        return casted;
    }

    /**
     * 调用 {@link TenantLineInnerInterceptor#intercept} 触发 SQL 改写并返回改写后 SQL。
     *
     * <p>租户 ID 通过当前线程的 {@link TenantContext} 传入，
     * 与生产环境调用路径一致（Filter 绑定 → interceptor 读取）。</p>
     */
    private String applyRewrite(String originalSql) {
        try {
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

            interceptor.intercept(invocation);
            return boundSql.getSql();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private void insertRaw(long id, long tenantId, String name) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO " + TABLE + " (id, tenant_id, name) VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setLong(2, tenantId);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    private String queryName(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name FROM " + TABLE + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                return rs.getString("name");
            }
        }
    }

    private boolean exists(long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM " + TABLE + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
