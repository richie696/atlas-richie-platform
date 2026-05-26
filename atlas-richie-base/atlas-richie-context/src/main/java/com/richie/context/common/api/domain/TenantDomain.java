package com.richie.context.common.api.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 多租户领域基类
 *
 * <p>在 {@link Domain} 基础上增加了租户 ID 字段 {@code tenantId}。
 * 适用于需要多租户数据隔离但无需审计追踪的表。</p>
 *
 * @author richie696
 * @since 1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class TenantDomain extends Domain implements TenantAware {

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
