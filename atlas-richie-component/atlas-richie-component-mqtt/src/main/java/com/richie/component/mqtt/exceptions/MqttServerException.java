package com.richie.component.mqtt.exceptions;

/**
 * MQTT服务器异常
 * <p>
 * 用于表示MQTT服务器相关的异常情况。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-12 13:41:26
 */
public class MqttServerException extends Exception {

    /**
     * 构造一个空的MQTT服务器异常
     */
    public MqttServerException() {
    }

    /**
     * 构造一个带错误消息的MQTT服务器异常
     *
     * @param message 错误消息
     */
    public MqttServerException(String message) {
        super(message);
    }

    /**
     * 构造一个带错误消息和原因的MQTT服务器异常
     *
     * @param message 错误消息
     * @param cause   异常原因
     */
    public MqttServerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造一个带原因的MQTT服务器异常
     *
     * @param cause 异常原因
     */
    public MqttServerException(Throwable cause) {
        super(cause);
    }

    /**
     * 构造一个带完整信息的MQTT服务器异常
     *
     * @param message            错误消息
     * @param cause              异常原因
     * @param enableSuppression  是否启用抑制
     * @param writableStackTrace 是否可写堆栈跟踪
     */
    public MqttServerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
