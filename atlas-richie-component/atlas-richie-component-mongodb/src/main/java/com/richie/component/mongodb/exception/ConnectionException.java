package com.richie.component.mongodb.exception;

/**
 * MongoDB 连接错误时抛出的异常。
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
