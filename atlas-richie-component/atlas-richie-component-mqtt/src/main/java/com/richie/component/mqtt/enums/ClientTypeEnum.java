package com.richie.component.mqtt.enums;

/**
 * 客户端类型枚举
 * <p>
 * 用于区分MQTT客户端是服务端还是客户端。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-30 23:18:54
 */
public enum ClientTypeEnum {

    /**
     * 服务端类型
     * <p>
     * 用于标识MQTT服务端实例。
     */
    SERVER,

    /**
     * 客户端类型
     * <p>
     * 用于标识MQTT客户端实例。
     */
    CLIENT
}
