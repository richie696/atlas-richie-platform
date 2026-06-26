package com.richie.component.tenant.exception;

import lombok.Getter;

/**
 * 事务内租户切换异常。
 *
 * <p>在同一事务内检测到租户切换时抛出。同一事务必须操作同一租户数据源，
 * 禁止在 {@code @Transactional} 方法内部切换租户上下文。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class TenantSwitchInTransactionException extends RuntimeException {

    private final Long fromTenantId;
    private final Long toTenantId;

    public TenantSwitchInTransactionException(Long fromTenantId, Long toTenantId) {
        super("Cannot switch tenant from " + fromTenantId + " to " + toTenantId + " within a transaction");
        this.fromTenantId = fromTenantId;
        this.toTenantId = toTenantId;
    }

}
