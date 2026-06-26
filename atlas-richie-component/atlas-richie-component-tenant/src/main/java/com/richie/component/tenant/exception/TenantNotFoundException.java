package com.richie.component.tenant.exception;

import lombok.Getter;

/**
 * 租户未找到异常。
 *
 * <p>当 {@code TenantInfoProvider.getTenantInfo(tenantId)} 返回 {@code null} 时抛出，
 * 表示租户在 {@code sys_tenant} 表中不存在或未被注册。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class TenantNotFoundException extends RuntimeException {

    private final Long tenantId;

    public TenantNotFoundException(Long tenantId) {
        super("Tenant not found: " + tenantId);
        this.tenantId = tenantId;
    }

    public TenantNotFoundException(String message) {
        super(message);
        this.tenantId = null;
    }

}
