package com.richie.component.oauth.core.exception;

import com.richie.contract.exception.BusinessException;

/**
 * Token 已过期异常
 *
 * @author richie696
 * @since 2026-06-12
 */
public class TokenExpiredException extends BusinessException {

    public TokenExpiredException() {
        super("invalid_token", "Access token 已过期");
    }
}
