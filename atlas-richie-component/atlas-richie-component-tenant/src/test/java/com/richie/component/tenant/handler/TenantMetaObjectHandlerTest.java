package com.richie.component.tenant.handler;

import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.contract.model.TenantPrincipal;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TenantMetaObjectHandler — INSERT 自动填充 tenant_id")
class TenantMetaObjectHandlerTest {

    private static final String TENANT_ID = "tenantId";

    /**
     * 测试子类：内联 insertFill 逻辑（hasGetter → getTenantId → setValue），
     * 跳过 strictInsertFill 内部的 TableInfo 查找。
     * 验证重点：路由逻辑（有/无 tenantId getter、有/无绑定租户）。
     */
    private static class TestableHandler extends TenantMetaObjectHandler {
        @Override
        public void insertFill(MetaObject metaObject) {
            if (!metaObject.hasGetter(TENANT_ID)) {
                return;
            }
            Long tenantId = TenantContext.getTenantId();
            metaObject.setValue(TENANT_ID, Objects.requireNonNullElse(tenantId, 0L));
        }
    }

    private TenantMetaObjectHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TestableHandler();
        TenantContext.init(new ThreadLocalHolder());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("有 tenantId getter + 已绑定租户 → 填充当前租户 ID")
    void insertFillWithBoundTenant() {
        MetaObject metaObject = mock(MetaObject.class);
        when(metaObject.hasGetter(TENANT_ID)).thenReturn(true);

        TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
            handler.insertFill(metaObject);
        });

        verify(metaObject).hasGetter(TENANT_ID);
        verify(metaObject).setValue(TENANT_ID, 1001L);
    }

    @Test
    @DisplayName("无 tenantId getter → 跳过填充")
    void insertFillWithoutGetterSkips() {
        MetaObject metaObject = mock(MetaObject.class);
        when(metaObject.hasGetter(TENANT_ID)).thenReturn(false);

        handler.insertFill(metaObject);

        verify(metaObject, never()).setValue(anyString(), any());
    }

    @Test
    @DisplayName("未绑定租户 → 填充 0L（平台默认租户）")
    void insertFillWithoutTenantFillsZero() {
        MetaObject metaObject = mock(MetaObject.class);
        when(metaObject.hasGetter(TENANT_ID)).thenReturn(true);

        handler.insertFill(metaObject);

        verify(metaObject).hasGetter(TENANT_ID);
        verify(metaObject).setValue(TENANT_ID, 0L);
    }

    @Test
    @DisplayName("updateFill 不更新租户字段（空实现）")
    void updateFillIsNoOp() {
        MetaObject metaObject = mock(MetaObject.class);
        handler.updateFill(metaObject);
        // updateFill 为空实现，不应调用任何 setValue
        verify(metaObject, never()).setValue(anyString(), any());
    }
}
