package com.richie.component.tenant.handler;

import com.richie.component.tenant.context.TenantContext;
import org.apache.ibatis.reflection.MetaObject;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

import java.util.Objects;

/**
 * 租户字段自动填充处理器。
 *
 * <p>INSERT 操作时自动填充 {@code tenantId} 字段：
 * <ul>
 *   <li>已绑定租户上下文 → 填充当前租户 ID</li>
 *   <li>未绑定租户上下文 → 填充 {@code 0L}（平台默认租户）</li>
 * </ul>
 *
 * <p>DDL 约定：所有业务表 {@code tenant_id BIGINT NOT NULL DEFAULT 0}。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class TenantMetaObjectHandler implements MetaObjectHandler {

    private static final String TENANT_ID = "tenantId";

    @Override
    public void insertFill(MetaObject metaObject) {
        if (!metaObject.hasGetter(TENANT_ID)) {
            return;
        }
        Long tenantId = TenantContext.getTenantId();
        // 未开启租户功能时，tenant_id 默认为 0，不允许为 null
        this.strictInsertFill(metaObject, TENANT_ID, Long.class, Objects.requireNonNullElse(tenantId, 0L));
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 通常不更新租户字段
    }
}
