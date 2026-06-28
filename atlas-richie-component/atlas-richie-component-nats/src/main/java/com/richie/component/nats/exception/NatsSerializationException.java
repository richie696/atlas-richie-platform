package com.richie.component.nats.exception;

/**
 * NATS 消息序列化/反序列化异常
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsSerializationException extends NatsException {

    public NatsSerializationException(String message) {
        super(message);
    }

    public NatsSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
