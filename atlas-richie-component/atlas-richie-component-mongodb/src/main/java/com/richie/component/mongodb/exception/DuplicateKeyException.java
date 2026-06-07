package com.richie.component.mongodb.exception;

/**
 * 插入时发生重复键冲突时抛出的异常。
 *
 * @author richie696
 * @since 1.x
 */
public class DuplicateKeyException extends MongodbException {

    public DuplicateKeyException(String msg) {
        super(msg);
    }

    public DuplicateKeyException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * 将 MongoDB DuplicateKeyException 包装为当前类型。
     *
     * @param e 原始 MongoDB 异常
     * @return 包装了原始异常的新 DuplicateKeyException
     */
    public static DuplicateKeyException wrap(com.mongodb.DuplicateKeyException e) {
        return new DuplicateKeyException("Duplicate key: " + e.getMessage(), e);
    }
}
