package com.richie.component.tenant.exception;

import lombok.Getter;

/**
 * 租户模式迁移拒绝异常。
 *
 * <p>当尝试对租户执行隔离模式变更（如从 COLUMN 切换到 DATABASE），
 * 但该操作未被授权或当前不允许时抛出。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class TenantModeMigrationException extends RuntimeException {

    private final Long tenantId;

    public TenantModeMigrationException(Long tenantId, String message) {
        super(message);
        this.tenantId = tenantId;
    }

}
