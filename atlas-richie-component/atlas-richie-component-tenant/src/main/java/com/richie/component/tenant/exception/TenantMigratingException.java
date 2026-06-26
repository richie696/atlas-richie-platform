package com.richie.component.tenant.exception;

import lombok.Getter;

/**
 * 租户迁移中异常。
 *
 * <p>当租户处于 {@code MIGRATING} 状态时抛出，表示租户正在进行数据迁移（模式切换），
 * 暂时拒绝访问。HTTP 状态码应为 503。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class TenantMigratingException extends RuntimeException {

    private final Long tenantId;

    public TenantMigratingException(Long tenantId) {
        super("Tenant " + tenantId + " is currently migrating, please try again later");
        this.tenantId = tenantId;
    }

}
