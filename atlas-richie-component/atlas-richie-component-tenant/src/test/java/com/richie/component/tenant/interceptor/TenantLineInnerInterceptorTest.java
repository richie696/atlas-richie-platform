package com.richie.component.tenant.interceptor;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.contract.model.TenantPrincipal;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TenantLineInnerInterceptor — SQL 改写 (Column 模式)")
class TenantLineInnerInterceptorTest {

    private static final TenantInfoProvider COLUMN_PROVIDER = new StaticProvider(IsolationMode.COLUMN);
    private static final TenantInfoProvider TABLE_PROVIDER = new StaticProvider(IsolationMode.TABLE);

    private MultiTenancyProperties props;
    private TenantLineInnerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        props = new MultiTenancyProperties();
        interceptor = new TenantLineInnerInterceptor(props, COLUMN_PROVIDER);
        TenantContext.init(new ThreadLocalHolder());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 静态租户模式桩
     */
    private static final class StaticProvider implements TenantInfoProvider {
        private final IsolationMode mode;

        StaticProvider(IsolationMode mode) {
            this.mode = mode;
        }

        @Override
        public TenantInfo getTenantInfo(Long tenantId) {
            return new TenantInfo()
                    .setTenantId(tenantId)
                    .setMode(mode)
                    .setStatus(TenantStatus.ACTIVE);
        }

        @Override
        public boolean exists(Long tenantId) {
            return true;
        }
    }

    /**
     * 直接测试 processSql 逻辑（通过反射调用 private 方法）
     */
    private String processSql(String sql, Long tenantId) throws Exception {
        var method = TenantLineInnerInterceptor.class.getDeclaredMethod(
                "processSql", String.class, Long.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, sql, tenantId);
    }

    @Nested
    @DisplayName("SELECT 改写")
    class SelectRewrite {

        @Test
        @DisplayName("简单 SELECT 追加 WHERE tenant_id = ?")
        void simpleSelectAppendsWhere() throws Exception {
            String result = processSql("SELECT * FROM t_order", 1001L);
            assertThat(result.toLowerCase()).contains("tenant_id = 1001");
        }

        @Test
        @DisplayName("带 WHERE 的 SELECT 追加 AND tenant_id = ?")
        void selectWithExistingWhereAppendsAnd() throws Exception {
            String result = processSql("SELECT * FROM t_order WHERE status = 1", 1001L);
            assertThat(result.toLowerCase()).contains("tenant_id = 1001");
            assertThat(result.toLowerCase()).contains("status = 1");
        }

        @Test
        @DisplayName("忽略表不追加 tenant_id")
        void ignoredTableSkipped() throws Exception {
            props.setIgnoreTables(List.of("dict_table"));
            String result = processSql("SELECT * FROM dict_table", 1001L);
            assertThat(result.toLowerCase()).doesNotContain("tenant_id");
        }
    }

    @Nested
    @DisplayName("UPDATE 改写")
    class UpdateRewrite {

        @Test
        @DisplayName("UPDATE 追加 WHERE tenant_id = ?")
        void updateAppendsWhere() throws Exception {
            String result = processSql("UPDATE t_order SET status = 2", 2001L);
            assertThat(result.toLowerCase()).contains("tenant_id = 2001");
        }

        @Test
        @DisplayName("UPDATE 带 WHERE 追加 AND tenant_id = ?")
        void updateWithExistingWhereAppendsAnd() throws Exception {
            String result = processSql("UPDATE t_order SET status = 2 WHERE id = 100", 2001L);
            assertThat(result.toLowerCase()).contains("tenant_id = 2001");
        }
    }

    @Nested
    @DisplayName("DELETE 改写")
    class DeleteRewrite {

        @Test
        @DisplayName("DELETE 追加 WHERE tenant_id = ?")
        void deleteAppendsWhere() throws Exception {
            String result = processSql("DELETE FROM t_order", 3001L);
            assertThat(result.toLowerCase()).contains("tenant_id = 3001");
        }

        @Test
        @DisplayName("DELETE 带 WHERE 追加 AND tenant_id = ?")
        void deleteWithExistingWhereAppendsAnd() throws Exception {
            String result = processSql("DELETE FROM t_order WHERE id = 5", 3001L);
            assertThat(result.toLowerCase()).contains("tenant_id = 3001");
        }
    }

    @Nested
    @DisplayName("INSERT 改写")
    class InsertRewrite {

        @Test
        @DisplayName("INSERT 自动追加 tenant_id 列和值")
        void insertAppendsTenantColumn() throws Exception {
            String result = processSql("INSERT INTO t_order (id, name) VALUES (1, 'test')", 4001L);
            assertThat(result.toLowerCase()).contains("tenant_id");
            assertThat(result).contains("4001");
        }

        @Test
        @DisplayName("INSERT 已有 tenant_id 时不重复追加")
        void insertWithExistingTenantColumnSkipped() throws Exception {
            String sql = "INSERT INTO t_order (id, tenant_id) VALUES (1, 999)";
            String result = processSql(sql, 4001L);
            // 原始 SQL 中已有 tenant_id = 999，不应被覆盖
            assertThat(result).contains("999");
        }
    }

