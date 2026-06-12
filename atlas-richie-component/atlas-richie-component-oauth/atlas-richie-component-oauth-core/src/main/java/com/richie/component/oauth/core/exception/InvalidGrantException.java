package com.richie.component.oauth.core.exception;

import com.richie.contract.exception.BusinessException;

/**
 * Grant Type 无效异常
 *
 * @author richie696
 * @since 2026-06-12
 */
public class InvalidGrantException extends BusinessException {

    public InvalidGrantException(String message) {
        super("invalid_grant", message);
    }
}
