package com.richie.component.vector.exceptions;

/**
 * 向量数据库不存在异常
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01 16:18:21
 */
public class VectorStoreNotExistException extends RuntimeException {

    public VectorStoreNotExistException() {
    }

    public VectorStoreNotExistException(String message) {
        super(message);
    }

    public VectorStoreNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public VectorStoreNotExistException(Throwable cause) {
        super(cause);
    }

    public VectorStoreNotExistException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
