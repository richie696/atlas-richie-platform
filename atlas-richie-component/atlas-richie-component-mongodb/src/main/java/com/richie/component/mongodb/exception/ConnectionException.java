package com.richie.component.mongodb.exception;

/**
 * Exception thrown when a MongoDB connection error occurs.
 *
 * @author richie696
 * @since 1.x
 */
public class ConnectionException extends MongodbException {

    public ConnectionException(String msg) {
        super(msg);
    }

    public ConnectionException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
