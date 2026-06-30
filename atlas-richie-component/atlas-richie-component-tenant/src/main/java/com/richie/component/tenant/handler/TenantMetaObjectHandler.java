package com.richie.component.tenant.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.contract.exception.BusinessException;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Objects;

/**
 * 租户字段自动填充处理器。
 *
 * <p>INSERT 操作时自动填充 {@code tenantId} 字段:
 * <ul>
 *   <li>已绑定租户上下文 → 填充当前租户 ID</li>
 *   <li>未绑定租户上下文 →
 *     <ul>
 *       <li>{@code enforceAuthTenant=true} → 抛 {@link com.richie.component.tenant.exception.BusinessException}
 *           fail-fast,避免 INSERT 静默写入平台默认租户导致数据归属错误</li>
 *       <li>{@code enforceAuthTenant=false} → 填 {@code 0L}(平台默认租户,向后兼容)</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>DDL 约定:所有业务表 {@code tenant_id BIGINT NOT NULL DEFAULT 0}。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class TenantMetaObjectHandler implements MetaObjectHandler {

    private static final String TENANT_ID = "tenantId";

    private final MultiTenancyProperties properties;

    public TenantMetaObjectHandler(MultiTenancyProperties properties) {
        this.properties = properties;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        if (!metaObject.hasGetter(TENANT_ID)) {
            return;
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null && properties.isEnforceAuthTenant()) {
            throw new BusinessException("TENANT_BUSINESS_ERROR",
                "Tenant context not bound — INSERT would silently write tenant_id=0 (platform default). "
                    + "Set multi-tenancy.enforce-auth-tenant=false to allow this, "
                    + "or ensure the entry point binds a tenant via TenantContext.runWithTenant().");
        }
        metaObject.setValue(TENANT_ID, Objects.requireNonNullElse(tenantId, 0L));
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 通常不更新租户字段
    }
}
