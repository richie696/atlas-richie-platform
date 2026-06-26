package com.richie.component.tenant.handler;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.component.tenant.exception.BusinessException;
import com.richie.contract.model.TenantPrincipal;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TenantMetaObjectHandler — INSERT 自动填充 tenant_id")
class TenantMetaObjectHandlerTest {

    /** 测试 Bean — 含 tenantId 字段 */
    static class DemoEntity {
        private Long id;
        private Long tenantId;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getTenantId() { return tenantId; }
        public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** 测试 Bean — 不含 tenantId 字段,验证 getter 缺失场景 */
    static class NoTenantEntity {
        private Long id;
        private String name;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private static MetaObject metaFor(Object bean) {
        return MetaObject.forObject(bean,
            SystemMetaObject.DEFAULT_OBJECT_FACTORY,
            SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY,
            new DefaultReflectorFactory());
    }

    private MultiTenancyProperties properties;
    private TenantMetaObjectHandler handler;

    @BeforeEach
    void setUp() {
        properties = new MultiTenancyProperties();
        // 默认 enforceAuthTenant=false — 后向兼容场景
        properties.setEnforceAuthTenant(false);
        handler = new TenantMetaObjectHandler(properties);
        TenantContext.init(new ThreadLocalHolder());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("租户字段填充路由")
    class FillingRouting {

        @Test
        @DisplayName("有 tenantId getter + 已绑定租户 → 填充当前租户 ID")
        void insertFillWithBoundTenant() {
            DemoEntity entity = new DemoEntity();

            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                handler.insertFill(metaFor(entity));
            });

            assertEquals(1001L, entity.getTenantId());
        }

        @Test
        @DisplayName("无 tenantId getter → 跳过填充 (getter 缺失)")
        void insertFillWithoutGetterSkips() {
            NoTenantEntity entity = new NoTenantEntity();

            handler.insertFill(metaFor(entity));

            assertNull(entity.getName());
        }
    }

    @Nested
    @DisplayName("enforceAuthTenant=false → 缺上下文时填 0L (向后兼容)")
    class NonEnforcingFallback {

        @Test
        @DisplayName("未绑定租户 → 填充 0L (平台默认租户)")
        void insertFillWithoutTenantFillsZero() {
            DemoEntity entity = new DemoEntity();

            handler.insertFill(metaFor(entity));

            assertEquals(0L, entity.getTenantId());
        }
    }

    @Nested
    @DisplayName("enforceAuthTenant=true → 缺上下文时 fail-fast (P1#4)")
    class EnforcingFailFast {

        @Test
        @DisplayName("未绑定租户 → 抛 BusinessException,避免 INSERT 静默写 tenant_id=0")
        void insertFillWithoutTenantThrowsWhenEnforcing() {
            properties.setEnforceAuthTenant(true);
            DemoEntity entity = new DemoEntity();

            BusinessException ex = assertThrows(BusinessException.class,
                () -> handler.insertFill(metaFor(entity)));

            assertTrue(ex.getMessage().contains("Tenant context not bound"),
                "异常消息应明确指出 'Tenant context not bound'");
            assertTrue(ex.getMessage().contains("multi-tenancy.enforce-auth-tenant=false"),
                "异常消息应提示如何关闭 enforce 模式");
        }

        @Test
        @DisplayName("已绑定租户 → 正常填充,不抛错")
        void insertFillWithBoundTenantWhenEnforcing() {
            properties.setEnforceAuthTenant(true);
            DemoEntity entity = new DemoEntity();

            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(2002L), () -> {
                handler.insertFill(metaFor(entity));
            });

            assertEquals(2002L, entity.getTenantId());
        }
    }

    @Nested
    @DisplayName("updateFill")
    class UpdateFill {

        @Test
        @DisplayName("updateFill 为空实现,不写租户字段")
        void updateFillIsNoOp() {
            DemoEntity entity = new DemoEntity();

            handler.updateFill(metaFor(entity));

            assertNull(entity.getTenantId());
        }
    }
}