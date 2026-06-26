package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.DataSourceContextHolder;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.component.tenant.exception.BusinessException;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.component.tenant.support.PostgresTestSupport;
import com.richie.component.tenant.support.TenantIntegrationTest;
import com.richie.contract.model.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DatabaseStrategy 集成测试 — 数据源 key 上下文真跑 PostgreSQL。
 *
 * <p>Database 模式下 {@link DatabaseStrategy#beforeSqlExecute} 将租户的数据源 key
 * 写入 {@link DataSourceContextHolder},由 {@code DynamicTenantDataSource} 读取后
 * 路由到租户专属数据源。</p>
 *
 * <p>本测试在同一个 PostgreSQL 实例内创建多个数据库（tenant 100 / 200 各一个）
 * 验证：</p>
 * <ul>
 *   <li>{@link DataSourceContextHolder} 在多租户上下文中正确写入不同的数据源 key</li>
 *   <li>租户 100 写入数据库 {@code tenant_100} 后,SQL 真跑该库能查到独立数据</li>
 *   <li>租户 200 写入数据库 {@code tenant_200} 后,SQL 真跑该库只能查到自己的数据</li>
 *   <li>{@link IsolationMode#DATABASE} 路由正确性</li>
 *   <li>校验：未绑定租户 / 非法租户 ID / 缺失 dataSourceName</li>
 * </ul>
 *
 * <p>注：本 IT 验证策略正确填充 {@link DataSourceContextHolder}，
 * 不验证 {@code DynamicTenantDataSource} 实际路由行为（后者已有单元测试覆盖）。</p>
 */
@TenantIntegrationTest
@DisplayName("DatabaseStrategy 集成测试 — 数据源 key 路由 (PostgreSQL)")
class DatabaseStrategyIT {

    private static final String DB_TENANT_100 = "tenant_db_100";
    private static final String DB_TENANT_200 = "tenant_db_200";
    private static final long TENANT_100 = 100L;
    private static final long TENANT_200 = 200L;

    private Connection adminConnection;
    private MultiTenancyProperties properties;
    private DatabaseStrategy databaseStrategy;
    private TenantInfoProvider noopProvider;

    @BeforeAll
    static void initContext() {
        TenantContext.init(new ThreadLocalHolder());
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(PostgresTestSupport.isEnabled(), "PostgreSQL not available");

        PostgresTestSupport pg = PostgresTestSupport.getInstance();
        // 用管理员连接创建租户专属数据库
        adminConnection = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        adminConnection.setAutoCommit(true);

        dropDatabaseIfExists(DB_TENANT_100);
        dropDatabaseIfExists(DB_TENANT_200);
        adminConnection.createStatement().execute("CREATE DATABASE " + DB_TENANT_100);
        adminConnection.createStatement().execute("CREATE DATABASE " + DB_TENANT_200);

        // 在每个租户库建独立表并 seed 数据
        seedTenantDatabase(DB_TENANT_100, 100);
        seedTenantDatabase(DB_TENANT_200, 200);

        properties = new MultiTenancyProperties();

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

        databaseStrategy = new DatabaseStrategy(properties, noopProvider);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (adminConnection != null && !adminConnection.isClosed()) {
            // 清理租户专属数据库
            dropDatabaseIfExists(DB_TENANT_100);
            dropDatabaseIfExists(DB_TENANT_200);
            adminConnection.close();
        }
        TenantContext.clear();
        DataSourceContextHolder.clear();
    }

    private void dropDatabaseIfExists(String dbName) {
        try (Statement stmt = adminConnection.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS " + dbName + " WITH (FORCE)");
        } catch (Exception ignored) {
            // 数据库可能不存在或仍有连接，忽略
        }
    }

    private void seedTenantDatabase(String dbName, long tenantId) throws Exception {
        String jdbcUrl = adminConnection.getMetaData().getURL()
                .replace("/" + adminConnection.getCatalog(), "/" + dbName);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "tenant_it", "tenant_it");
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE orders ("
                    + "id BIGINT PRIMARY KEY, "
                    + "tenant_id BIGINT NOT NULL, "
                    + "amount NUMERIC(10,2) NOT NULL)");
            stmt.execute("INSERT INTO orders VALUES (1, " + tenantId + ", 100.00)");
            stmt.execute("INSERT INTO orders VALUES (2, " + tenantId + ", 200.00)");
        }
    }

    private TenantInfo tenantInfo(long tenantId, String dataSourceKey) {
        return new TenantInfo()
                .setTenantId(tenantId)
                .setMode(IsolationMode.DATABASE)
                .setDataSourceName(dataSourceKey)
                .setStatus(TenantStatus.ACTIVE);
    }

    private Connection connectTo(String dbName) throws Exception {
        String jdbcUrl = adminConnection.getMetaData().getURL()
                .replace("/" + adminConnection.getCatalog(), "/" + dbName);
        Connection conn = DriverManager.getConnection(jdbcUrl, "tenant_it", "tenant_it");
        conn.setAutoCommit(false);
        return conn;
    }

    // ==================== 路由判断 ====================

    @Nested
    @DisplayName("路由判断")
    class Routing {

        @Test
        @DisplayName("DatabaseStrategy.supports 仅 DATABASE 模式返回 true")
        void supportsOnlyDatabaseMode() {
            assertThat(databaseStrategy.supports(IsolationMode.DATABASE)).isTrue();
            assertThat(databaseStrategy.supports(IsolationMode.COLUMN)).isFalse();
            assertThat(databaseStrategy.supports(IsolationMode.TABLE)).isFalse();
            assertThat(databaseStrategy.supports(IsolationMode.SCHEMA)).isFalse();
            assertThat(databaseStrategy.supports(IsolationMode.HYBRID)).isFalse();
        }
    }

    // ==================== 数据源 key 上下文填充 ====================

    @Nested
    @DisplayName("数据源 key 上下文填充")
    class DataSourceKeyBinding {

        @Test
        @DisplayName("beforeSqlExecute 将 TenantInfo.dataSourceName 写入 DataSourceContextHolder")
        void beforeSqlExecuteSetsDataSourceKey() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "ds-100"));
                assertThat(DataSourceContextHolder.get()).isEqualTo("ds-100");
            });
        }

        @Test
        @DisplayName("同一线程连续调用以最后一次为准")
        void overwritesPreviousDataSourceKey() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "ds-100"));
                assertThat(DataSourceContextHolder.get()).isEqualTo("ds-100");

                databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "ds-100-v2"));
                assertThat(DataSourceContextHolder.get()).isEqualTo("ds-100-v2");
            });
        }
    }

    // ==================== 多租户数据源 key 隔离 ====================

    @Nested
    @DisplayName("多租户数据源 key 隔离")
    class TenantDataSourceIsolation {

        @Test
        @DisplayName("不同租户上下文对应不同 dataSourceKey")
        void differentTenantsGetDifferentDataSourceKeys() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "ds-100"));
                assertThat(DataSourceContextHolder.get()).isEqualTo("ds-100");
            });

            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_200), () -> {
                databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_200, "ds-200"));
                assertThat(DataSourceContextHolder.get()).isEqualTo("ds-200");
            });
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
                    databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, "ds-100")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Tenant not bound");
        }

        @Test
        @DisplayName("租户 ID 非法时抛 BusinessException")
        void rejectsNegativeTenantId() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(-1L), () -> {
                assertThatThrownBy(() ->
                        databaseStrategy.beforeSqlExecute(null, tenantInfo(-1L, "ds-neg")))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("Invalid tenant ID");
            });
        }

        @Test
        @DisplayName("dataSourceName 为 null 时抛 IllegalArgumentException")
        void rejectsNullDataSourceName() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                assertThatThrownBy(() ->
                        databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, null)))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("No datasource configured");
            });
        }
    }

    // ==================== 真实多数据库隔离 ====================

    @Nested
    @DisplayName("真实多数据库隔离")
    class RealDatabaseIsolation {

        @Test
        @DisplayName("tenant 100 上下文连接 tenant_db_100 后仅看到自己的数据")
        void tenant100OnlySeesOwnDatabase() throws Exception {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_100), () -> {
                databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_100, DB_TENANT_100));
                assertThat(DataSourceContextHolder.get()).isEqualTo(DB_TENANT_100);
            });

            try (Connection conn = connectTo(DB_TENANT_100);
                 PreparedStatement ps = conn.prepareStatement("SELECT tenant_id FROM orders ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("tenant_id")).isEqualTo(TENANT_100);
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("tenant_id")).isEqualTo(TENANT_100);
                assertThat(rs.next()).isFalse();
            }
        }

        @Test
        @DisplayName("tenant 200 上下文连接 tenant_db_200 后仅看到自己的数据")
        void tenant200OnlySeesOwnDatabase() throws Exception {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(TENANT_200), () -> {
                databaseStrategy.beforeSqlExecute(null, tenantInfo(TENANT_200, DB_TENANT_200));
                assertThat(DataSourceContextHolder.get()).isEqualTo(DB_TENANT_200);
            });

            try (Connection conn = connectTo(DB_TENANT_200);
                 PreparedStatement ps = conn.prepareStatement("SELECT tenant_id FROM orders ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("tenant_id")).isEqualTo(TENANT_200);
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong("tenant_id")).isEqualTo(TENANT_200);
                assertThat(rs.next()).isFalse();
            }
        }
    }
}
