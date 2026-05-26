package com.richie.component.mqtt.client;

import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.filter.handler.MessageHandler;
import com.richie.component.mqtt.generator.IMqttClientDeviceIdGenerator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 默认的MQTT客户端实现类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-13 14:32:52
 */
@Component
@Primary
@ConditionalOnMissingBean(MqttClientApi.class)
public final class DefaultMqttClient extends HiveMqMqttClient implements MqttClientApi {

    /**
     * 构造默认MQTT客户端
     *
     * @param properties MQTT客户端配置属性
     * @param deviceIdGenerator 设备ID生成器
     * @param messageHandler 消息处理器
     * @param networkQualityManager 网络质量管理器
     */
    public DefaultMqttClient(MqttClientProperties properties, IMqttClientDeviceIdGenerator deviceIdGenerator, @Qualifier("hiveMqMessageHandler") MessageHandler<Mqtt5Publish> messageHandler, NetworkQualityManager networkQualityManager) {
        super(properties, deviceIdGenerator, messageHandler, networkQualityManager);
    }

}
