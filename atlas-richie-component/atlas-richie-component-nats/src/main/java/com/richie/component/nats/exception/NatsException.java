package com.richie.component.nats.exception;

/**
 * NATS 组件统一异常基类
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsException extends RuntimeException {

    public NatsException(String message) {
        super(message);
    }

    public NatsException(String message, Throwable cause) {
        super(message, cause);
    }
}
