package com.richie.component.mqtt.service;


import com.richie.component.mqtt.beans.ConsumerMessage;

/**
 * MQTT 演示业务服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-10-02 10:39:23
 */
public interface MqttDemoService {

    void handleMessage(ConsumerMessage message);

}
