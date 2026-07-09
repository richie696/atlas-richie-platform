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
