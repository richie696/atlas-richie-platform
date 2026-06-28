package com.richie.component.nats.exception;

/**
 * NATS 连接异常
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsConnectionException extends NatsException {

    public NatsConnectionException(String message) {
        super(message);
    }

    public NatsConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
