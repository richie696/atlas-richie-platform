/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.client;

import com.richie.component.mqtt.beans.*;
import com.richie.component.mqtt.beans.ConnectionStateEvent;
import com.richie.component.mqtt.beans.HeartbeatEvent;
import com.richie.component.mqtt.beans.NetworkQualityEvent;
import com.richie.component.mqtt.beans.SubscriptionResult;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

/**
 * MQTT事件总线内部访问器
 * <p>
 * 该类仅供client包及其子包内的类使用，用于发布MQTT相关事件。
 * 外部使用者只能通过MqttEventBus订阅事件，不能发布事件。
 * <p>
 * <strong>设计原则：</strong>
 * <ul>
 *   <li><strong>封装性</strong>：隐藏事件发布的内部实现</li>
 *   <li><strong>访问控制</strong>：限制事件发布权限到内部组件</li>
 *   <li><strong>职责分离</strong>：事件发布和订阅职责分离</li>
 *   <li><strong>安全性</strong>：防止外部滥用事件发布功能</li>
 * </ul>
 * <p>
 * <strong>使用范围：</strong>
 * <ul>
 *   <li>client包内的所有类</li>
 *   <li>client包的子包内的所有类</li>
 *   <li>其他包内的类无法访问</li>
 * </ul>
 * <p>
 * <strong>注意事项：</strong>
 * <ul>
 *   <li>该类仅供内部组件使用，不要暴露给外部</li>
 *   <li>事件发布应该遵循组件生命周期</li>
 *   <li>发布事件前应确保相关组件已初始化</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-08-13
 */
public sealed class MqttEventPublisher permits AbstractMqttClientApi, ConnectionMonitor {

    /**
     * 发布连接状态事件
     * <p>
     * <strong>使用说明：</strong>
     * 在连接状态发生变化时调用，发布相应的连接状态事件。
     *
     * @param event 连接状态事件对象
     * @return 发布结果，包含成功状态和失败原因
     */
    protected static PublishResult<ConnectionStateEvent> publishConnectionState(ConnectionStateEvent event) {
        return MqttEventBus.publishConnectionState(event);
    }

    /**
     * 发布MQTT消息事件
     * <p>
     * <strong>使用说明：</strong>
     * 在接收到MQTT消息时调用，发布消息事件供订阅者处理。
     *
     * @param message MQTT消息对象
     * @return 发布结果，包含成功状态和失败原因
     */
    protected static PublishResult<Mqtt5Publish> publishMessage(Mqtt5Publish message) {
        return MqttEventBus.publishMessage(message);
    }

    /**
     * 发布心跳事件
     * <p>
     * <strong>使用说明：</strong>
     * 在发送心跳包时调用，发布心跳事件供监控组件使用。
     *
     * @param event 心跳事件对象
     * @return 发布结果，包含成功状态和失败原因
     */
    protected static PublishResult<HeartbeatEvent> publishHeartbeat(HeartbeatEvent event) {
        return MqttEventBus.publishHeartbeat(event);
    }

    /**
     * 发布订阅结果事件
     * <p>
     * <strong>使用说明：</strong>
     * 在订阅操作完成后调用，发布订阅结果事件。
     *
     * @param result 订阅结果对象
     * @return 发布结果，包含成功状态和失败原因
     */
    protected static PublishResult<SubscriptionResult> publishSubscriptionResult(SubscriptionResult result) {
        return MqttEventBus.publishSubscriptionResult(result);
    }

    /**
     * 发布网络质量事件
     * <p>
     * <strong>使用说明：</strong>
     * 在网络质量检测完成后调用，发布网络质量事件。
     *
     * @param event 网络质量事件对象
     * @return 发布结果，包含成功状态和失败原因
     */
    protected static PublishResult<NetworkQualityEvent> publishNetworkQuality(NetworkQualityEvent event) {
        return MqttEventBus.publishNetworkQuality(event);
    }

    /**
     * 广播连接状态
     * <p>
     * <strong>使用说明：</strong>
     * 在连接状态变化时调用，广播简化的连接状态。
     *
     * @param connected 连接状态，true表示已连接，false表示未连接
     * @return 发布结果，包含成功状态和失败原因
     */
    protected static PublishResult<Boolean> publishConnected(boolean connected) {
        return MqttEventBus.broadcastConnected(connected);
    }

    /**
     * 广播网络类型
     * <p>
     * <strong>使用说明：</strong>
     * 在网络类型变化时调用，广播网络类型信息。
     *
     * @param networkType 当前网络类型枚举值
     * @return 发布结果，包含成功状态和失败原因
     */
    protected static PublishResult<NetworkTypeEnum> publishNetworkType(NetworkTypeEnum networkType) {
     return MqttEventBus.broadcastNetworkType(networkType);
    }

    /**
     * 清理所有事件流
     * <p>
     * <strong>使用说明：</strong>
     * 清理所有事件流，释放资源并确保没有残留的事件。
     * 仅在MQTT连接断开或应用程序关闭时调用。
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>清理后所有事件流将不可用</li>
     *   <li>需要重新建立连接才能恢复事件流</li>
     *   <li>清理操作是幂等的，可以多次调用</li>
     *   <li>仅在必要时调用，避免影响正常的事件处理</li>
     * </ul>
     * <p>
     * <strong>调用时机：</strong>
     * <ul>
     *   <li>MQTT连接断开时</li>
     *   <li>应用程序关闭时</li>
     *   <li>连接重置时</li>
     *   <li>资源清理时</li>
     * </ul>
     */
    protected static void clearAllEvents() {
        MqttEventBus.clearAllEvents();
    }
}
