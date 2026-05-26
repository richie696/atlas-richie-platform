package com.richie.component.storage.exception;

/**
 * 对象存储异常
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-06 11:17:52
 */
public class StorageException extends Exception {

    /** 默认构造 */
    public StorageException() {
    }

    /**
     * @param message 错误信息
     */
    public StorageException(String message) {
        super(message);
    }

    /**
     * @param message 错误信息
     * @param cause   原因
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param cause 原因
     */
    public StorageException(Throwable cause) {
        super(cause);
    }

    /**
     * @param message            错误信息
     * @param cause              原因
     * @param enableSuppression  是否启用抑制
     * @param writableStackTrace 是否写栈
     */
    public StorageException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
