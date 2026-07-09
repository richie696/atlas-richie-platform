/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.exceptions;

/**
 * MQTT客户端连接异常
 * <p>
 * 用于表示MQTT客户端连接过程中的异常情况。
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-12
 */
public class MqttClientConnectionException extends RuntimeException {
    /**
     * 构造一个空的MQTT客户端连接异常
     */
    public MqttClientConnectionException() {
        super();
    }

    /**
     * 构造一个带完整信息的MQTT客户端连接异常
     *
     * @param message            错误消息
     * @param cause              异常原因
     * @param enableSuppression  是否启用抑制
     * @param writableStackTrace 是否可写堆栈跟踪
     */
    public MqttClientConnectionException(String message, Throwable cause, boolean enableSuppression,
                                         boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * 构造一个带错误消息和原因的MQTT客户端连接异常
     *
     * @param message 错误消息
     * @param cause   异常原因
     */
    public MqttClientConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 构造一个带错误消息的MQTT客户端连接异常
     *
     * @param message 错误消息
     */
    public MqttClientConnectionException(String message) {
        super(message);
    }

    /**
     * 构造一个带原因的MQTT客户端连接异常
     *
     * @param cause 异常原因
     */
    public MqttClientConnectionException(Throwable cause) {
        super(cause);
    }

}
