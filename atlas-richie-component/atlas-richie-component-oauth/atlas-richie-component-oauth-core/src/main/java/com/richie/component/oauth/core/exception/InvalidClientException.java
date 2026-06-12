package com.richie.component.oauth.core.exception;

import com.richie.contract.exception.BusinessException;

/**
 * 客户端认证失败异常
 *
 * @author richie696
 * @since 2026-06-12
 */
public class InvalidClientException extends BusinessException {

    public InvalidClientException(String clientId) {
        super("invalid_client", "客户端认证失败: %s".formatted(clientId));
    }

    public InvalidClientException(String errorCode, String message) {
        super(errorCode, message);
    }
}