    @Nested
    @DisplayName("自定义列名")
    class CustomColumn {

        @Test
        @DisplayName("自定义 tenantIdColumn 生效")
        void customColumnUsed() throws Exception {
            props.setTenantIdColumn("org_id");
            String result = processSql("SELECT * FROM t_order", 1001L);
            assertThat(result.toLowerCase()).contains("org_id = 1001");
        }
    }

    @Nested
    @DisplayName("模式短路")
    class ModeShortCircuit {

        @Test
        @DisplayName("TABLE 模式下不执行 SQL 改写")
        void tableModeSkipsRewrite() throws Throwable {
            TenantLineInnerInterceptor tableInterceptor =
                    new TenantLineInnerInterceptor(props, TABLE_PROVIDER);
            props.setMode(IsolationMode.TABLE);

            String originalSql = "SELECT * FROM t_order";
            String result = intercept(tableInterceptor, originalSql, 5001L);

            assertThat(result).isEqualTo(originalSql);
        }

        @Test
        @DisplayName("DATABASE 模式下不执行 SQL 改写")
        void databaseModeSkipsRewrite() throws Throwable {
            TenantLineInnerInterceptor tableInterceptor =
                    new TenantLineInnerInterceptor(props, TABLE_PROVIDER);
            props.setMode(IsolationMode.DATABASE);

            String originalSql = "SELECT * FROM t_order";
            String result = intercept(tableInterceptor, originalSql, 5001L);

            assertThat(result).isEqualTo(originalSql);
        }

        @Test
        @DisplayName("HYBRID + 租户为 TABLE 模式时跳过改写")
        void hybridTableTenantSkipsRewrite() throws Throwable {
            TenantLineInnerInterceptor hybridInterceptor =
                    new TenantLineInnerInterceptor(props, TABLE_PROVIDER);
            props.setMode(IsolationMode.HYBRID);

            String originalSql = "SELECT * FROM t_order";
            String result = intercept(hybridInterceptor, originalSql, 5001L);

            assertThat(result).isEqualTo(originalSql);
        }

        @Test
        @DisplayName("HYBRID + 租户为 COLUMN 模式时正常改写")
        void hybridColumnTenantRewrites() throws Throwable {
            props.setMode(IsolationMode.HYBRID);

            String result = intercept(interceptor, "SELECT * FROM t_order", 5001L);

            assertThat(result.toLowerCase()).contains("tenant_id = 5001");
        }

        @Test
        @DisplayName("HYBRID + 租户不存在时跳过改写（不抛异常）")
        void hybridMissingTenantSkipsRewrite() throws Throwable {
            TenantInfoProvider missing = new TenantInfoProvider() {
                @Override
                public TenantInfo getTenantInfo(Long tenantId) {
                    return null;
                }

                @Override
                public boolean exists(Long tenantId) {
                    return false;
                }
            };
            TenantLineInnerInterceptor hybridInterceptor =
                    new TenantLineInnerInterceptor(props, missing);
            props.setMode(IsolationMode.HYBRID);

            String originalSql = "SELECT * FROM t_order";
            String result = intercept(hybridInterceptor, originalSql, 5001L);

            assertThat(result).isEqualTo(originalSql);
        }

        @Test
        @DisplayName("Provider 抛异常时跳过改写（不中断 SQL 执行）")
        void hybridProviderErrorSkipsRewrite() throws Throwable {
            TenantInfoProvider broken = new TenantInfoProvider() {
                @Override
                public TenantInfo getTenantInfo(Long tenantId) {
                    throw new IllegalStateException("remote lookup failed");
                }

                @Override
                public boolean exists(Long tenantId) {
                    throw new IllegalStateException("remote lookup failed");
                }
            };
            TenantLineInnerInterceptor hybridInterceptor =
                    new TenantLineInnerInterceptor(props, broken);
            props.setMode(IsolationMode.HYBRID);

            String originalSql = "SELECT * FROM t_order";
            String result = intercept(hybridInterceptor, originalSql, 5001L);

            assertThat(result).isEqualTo(originalSql);
        }

        private String intercept(TenantLineInnerInterceptor target, String originalSql,
                                 long tenantId) throws Exception {
            TenantPrincipal principal = new TenantPrincipal().setTenantId(tenantId);
            String[] holder = new String[1];
            TenantContext.runWithTenant(principal, () -> {
                StatementHandler handler = mock(StatementHandler.class);
                BoundSql boundSql = new BoundSql(
                        new Configuration(), originalSql, List.of(), null);
                when(handler.getBoundSql()).thenReturn(boundSql);
                try {
                    Method prepare = StatementHandler.class.getMethod(
                            "prepare", Connection.class, Integer.class);
                    Invocation invocation = new Invocation(
                            handler, prepare, new Object[]{null, 1});
                    target.intercept(invocation);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
                holder[0] = boundSql.getSql();
            });
            return holder[0];
        }
    }
}
