package com.richie.context.common.api.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多租户审计领域基类
 *
 * <p>在 {@link AuditDomain} 基础上增加了租户 ID 字段 {@code tenantId}。
 * 适用于同时需要多租户数据隔离和审计追踪的业务表。</p>
 *
 * @author richie696
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantAuditDomain extends AuditDomain implements TenantAware {

    /**
     * 租户 ID
     */
    protected Long tenantId;

    @Override
    public Long getTenantId() {
        return tenantId;
    }

    @Override
    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

}
