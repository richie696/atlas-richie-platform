package com.richie.component.mongodb.exception;

/**
 * Exception thrown when a MongoDB transaction error occurs.
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
