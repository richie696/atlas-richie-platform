package com.richie.component.mqtt.exceptions;

/**
 * MQTT配置错误异常
 * <p>
 * 用于表示MQTT配置相关的错误情况。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-12
 */
public class MqttConfigErrorException extends RuntimeException {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = -5220142262364073456L;

    /**
     * 构造一个空的MQTT配置错误异常
     */
    public MqttConfigErrorException() {
        super();
    }

    /**
     * 构造一个带完整信息的MQTT配置错误异常
     *
     * @param message            错误消息
     * @param cause              异常原因
     * @param enableSuppression  是否启用抑制
     * @param writableStackTrace 是否可写堆栈跟踪
     */
    public MqttConfigErrorException(String message, Throwable cause, boolean enableSuppression,
                                    boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * 构造一个带错误消息和原因的MQTT配置错误异常
     *
     * @param message 错误消息
     * @param cause   异常原因
     */
    public MqttConfigErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造一个带错误消息的MQTT配置错误异常
     *
     * @param message 错误消息
     */
    public MqttConfigErrorException(String message) {
        super(message);
    }

    /**
     * 构造一个带原因的MQTT配置错误异常
     *
     * @param cause 异常原因
     */
    public MqttConfigErrorException(Throwable cause) {
        super(cause);
    }

}
