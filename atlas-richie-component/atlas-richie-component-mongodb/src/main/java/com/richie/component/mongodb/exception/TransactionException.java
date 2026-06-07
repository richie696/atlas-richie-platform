package com.richie.component.mongodb.exception;

/**
 * MongoDB 事务错误时抛出的异常。
 *
 * @author richie696
 * @since 1.x
 */
public class TransactionException extends MongodbException {

    public TransactionException(String msg) {
        super(msg);
    }

    public TransactionException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
