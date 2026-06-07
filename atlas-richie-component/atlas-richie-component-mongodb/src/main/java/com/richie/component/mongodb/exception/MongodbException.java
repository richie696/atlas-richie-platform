package com.richie.component.mongodb.exception;

/**
 * Base runtime exception for MongoDB operations.
 *
 * @author richie696
 * @since 1.x
 */
public class MongodbException extends RuntimeException {

    public MongodbException(String msg) {
        super(msg);
    }

    public MongodbException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
