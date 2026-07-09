/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.client;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.mqtt.beans.WillMessage;
import com.richie.component.mqtt.config.Mqtt5Config;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperty;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * MQTT连接配置构建器
 * <p>
 * 负责构建MQTT 5.0连接选项，包括认证、遗嘱消息、用户属性等
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Slf4j
public class ConnectionConfigBuilder {

    /**
     * MQTT客户端配置
     */
    private final MqttClientProperties properties;

    /**
     * 客户端ID
     */
    private final String clientId;

    /**
     * 网络类型
     */
    private final NetworkTypeEnum networkType;

    /**
     * 连接时长计时器
     */
    @Setter
    private long connectionStartTime;

    ConnectionConfigBuilder(MqttClientProperties properties, String clientId, NetworkTypeEnum networkType) {
        this.properties = properties;
        this.clientId = clientId;
        this.networkType = networkType;
        // 不在构造函数中设置连接开始时间，由外部调用者设置
    }

    /**
     * 构建MQTT 5.0连接选项
     *
     * @return MQTT 5.0连接选项
     */
    public Mqtt5Connect buildMqtt5Connect() {
        // 获取MQTT服务器密码
        var password = properties.getServer().getPassword();
        if (password == null) {
            throw new IllegalArgumentException("MQTT 密码不能为空");
        }

        // 获取MQTT 5.0配置
        var mqtt5Config = properties.getMqtt5();

        // 构建基础连接选项
        Mqtt5ConnectBuilder builder = Mqtt5Connect.builder()
                .simpleAuth()
                .username(properties.getServer().getUsername())                         // 设置用户名
                .password(password.getBytes(StandardCharsets.UTF_8))                    // 设置密码
                .applySimpleAuth()                                                      // 应用认证配置
                .cleanStart(!mqtt5Config.isKeepSession())                               // 使用MQTT 5.0配置的会话保持设置（取反，因为cleanStart与keepSession相反）
                .keepAlive(properties.getFastRecovery().getKeepAliveInterval())        // 使用快速恢复配置的心跳间隔
                .sessionExpiryInterval(mqtt5Config.getSessionExpiryInterval());         // 使用配置的会话过期时间

        // 配置遗嘱消息（如果启用）
        if (mqtt5Config.isEnableWillMessage()) {
            configureWillMessage(builder, mqtt5Config);
        }

        // 配置用户属性（如果启用）
        if (mqtt5Config.isEnableUserProperties()) {
            return builder.userProperties(buildUserProperties(mqtt5Config)).build();
        }

        return builder.build();
    }

    /**
     * 配置遗嘱消息
     *
     * @param builder 连接构建器
     * @param mqtt5Config MQTT 5.0配置
     */
    private void configureWillMessage(Mqtt5ConnectBuilder builder, Mqtt5Config mqtt5Config) {
        String willTopic = mqtt5Config.getWillTopic().replace("{clientId}", clientId);

        var willMessage = new WillMessage()
                .setStoreId(mqtt5Config.getStoreId())
                .setClientId(clientId)
                .setStatus("offline")
                .setReason(mqtt5Config.getWillMessage())
                .setNetworkType(this.networkType.name())
                .setAppVersion(mqtt5Config.getAppVersion())
                .setConnectionDuration(connectionStartTime);

        builder.willPublish()
                .topic(willTopic)                                                  // 遗嘱消息主题
                .payload(JsonUtils.getInstance().serializeBytes(willMessage))      // 遗嘱消息内容
                .qos(MqttQos.AT_MOST_ONCE)                                         // 遗嘱消息QoS级别
                .retain(false)                                                     // 遗嘱消息保留设置
                .applyWillPublish();                                               // 应用遗嘱消息配置
    }

    /**
     * 构建用户属性
     *
     * @param mqtt5Config MQTT 5.0配置
     * @return MQTT 5.0用户属性
     */
    private Mqtt5UserProperties buildUserProperties(Mqtt5Config mqtt5Config) {
        return Mqtt5UserProperties.builder().addAll(
                Mqtt5UserProperty.of("clientType", mqtt5Config.getClientType()),
                Mqtt5UserProperty.of("version", "5.0"),
                Mqtt5UserProperty.of("clientId", this.clientId),
                Mqtt5UserProperty.of("appVersion", mqtt5Config.getAppVersion()),
                Mqtt5UserProperty.of("storeId", mqtt5Config.getStoreId()),
                Mqtt5UserProperty.of("keepAliveInterval", String.valueOf(properties.getFastRecovery().getKeepAliveInterval())),
                Mqtt5UserProperty.of("networkType", this.networkType.name()),
                Mqtt5UserProperty.of("timestamp", String.valueOf(System.currentTimeMillis()))
        ).build();
    }

}
