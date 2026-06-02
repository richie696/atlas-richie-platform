package com.richie.context.utils.security;

/**
 * 加密/解密操作异常
 * <p>
 * 由 {@link CryptoUtils} 统一包装底层异常后抛出，
 * 调用方无需捕获底层 JCA/BouncyCastle 的受检异常。
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
