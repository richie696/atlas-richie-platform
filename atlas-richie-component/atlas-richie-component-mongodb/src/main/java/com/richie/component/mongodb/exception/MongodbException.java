package com.richie.component.mongodb.exception;

/**
 * MongoDB 操作的基础运行时异常。
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
