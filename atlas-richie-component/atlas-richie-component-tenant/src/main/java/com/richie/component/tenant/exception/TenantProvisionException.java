package com.richie.component.tenant.exception;

import lombok.Getter;

/**
 * 租户开通/配置异常。
 *
 * <p>在租户开通（provision）过程中发生错误时抛出，
 * 如数据源创建失败、Schema 初始化失败等。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class TenantProvisionException extends RuntimeException {

    private final Long tenantId;

    public TenantProvisionException(Long tenantId, String message) {
        super(message);
        this.tenantId = tenantId;
    }

    public TenantProvisionException(Long tenantId, String message, Throwable cause) {
        super(message, cause);
        this.tenantId = tenantId;
    }

}
