package com.richie.component.tenant.autoconfigure;

import com.richie.component.tenant.circuit.DataSourceCircuitBreaker;
import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.cross.TenantTaskDecorator;
import com.richie.component.tenant.handler.TenantMetaObjectHandler;
import com.richie.component.tenant.interceptor.ConnectionResetInterceptor;
import com.richie.component.tenant.interceptor.DynamicTableNameInnerInterceptor;
import com.richie.component.tenant.interceptor.TenantLineInnerInterceptor;
import com.richie.component.tenant.interceptor.TenantStrategyInterceptor;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.component.tenant.support.PostgresTestSupport;
import com.richie.component.tenant.support.TenantIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantAutoConfiguration 集成测试 — Spring 上下文加载 + PostgreSQL 连接。
 *
 * <p>验证自动配置在真实 Spring 环境中正确注册所有核心 Bean，
 * 并能成功连接 PostgreSQL Testcontainer 实例。</p>
 */
@TenantIntegrationTest
class TenantAutoConfigurationIT {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MultiTenancyProperties properties;

    // ==================== 上下文加载 ====================

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    // ==================== 核心 Bean 注册 ====================

    @Test
    void multiTenancyProperties_shouldBeRegistered() {
        assertThat(properties).isNotNull();
    }

    @Test
    void tenantInfoProvider_shouldBeRegistered() {
        assertThat(applicationContext.containsBean("tenantInfoProvider")).isTrue();
        TenantInfoProvider provider = applicationContext.getBean(TenantInfoProvider.class);
        assertThat(provider).isNotNull();
        // NoOp 实现：getTenantInfo 返回 null
        assertThat(provider.getTenantInfo(999L)).isNull();
        assertThat(provider.exists(999L)).isFalse();
    }

    @Test
    void dataSourceCircuitBreaker_shouldBeRegistered() {
        assertThat(applicationContext.containsBean("dataSourceCircuitBreaker")).isTrue();
        DataSourceCircuitBreaker breaker = applicationContext.getBean(DataSourceCircuitBreaker.class);
        assertThat(breaker).isNotNull();
        // 初始状态：所有熔断器 CLOSED
        assertThat(breaker.isOpen("any-key")).isFalse();
    }

    @Test
    void tenantTaskDecorator_shouldBeRegistered() {
        assertThat(applicationContext.containsBean("tenantTaskDecorator")).isTrue();
        TenantTaskDecorator decorator = applicationContext.getBean(TenantTaskDecorator.class);
        assertThat(decorator).isNotNull();
    }

    @Test
    void tenantMetaObjectHandler_shouldBeRegistered() {
        assertThat(applicationContext.containsBean("tenantMetaObjectHandler")).isTrue();
        TenantMetaObjectHandler handler = applicationContext.getBean(TenantMetaObjectHandler.class);
        assertThat(handler).isNotNull();
    }

    // ==================== TenantContext 初始化 ====================

    @Test
    void tenantContext_shouldBeInitialized() {
        // AutoConfiguration 的 @PostConstruct 应已初始化 TenantContext
        assertThat(TenantContext.getHolder()).isNotNull();
        // 未绑定租户时返回 null
        assertThat(TenantContext.get()).isNull();
        assertThat(TenantContext.getTenantId()).isNull();
    }

    // ==================== MyBatis Interceptor 顺序 ====================

    /**
     * 验证 4 个 MyBatis Interceptor 均标注 {@link Order} 且值达预期:
     * <pre>
     *   @Order(1) ConnectionResetInterceptor    — 最内层,SQL 后清理连接资源
     *   @Order(2) TenantLineInnerInterceptor    — tenant_id WHERE 条件
     *   @Order(3) DynamicTableNameInnerInterceptor — 表名后缀改写
     *   @Order(4) TenantStrategyInterceptor     — 最外层,租户解析 + 熔断 + 策略调度
     * </pre>
     *
     * <p>MyBatis-Spring-Boot-Starter 收集 {@code Interceptor} 后经
     * {@code AnnotationAwareOrderComparator.sort()} 排序才注册到
     * {@code InterceptorChain},低 {@code @Order} 值先注册(内层),
     * 高 {@code @Order} 值后注册(外层)。</p>
     */
    @Test
    void interceptorOrder_shouldBeExplicit() {
        assertThat(applicationContext.findAnnotationOnBean(
            "connectionResetInterceptor", Order.class).value()).isEqualTo(1);
        assertThat(applicationContext.findAnnotationOnBean(
            "tenantLineInnerInterceptor", Order.class).value()).isEqualTo(2);
        assertThat(applicationContext.findAnnotationOnBean(
            "dynamicTableNameInnerInterceptor", Order.class).value()).isEqualTo(3);
        assertThat(applicationContext.findAnnotationOnBean(
            "tenantStrategyInterceptor", Order.class).value()).isEqualTo(4);
    }

    // ==================== PostgreSQL 连接 ====================

    @Test
    void postgresConnection_shouldBeAccessible() throws Exception {
        assertThat(PostgresTestSupport.isEnabled()).isTrue();

        PostgresTestSupport pg = PostgresTestSupport.getInstance();
        try (Connection conn = DriverManager.getConnection(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
            assertThat(conn.isValid(5)).isTrue();

            // 验证 PostgreSQL 版本
            String productName = conn.getMetaData().getDatabaseProductName();
            assertThat(productName).containsIgnoringCase("PostgreSQL");

            // 验证数据库名
            String dbName = conn.getCatalog();
            assertThat(dbName).isEqualTo("tenant_it");
        }
    }

    @Test
    void postgresConnection_shouldSupportBasicDDL() throws Exception {
        PostgresTestSupport pg = PostgresTestSupport.getInstance();
        try (Connection conn = DriverManager.getConnection(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())) {
            conn.setAutoCommit(false);

            // CREATE TABLE
            try (var stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS it_test_basic (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(100) NOT NULL,
                        tenant_id BIGINT NOT NULL
                    )
                    """);
            }

            // INSERT + SELECT
            try (var stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO it_test_basic (name, tenant_id) VALUES ('test', 1001)");
            }
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT count(*) FROM it_test_basic WHERE tenant_id = 1001")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }

            conn.rollback();
        }
    }
}
