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

import com.richie.component.mqtt.beans.ConsumerListener;
import com.richie.component.mqtt.beans.ConsumerMessage;
import com.richie.component.mqtt.beans.MqttServerInfo;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.enums.QosEnum;
import jakarta.annotation.Nonnull;

import java.util.Set;
import java.util.function.Consumer;

/**
 * MQTT客户端接口
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-09 14:48:37
 */
public interface MqttClientApi {

    /**
     * 下发消息的方法
     * <p style="color: red">（注：本接口不会保留最新下发的消息内容，即：retained = false）
     *
     * @param topic 下发消息的主题
     * @param value 下发的内容
     */
    void doPublish(String topic, byte[] value);

    /**
     * 下发消息的方法（指定 QoS）
     * <p style="color: red">（注：本接口不会保留最新下发的消息内容，即：retained = false）
     *
     * @param topic 下发消息的主题
     * @param qos   消息服务质量，覆盖全局配置
     * @param value 下发的内容
     */
    void doPublish(String topic, QosEnum qos, byte[] value);

    /**
     * 下发消息的方法
     *
     * @param topic    下发消息的主题
     * @param value    下发的内容
     * @param retained 是否在服务器中保留本条消息
     *                 <p style="color:red">（注：服务器只会保留最新下发的1条消息在内存或磁盘文件中，当有新消息进行发送时会被最新的消息覆盖）
     */
    void doPublish(String topic, byte[] value, boolean retained);

    /**
     * 下发消息的方法（指定 QoS）
     *
     * @param topic    下发消息的主题
     * @param qos      消息服务质量，覆盖全局配置
     * @param value    下发的内容
     * @param retained 是否在服务器中保留本条消息
     *                 <p style="color:red">（注：服务器只会保留最新下发的1条消息在内存或磁盘文件中，当有新消息进行发送时会被最新的消息覆盖）
     */
    void doPublish(String topic, QosEnum qos, byte[] value, boolean retained);

    /**
     * 获取当前 MQTT 客户端 ID 的方法
     *
     * @return 返回 MQTT 客户端 ID
     */
    String getClientId();

    /**
     * 获取分组ID的方法
     *
     * @return 返回分组ID
     */
    String getGroupId();

    /**
     * 获取根主题的方法
     * @return 返回根主题
     */
    String getParentTopic();

    /**
     * 注册消费者监听事件的方法
     *
     * @param topic    绑定的主题
     * @param callback 消费者监听事件
     */
    void registerConsumer(@Nonnull String topic, @Nonnull Consumer<ConsumerMessage> callback);

    /**
     * 注册消费者监听事件的方法（指定订阅 QoS）
     * <p>
     * 使用指定的 QoS 订阅该主题，覆盖全局配置中的默认 QoS。
     *
     * @param topic    绑定的主题
     * @param qos      订阅服务质量，覆盖全局配置
     * @param callback 消费者监听事件
     */
    void registerConsumer(@Nonnull String topic, @Nonnull QosEnum qos, @Nonnull Consumer<ConsumerMessage> callback);

    /**
     * 解绑对应主题监听事件的方法
     *
     * @param topic 需要解绑的主题
     */
    void unregisterConsumer(@Nonnull String topic);

    /**
     * 注册共享订阅消费者
     * <p>
     * 共享订阅的物理订阅 topic 形如：$share/{groupId}/{businessTopic}
     * 缓存 key 使用完整的共享订阅 topic，便于收到消息时进行通配符匹配。
     * <p>
     * 必须传入完整的共享订阅 topic（包含 $share/{groupId} 前缀），
     * 方法内部会校验格式。
     * <p>
     * 注意：MQTT 3.1.1 不支持共享订阅（Shared Subscriptions 是 MQTT 5.0 的特性），
     * 如果使用 MQTT 3.1.1 客户端调用此方法会抛出 {@link UnsupportedOperationException}。
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic，如：$share/GID_AGENT_DEVICE/device/+/status）
     * @param callback    业务回调函数
     * @throws IllegalArgumentException     如果 topic 格式不正确
     * @throws UnsupportedOperationException 如果使用 MQTT 3.1.1 客户端
     */
    void registerSharedConsumer(@Nonnull String sharedTopic, @Nonnull Consumer<ConsumerMessage> callback);

    /**
     * 注销共享订阅消费者
     * <p>
     * 必须传入完整的共享订阅 topic（包含 $share/{groupId} 前缀），
     * 方法内部会校验格式。
     * <p>
     * 注意：MQTT 3.1.1 不支持共享订阅（Shared Subscriptions 是 MQTT 5.0 的特性），
     * 如果使用 MQTT 3.1.1 客户端调用此方法会抛出 {@link UnsupportedOperationException}。
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic，如：$share/GID_AGENT_DEVICE/device/+/status）
     * @throws IllegalArgumentException     如果 topic 格式不正确
     * @throws UnsupportedOperationException 如果使用 MQTT 3.1.1 客户端
     */
    void unregisterSharedConsumer(@Nonnull String sharedTopic);

    /**
     * 批量注册消费者监听事件的方法
     *
     * @param listeners 订阅的主题监听列表
     */
    void registerConsumers(@Nonnull Set<ConsumerListener> listeners);

    /**
     * 批量解绑对应主题监听事件的方法
     *
     * @param topics 解绑的主题列表
     */
    void unregisterConsumers(@Nonnull String... topics);

    /**
     * 批量解绑对应主题监听事件的方法
     *
     * @param topics 解绑的主题集合
     */
    void unregisterConsumers(@Nonnull Set<String> topics);

    /**
     * 初始化MQTT客户端的方法
     *
     * @param serverInfo MQTT 服务器信息
     */
    void initialClient(@Nonnull MqttServerInfo serverInfo);

    /**
     * 初始化MQTT客户端的方法
     *
     * @param serverInfo MQTT 服务器信息
     * @param enable 是否启动MQTT
     */
    void initialClient(@Nonnull MqttServerInfo serverInfo, boolean enable);

    /**
     * 更改连接的MQTT服务器地址的方法
     *
     * @param networkType 网络类型
     * @param host        自定义主机地址
     * @param port        自定义主机端口
     */
    void changeServer(@Nonnull NetworkTypeEnum networkType, @Nonnull String host, int port);

    /**
     * 切换已有公网或VPC内网服务器地址的方法
     *
     * @param networkType 网络类型
     */
    void changeServer(@Nonnull NetworkTypeEnum networkType);

    /**
     * 切换已有公网或VPC内网服务器地址的方法
     *
     */
    void changeServer();

    /**
     * 断开连接的方法
     */
    void disconnect();

    /**
     * 销毁当前 MQTT 客户端的方法
     */
    void destroy();

    /**
     * 重新初始化新的 MQTT 客户端的方法
     * <p style="color: red">（注意：本方法仅用于客户端无法连接一类的异常时，
     * 结合业务场景手工重新初始化连接的情况下使用，其他时间调用可能会造成
     * 数据丢失的问题，使用时请小心。）
     */
    void reinitialize();

}
