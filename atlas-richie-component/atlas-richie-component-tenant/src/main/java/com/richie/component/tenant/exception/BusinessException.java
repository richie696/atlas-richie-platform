package com.richie.component.tenant.exception;

import lombok.Getter;

/**
 * 多租户业务异常。
 *
 * <p>通用的租户业务逻辑异常，如租户未绑定到当前上下文、
 * 非法租户 ID、管理接口权限不足等。</p>
 *
 * @author richie696
 * @since 2.0
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        super(message);
        this.code = "TENANT_BUSINESS_ERROR";
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = "TENANT_BUSINESS_ERROR";
    }

}
