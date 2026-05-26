package com.richie.component.mqtt.generator;

/**
 * MQTT客户端设备ID生成器
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-11 23:37:25
 */
public interface IMqttClientDeviceIdGenerator {

    /**
     * 生成MQTT客户端ID的方法
     *
     * @return 返回客户端ID
     */
    String generateDeviceId();

    /**
     * 自定义设备ID段的方法
     *
     * @param deviceId 设备ID段
     */
    void setDeviceId(String deviceId);
}
