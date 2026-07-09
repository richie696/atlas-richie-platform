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

import com.richie.component.mqtt.beans.ConsumerListener;
import com.richie.component.mqtt.beans.ConsumerMessage;
import com.richie.component.mqtt.beans.MqttServerInfo;
import com.richie.component.mqtt.beans.NetworkQualityStats;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.enums.ClientTypeEnum;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.enums.QosEnum;
import com.richie.component.mqtt.exceptions.MqttClientException;
import com.richie.component.mqtt.filter.handler.MessageHandler;
import com.richie.component.mqtt.generator.IMqttClientDeviceIdGenerator;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * HiveMQ MQTT 5.0 客户端实现（组件化架构）
 * <p>
 * 基于HiveMQ客户端库实现的MQTT 5.0客户端，采用组件化架构设计，
 * 支持连接管理、消息处理、心跳监控、网络质量监控等功能。
 * <p>
 * <strong>主要特性：</strong>
 * <ul>
 *   <li><strong>MQTT 5.0协议支持</strong>：完整的MQTT 5.0特性支持</li>
 *   <li><strong>组件化架构</strong>：连接管理、消息管理、心跳管理等独立组件</li>
 *   <li><strong>自动重连机制</strong>：支持网络异常后的自动恢复</li>
 *   <li><strong>网络质量监控</strong>：实时监控网络状况和连接质量</li>
 *   <li><strong>事件驱动设计</strong>：基于事件总线的松耦合架构</li>
 * </ul>
 * <p>
 * <strong>架构组件：</strong>
 * <ul>
 *   <li><strong>连接管理器</strong>：负责MQTT连接的建立、断开、重连</li>
 *   <li><strong>消息管理器</strong>：处理消息的发布、订阅、接收</li>
 *   <li><strong>心跳管理器</strong>：维护连接活跃性</li>
 *   <li><strong>连接监控器</strong>：监控连接状态变化</li>
 *   <li><strong>网络质量管理器</strong>：监控网络质量和性能</li>
 * </ul>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li><strong>物联网应用</strong>：设备与云端的MQTT通信</li>
 *   <li><strong>实时消息系统</strong>：需要低延迟的消息传递</li>
 *   <li><strong>移动应用</strong>：支持网络切换和重连</li>
 *   <li><strong>企业集成</strong>：系统间的消息通信</li>
 * </ul>
 *
 * @author richie696
 * @version 2.3
 * @since 2025-08-15
 */
@Slf4j
@Component("mqtt_5")
@ConditionalOnProperty(prefix = "platform.component.mqtt", name = "mqtt-version", havingValue = "mqtt_5_0", matchIfMissing = true)
public class HiveMqMqttClient extends AbstractMqttClientApi implements MqttClientApi {

    // ==================== 实例变量 ====================

    /**
     * 客户端ID
     * <p>
     * 用于标识MQTT客户端的唯一标识符，由设备ID生成器生成。
     * 在连接建立时用于服务器端的客户端识别和会话管理。
     */
    private String clientId;

    /**
     * 连接管理器
     * <p>
     * 负责MQTT连接的建立、断开、重连等连接相关操作。
     * 管理连接的生命周期，处理网络切换和异常恢复。
     */
    private ConnectionManager connectionManager;

    /**
     * 消息管理器
     * <p>
     * 负责MQTT消息的发布、订阅、接收等消息相关操作。
     * 管理消息的生命周期，处理消息的QoS、重试等。
     */
    private MqttMessageManager messageManager;

    /**
     * 心跳管理器
     * <p>
     * 负责发送心跳消息，维护MQTT连接的活跃性。
     * 定期发送PING消息，检测连接状态。
     */
    private HeartbeatManager heartbeatManager;

    /**
     * 连接监控器
     * <p>
     * 监控MQTT连接状态变化，检测连接异常。
     * 在连接异常时触发相应的事件和恢复机制。
     */
    private ConnectionMonitor connectionMonitor;

    /**
     * 消息处理器
     * <p>
     * 处理接收到的MQTT消息，执行消息过滤和业务逻辑。
     * 支持消息的预处理、过滤、转换等操作。
     */
    private final MessageHandler<Mqtt5Publish> messageHandler;

    /**
     * 网络质量管理器
     * <p>
     * 监控网络质量，检测网络延迟、丢包率等指标。
     * 提供网络质量统计信息，支持网络切换决策。
     */
    private final NetworkQualityManager networkQualityManager;


    /**
     * 连接锁，防止重复连接
     * <p>
     * 使用原子布尔值确保连接过程的线程安全，
     * 防止多个线程同时发起连接请求。
     */
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    /**
     * 标记是否已订阅 MQTT 消息事件流，防止重复订阅
     */
    private final AtomicBoolean messageFlowSubscribed = new AtomicBoolean(false);


    // ==================== 构造函数 ====================

    /**
     * 构造HiveMQ MQTT 5.0客户端
     * <p>
     * <strong>功能说明：</strong>
     * 创建MQTT客户端实例，初始化必要的组件和后台服务。
     * 根据配置决定是否立即初始化MQTT服务。
     * <p>
     * <strong>初始化流程：</strong>
     * <ol>
     *   <li>保存依赖注入的组件</li>
     *   <li>验证MQTT服务是否启用</li>
     *   <li>验证服务器配置信息</li>
     *   <li>初始化网络类型广播服务</li>
     *   <li>根据配置决定是否立即初始化MQTT服务</li>
     * </ol>
     * <p>
     * <strong>参数要求：</strong>
     * <ul>
     *   <li>properties：MQTT客户端配置，不能为null</li>
     *   <li>deviceIdGenerator：设备ID生成器，不能为null</li>
     *   <li>messageHandler：消息处理器，不能为null</li>
     *   <li>networkQualityManager：网络质量管理器，不能为null</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果MQTT服务被禁用，构造函数会提前返回</li>
     *   <li>只有服务端或要求立即初始化的客户端才会立即初始化MQTT服务</li>
     *   <li>异常情况下会抛出MqttClientException</li>
     * </ul>
     *
     * @param properties            MQTT客户端配置
     * @param deviceIdGenerator     设备ID生成器
     * @param messageHandler        消息处理器
     * @param networkQualityManager 网络质量管理器
     * @throws MqttClientException 当配置信息缺失时
     */
    public HiveMqMqttClient(MqttClientProperties properties, IMqttClientDeviceIdGenerator deviceIdGenerator,
                            @Qualifier("hiveMqMessageHandler") MessageHandler<Mqtt5Publish> messageHandler, NetworkQualityManager networkQualityManager) {
        this.messageHandler = messageHandler;
        this.networkQualityManager = networkQualityManager;
        this.properties = properties;
        this.deviceIdGenerator = deviceIdGenerator;
        // 设置默认网络类型
        this.networkType = properties.getServer().getDefaultNetworkType();

        // 如果MQTT服务被禁用，直接返回
        if (!properties.getEnable()) {
            return;
        }

        // 验证服务器配置信息
        if (Objects.isNull(properties.getServer())) {
            throw new MqttClientException("MQTT 客户端初始化异常，缺少必要的配置信息。");
        }

        // 初始化广播服务
        initBroadcast();

        // 如果当前初始化的客户端是云端服务端或者门店客户端要求立即初始化则进行MQTT服务的完整初始化
        if (this.properties.getClientType() == ClientTypeEnum.SERVER || properties.getInitClient()) {
            // 生成设备客户端ID
            this.clientId = deviceIdGenerator.generateDeviceId();
            // 初始化MQTT服务
            initService();
        }
    }

    // ==================== 私有方法 - 广播服务 ====================

    /**
     * 初始化网络类型广播服务
     * <p>
     * <strong>功能说明：</strong>
     * 初始化网络类型广播服务，启动后台线程定期广播当前网络类型。
     * 该服务使用虚拟线程，支持优雅关闭和资源清理。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>设置默认网络类型</li>
     *   <li>启动虚拟线程进行广播</li>
     *   <li>设置广播循环和异常处理</li>
     * </ol>
     * <p>
     * <strong>广播内容：</strong>
     * <ul>
     *   <li><strong>网络类型</strong>：当前使用的网络类型（公网/VPC）</li>
     *   <li><strong>广播频率</strong>：每秒广播一次</li>
     *   <li><strong>广播方式</strong>：通过MqttEventBusInternal.broadcastNetworkType()</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>使用虚拟线程提高性能</li>
     *   <li>支持线程中断处理</li>
     *   <li>异常情况下会记录错误日志</li>
     *   <li>广播服务在连接管理器存在时运行</li>
     * </ul>
     */
    private void initBroadcast() {
        // 使用虚拟线程启动广播
        Thread.startVirtualThread(() -> {
            try {
                while (connectionManager != null && connectionManager.isBroadcast()) {
                    PublishResult<NetworkTypeEnum> publishResult = MqttEventPublisher.publishNetworkType(networkType);
                    if (publishResult.isFailed()) {
                        log.warn("网络类型广播失败: {}, 原因: {}", networkType, publishResult.getFailureReason());
                        handleNetworkTypePublishFailure(networkType, publishResult);
                    }
                    TimeUnit.SECONDS.sleep(1L); // 每秒广播一次
                }
            } catch (InterruptedException e) {
                log.debug("网络类型广播线程被中断");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("网络类型广播异常: {}", e.getMessage(), e);
            } finally {
                log.debug("网络类型广播线程结束");
            }
        });

        log.debug("网络类型广播线程已启动");
    }

    // ==================== 公共方法 - 消息发布 ====================

    /**
     * 发布消息到指定主题
     * <p>
     * <strong>功能说明：</strong>
     * 将消息发布到指定的MQTT主题，使用非保留消息模式。
     * 在发布前会检查连接状态，确保连接就绪。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查MQTT连接状态</li>
     *   <li>验证连接就绪性</li>
     *   <li>调用消息管理器发送消息</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果连接未就绪，会记录警告日志并返回</li>
     *   <li>消息为非保留消息，不会在服务器端持久化</li>
     *   <li>使用默认的QoS级别发送消息</li>
     * </ul>
     *
     * @param topic 目标主题
     * @param value 消息内容
     */
    @Override
    public void doPublish(String topic, byte[] value) {
        if (isConnectionNotReady()) {
            log.warn("MQTT连接未就绪，无法发布消息到主题: {}", topic);
            return;
        }
        messageManager.sendMessage(topic, value, false);
    }

    /**
     * 发布消息到指定主题（支持保留消息）
     * <p>
     * <strong>功能说明：</strong>
     * 将消息发布到指定的MQTT主题，支持保留消息模式。
     * 在发布前会检查连接状态，确保连接就绪。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查MQTT连接状态</li>
     *   <li>验证连接就绪性</li>
     *   <li>调用消息管理器发送消息</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果连接未就绪，会记录警告日志并返回</li>
     *   <li>retained参数控制是否为保留消息</li>
     *   <li>保留消息会在服务器端持久化，新订阅者会收到</li>
     *   <li>使用默认的QoS级别发送消息</li>
     * </ul>
     *
     * @param topic    目标主题
     * @param value    消息内容
     * @param retained 是否为保留消息
     */
    @Override
    public void doPublish(String topic, byte[] value, boolean retained) {
        if (isConnectionNotReady()) {
            log.warn("MQTT连接未就绪，无法发布消息到主题: {}", topic);
            return;
        }
        messageManager.sendMessage(topic, value, retained);
    }

    @Override
    public void doPublish(String topic, QosEnum qos, byte[] value) {
        if (isConnectionNotReady()) {
            log.warn("MQTT连接未就绪，无法发布消息到主题: {}", topic);
            return;
        }
        messageManager.sendMessage(topic, value, false, qos);
    }

    @Override
    public void doPublish(String topic, QosEnum qos, byte[] value, boolean retained) {
        if (isConnectionNotReady()) {
            log.warn("MQTT连接未就绪，无法发布消息到主题: {}", topic);
            return;
        }
        messageManager.sendMessage(topic, value, retained, qos);
    }

    // ==================== 公共方法 - 生命周期管理 ====================

    /**
     * 销毁MQTT客户端并清理所有资源
     * <p>
     * <strong>功能说明：</strong>
     * 完全销毁MQTT客户端，包括停止网络类型广播、重置连接标志、
     * 执行完整的断开和清理操作。这是客户端的最终清理方法。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>停止网络类型广播</li>
     *   <li>重置连接标志</li>
     *   <li>调用通用的断开和清理逻辑</li>
     *   <li>记录销毁完成日志</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>应用程序完全关闭</li>
     *   <li>组件彻底销毁</li>
     *   <li>资源完全释放</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>该方法会完全清理所有资源，调用后客户端将不可用</li>
     *   <li>需要重新初始化才能恢复功能</li>
     *   <li>该方法会调用disconnect()的通用逻辑</li>
     * </ul>
     */
    @Override
    public void destroy() {
        log.info("开始执行MQTT客户端销毁操作，客户端ID: {}", clientId);

        // 停止网络类型广播
        if (connectionManager != null) {
            connectionManager.setBroadcast(false);
        }
        log.debug("网络类型广播已停止");

        // 重置连接标志
        connecting.set(false);

        // 调用通用的断开和清理方法
        disconnectAndCleanup();

        log.info("MQTT客户端资源清理完成，客户端ID: {}", clientId);
    }

    /**
     * 重新初始化MQTT服务
     * <p>
     * <strong>功能说明：</strong>
     * 重新初始化MQTT服务的所有组件，包括连接管理器、消息管理器、
     * 心跳管理器、连接监控器等。用于服务重启或配置变更后的重新初始化。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>销毁现有服务</li>
     *   <li>重新初始化所有组件</li>
     *   <li>启动监控和心跳服务</li>
     *   <li>启动网络质量监控</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>服务重启后的重新初始化</li>
     *   <li>配置变更后的服务更新</li>
     *   <li>故障恢复后的服务重建</li>
     * </ul>
     */
    @Override
    public void reinitialize() {
        initService();
    }

    // ==================== 受保护方法 - 主题订阅 ====================

    /**
     * 订阅指定主题
     * <p>
     * <strong>功能说明：</strong>
     * 订阅指定的MQTT主题，开始接收该主题的消息。
     * 订阅操作委托给消息管理器执行。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查消息管理器是否已初始化</li>
     *   <li>调用消息管理器的订阅方法</li>
     *   <li>记录订阅结果日志</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果消息管理器未初始化，会记录警告日志</li>
     *   <li>订阅操作是异步的，不会阻塞当前线程</li>
     *   <li>订阅成功后，消息会通过消息处理器处理</li>
     * </ul>
     *
     * @param topic 要订阅的主题
     */
    @Override
    protected void doSubscribe(String topic) {
        if (messageManager != null) {
            QosEnum qos = SUBSCRIBE_QOS_CACHE.get(topic);
            messageManager.doSubscribe(topic, qos);
        } else {
            log.warn("消息管理器未初始化，无法订阅主题");
        }
    }

    /**
     * 取消订阅指定主题
     * <p>
     * <strong>功能说明：</strong>
     * 取消订阅指定的MQTT主题，停止接收该主题的消息。
     * 取消订阅操作委托给消息管理器执行。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查消息管理器是否已初始化</li>
     *   <li>调用消息管理器的取消订阅方法</li>
     *   <li>记录取消订阅结果日志</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果消息管理器未初始化，会记录警告日志</li>
     *   <li>取消订阅操作是异步的，不会阻塞当前线程</li>
     *   <li>取消订阅后，不会再收到该主题的消息</li>
     * </ul>
     *
     * @param topic 要取消订阅的主题
     */
    @Override
    protected void doUnsubscribe(String topic) {
        if (messageManager != null) {
            messageManager.doUnsubscribe(topic);
        } else {
            log.warn("消息管理器未初始化，无法取消订阅主题");
        }
    }

    // ==================== 公共方法 - 消费者管理 ====================

    /**
     * 注册多个消费者监听器
     * <p>
     * <strong>功能说明：</strong>
     * 批量注册多个消费者监听器，每个监听器包含主题和回调函数。
     * 注册后，当收到对应主题的消息时会自动调用相应的回调函数。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>遍历所有监听器</li>
     *   <li>提取每个监听器的主题和回调</li>
     *   <li>调用registerConsumer方法注册单个消费者</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>listeners参数不能为null</li>
     *   <li>每个监听器必须包含有效的主题和回调函数</li>
     *   <li>如果某个监听器注册失败，不会影响其他监听器的注册</li>
     * </ul>
     *
     * @param listeners 消费者监听器集合
     */
    @Override
    public void registerConsumers(@Nonnull Set<ConsumerListener> listeners) {
        for (ConsumerListener listener : listeners) {
            registerConsumer(listener.getTopic(), listener.getCallback());
        }
    }

    /**
     * 取消注册多个主题的消费者
     * <p>
     * <strong>功能说明：</strong>
     * 批量取消注册多个主题的消费者，停止接收这些主题的消息。
     * 支持可变参数形式，可以一次取消多个主题。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>遍历所有主题</li>
     *   <li>调用unregisterConsumer方法取消单个主题</li>
     *   <li>记录取消注册结果</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>topics参数不能为null</li>
     *   <li>如果某个主题不存在，会记录警告日志但不会抛出异常</li>
     *   <li>取消注册后，不会再收到这些主题的消息</li>
     * </ul>
     *
     * @param topics 要取消注册的主题数组
     */
    @Override
    public void unregisterConsumers(@Nonnull String... topics) {
        for (var topic : topics) {
            unregisterConsumer(topic);
        }
    }

    /**
     * 取消注册多个主题的消费者
     * <p>
     * <strong>功能说明：</strong>
     * 批量取消注册多个主题的消费者，停止接收这些主题的消息。
     * 支持集合形式，可以一次取消多个主题。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>遍历主题集合</li>
     *   <li>调用unregisterConsumer方法取消单个主题</li>
     *   <li>记录取消注册结果</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>topics参数不能为null</li>
     *   <li>如果某个主题不存在，会记录警告日志但不会抛出异常</li>
     *   <li>取消注册后，不会再收到这些主题的消息</li>
     * </ul>
     *
     * @param topics 要取消注册的主题集合
     */
    @Override
    public void unregisterConsumers(@Nonnull Set<String> topics) {
        for (var topic : topics) {
            unregisterConsumer(topic);
        }
    }

    // ==================== 公共方法 - 客户端初始化 ====================

    /**
     * 初始化MQTT客户端
     * <p>
     * <strong>功能说明：</strong>
     * 使用服务器信息初始化MQTT客户端，配置服务器地址、认证信息、
     * 设备ID等参数，并根据配置决定是否立即启动MQTT服务。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>验证服务器信息有效性</li>
     *   <li>配置公网和VPC服务器地址</li>
     *   <li>设置认证信息（用户名、密码）</li>
     *   <li>配置分组ID和设备ID</li>
     *   <li>生成客户端ID</li>
     *   <li>设置网络类型和服务器类型</li>
     *   <li>根据配置决定是否启动服务</li>
     * </ol>
     * <p>
     * <strong>参数要求：</strong>
     * <ul>
     *   <li>serverInfo：服务器信息，不能为null</li>
     *   <li>服务器信息必须包含至少一个有效的地址配置</li>
     *   <li>认证信息必须完整</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果服务器信息无效，会抛出MqttClientException</li>
     *   <li>只有启用MQTT服务时才会启动服务</li>
     *   <li>支持公网和VPC两种网络环境</li>
     * </ul>
     *
     * @param serverInfo MQTT服务器信息
     * @throws MqttClientException 当服务器信息无效时
     */
    @Override
    public void initialClient(@Nonnull MqttServerInfo serverInfo) {
        if (serverInfo.isInvalid()) {
            throw new MqttClientException("服务器地址信息不能全部为空");
        }

        if (serverInfo.isValidOfPublic()) {
            connectionManager.setPublicServer(serverInfo.getHost(), serverInfo.getPort());
        }

        if (serverInfo.isValidOfVpc()) {
            connectionManager.setVpcServer(serverInfo.getVpcHost(), serverInfo.getVpcPort());
        }

        connectionManager.setAuthorization(serverInfo.getUsername(), serverInfo.getPassword());
        this.properties.setGroupId(serverInfo.getGroupId());
        this.deviceIdGenerator.setDeviceId(serverInfo.getDeviceId());
        this.clientId = this.deviceIdGenerator.generateDeviceId();
        this.networkType = serverInfo.getNetworkType();
        this.properties.setType(serverInfo.getServerType());

        if (properties.getEnable()) {
            initService();
        }
    }

    /**
     * 初始化MQTT客户端（支持启用控制）
     * <p>
     * <strong>功能说明：</strong>
     * 使用服务器信息初始化MQTT客户端，并可以控制是否启用MQTT服务。
     * 该方法是对initialClient(MqttServerInfo)的扩展，增加了服务启用控制。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>设置MQTT服务启用状态</li>
     *   <li>调用基础初始化方法</li>
     *   <li>根据启用状态决定是否启动服务</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>需要动态控制MQTT服务启用状态</li>
     *   <li>配置变更后的服务重启</li>
     *   <li>测试环境下的服务控制</li>
     * </ul>
     *
     * @param serverInfo MQTT服务器信息
     * @param enable     是否启用MQTT服务
     */
    @Override
    public void initialClient(@Nonnull MqttServerInfo serverInfo, boolean enable) {
        properties.setEnable(enable);
        initialClient(serverInfo);
    }

    // ==================== 公共方法 - 服务器配置 ====================

    /**
     * 切换服务器配置（指定地址和端口）
     * <p>
     * <strong>功能说明：</strong>
     * 动态切换MQTT服务器配置，包括网络类型、服务器地址和端口。
     * 该方法会同时更新连接管理器的配置和客户端的网络类型。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>调用连接管理器切换服务器配置</li>
     *   <li>更新客户端的网络类型</li>
     *   <li>记录配置变更日志</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>服务器地址变更时的动态调整</li>
     *   <li>负载均衡场景下的服务器切换</li>
     *   <li>故障转移时的备用服务器配置</li>
     *   <li>测试环境下的服务器配置</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>networkType和host参数不能为null</li>
     *   <li>配置变更不会立即生效，需要重新连接</li>
     *   <li>建议在配置变更后调用reinitialize()方法</li>
     * </ul>
     *
     * @param networkType 目标网络类型
     * @param host        服务器地址
     * @param port        服务器端口
     */
    @Override
    public void changeServer(@Nonnull NetworkTypeEnum networkType, @Nonnull String host, int port) {
        connectionManager.changeServer(networkType, host, port);
        this.networkType = networkType;
    }

    /**
     * 切换服务器配置（使用配置中的地址）
     * <p>
     * <strong>功能说明：</strong>
     * 动态切换MQTT服务器配置，使用配置文件中预设的服务器地址和端口。
     * 该方法会同时更新连接管理器的配置和客户端的网络类型。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>调用连接管理器切换服务器配置</li>
     *   <li>更新客户端的网络类型</li>
     *   <li>记录配置变更日志</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络环境切换（公网/VPC）</li>
     *   <li>配置文件中预设的服务器切换</li>
     *   <li>网络策略调整</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>networkType参数不能为null</li>
     *   <li>使用配置文件中预设的服务器地址</li>
     *   <li>配置变更不会立即生效，需要重新连接</li>
     * </ul>
     *
     * @param networkType 目标网络类型
     */
    @Override
    public void changeServer(@Nonnull NetworkTypeEnum networkType) {
        this.networkType = networkType;
        connectionManager.changeServer(networkType);
    }

    @Override
    public void changeServer() {
        this.networkType = this.networkType.getSwitchNetwork();
        connectionManager.changeServer(this.networkType);
    }

    // ==================== 私有方法 - 服务初始化 ====================

    /**
     * 初始化MQTT服务
     * <p>
     * <strong>功能说明：</strong>
     * 初始化MQTT服务的所有组件，包括连接管理器、消息管理器、
     * 心跳管理器、连接监控器等，并启动相关服务。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>销毁现有服务（如果存在）</li>
     *   <li>初始化各个组件</li>
     *   <li>启动监控和心跳服务</li>
     *   <li>启动网络质量监控</li>
     * </ol>
     * <p>
     * <strong>初始化内容：</strong>
     * <ul>
     *   <li>连接管理器：负责MQTT连接管理</li>
     *   <li>消息管理器：负责消息处理</li>
     *   <li>心跳管理器：负责连接活跃性维护</li>
     *   <li>连接监控器：负责连接状态监控</li>
     *   <li>网络质量监控：负责网络状况监控</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>该方法会先销毁现有服务，确保资源清理</li>
     *   <li>网络质量监控独立于连接状态，持续运行</li>
     *   <li>异常情况下会记录错误日志但不会中断初始化</li>
     * </ul>
     */
    private void initService() {
        // 销毁现有服务
        destroy();

        // 初始化组件
        initComponents();

        // 启动监控和心跳服务
        startServices();

        // 启动网络质量监控（独立于连接状态）
        if (networkQualityManager != null) {
            networkQualityManager.startMonitoring();
            log.info("网络质量监控已启动，将持续监控网络状况");
        }
    }

    // ==================== 私有方法 - 组件管理 ====================

    /**
     * 初始化各个组件
     * <p>
     * <strong>功能说明：</strong>
     * 初始化MQTT客户端的核心组件，包括连接管理器、消息管理器、
     * 心跳管理器、连接监控器等。使用连接锁确保初始化过程的线程安全。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>使用CAS操作设置连接标志，防止重复初始化</li>
     *   <li>初始化连接管理器并建立MQTT连接</li>
     *   <li>初始化消息管理器</li>
     *   <li>初始化心跳管理器</li>
     *   <li>初始化连接监控器</li>
     *   <li>清理连接标志</li>
     * </ol>
     * <p>
     * <strong>初始化顺序：</strong>
     * <ul>
     *   <li><strong>连接管理器</strong>：首先建立MQTT连接</li>
     *   <li><strong>消息管理器</strong>：依赖连接管理器</li>
     *   <li><strong>心跳管理器</strong>：依赖连接状态</li>
     *   <li><strong>连接监控器</strong>：依赖MQTT客户端实例</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>使用连接锁防止并发初始化</li>
     *   <li>如果连接失败，会跳过后续组件初始化</li>
     *   <li>异常情况下会清理连接标志</li>
     *   <li>组件初始化有依赖关系，必须按顺序进行</li>
     * </ul>
     */
    private void initComponents() {
        // 检查是否正在连接
        if (!connecting.compareAndSet(false, true)) {
            log.debug("连接已在进行中，跳过重复初始化");
            return;
        }

        try {
            // 初始化连接管理器
            this.connectionManager = new ConnectionManager(properties, clientId);

            // 建立连接
            if (!connectionManager.connect()) {
                log.error("MQTT客户端连接失败，跳过后续组件初始化");
                return;
            }

            // 初始化消息管理器（灰度过滤器现在通过静态方法调用，不需要传递）
            this.messageManager = new MqttMessageManager(connectionManager, properties, clientId, messageHandler);

            // 订阅 MQTT 消息事件流，将消息按 topic 分发到业务回调
            subscribeMessageFlowIfNecessary();

            // 初始化心跳管理器
            this.heartbeatManager = new HeartbeatManager(properties, clientId);

            // 初始化连接监控器
            this.connectionMonitor = new ConnectionMonitor(properties, clientId, connectionManager);

            // 为NetworkQualityManager设置clientId
            if (this.networkQualityManager != null) {
                this.networkQualityManager.setClientId(clientId);
                log.debug("网络质量管理器客户端ID已设置: {}", clientId);
            }

            // 订阅 MQTT 消息事件流，将消息按 topic 分发到业务回调
            subscribeMessageFlowIfNecessary();

        } finally {
            connecting.set(false);
        }
    }

    /**
     * 如有必要，订阅 MQTT 消息事件流
     * <p>
     * 说明：
     * <ul>
     *   <li>只订阅一次，防止重复订阅导致回调被多次触发</li>
     *   <li>将 HiveMQ 收到的 {@link Mqtt5Publish} 消息映射为 {@link ConsumerMessage}</li>
     *   <li>根据 topic 从 {@link AbstractMqttClientApi#LISTENER_CACHE} 中查找业务回调并分发</li>
     * </ul>
     */
    private void subscribeMessageFlowIfNecessary() {
        if (!messageFlowSubscribed.compareAndSet(false, true)) {
            return;
        }

        MqttEventBus.messageFlow.subscribe(
                this::dispatchMessageSafely,
                error -> log.error("订阅 MQTT 消息事件流时发生异常", error)
        );
        log.info("HiveMQ 客户端已订阅 MQTT 消息事件流，将按普通订阅与共享订阅两个缓存分发回调");
    }

    private void dispatchMessageSafely(Mqtt5Publish publish) {
        try {
            dispatchMessage(publish);
        } catch (Exception e) {
            log.error("分发 MQTT 消息到业务回调时发生异常，topic={}", publish.getTopic(), e);
        }
    }

    /**
     * 将 MQTT 消息路由到对应 topic 的业务回调
     * <p>
     * 路由规则：
     * <ol>
     *     <li><strong>普通订阅</strong>：按完整 topic 从 {@link AbstractMqttClientApi#LISTENER_CACHE} 精确查找回调</li>
     *     <li><strong>共享订阅</strong>：遍历 {@link AbstractMqttClientApi#SHARED_LISTENER_CACHE}，
     *     提取每个共享订阅 topic 的业务 topic 部分，通过通配符匹配找到对应的回调</li>
     * </ol>
     * <p>
     * 注意：收到消息时，实际 topic 是具体值（如：device/123/status），
     * 需要通过通配符匹配找到对应的共享订阅回调（如：$share/GID_AGENT_DEVICE/device/+/status）。
     *
     * @param publish HiveMQ MQTT 5.0 发布消息
     */
    private void dispatchMessage(Mqtt5Publish publish) {
        String rawTopic = publish.getTopic().toString();

        // 1. 先尝试普通订阅（精确匹配）
        Consumer<ConsumerMessage> callback = LISTENER_CACHE.get(rawTopic);

        // 2. 如果普通订阅未找到，尝试共享订阅（通配符匹配）
        if (callback == null) {
            callback = findSharedSubscriptionCallback(rawTopic);
        }

        if (callback == null) {
            if (log.isDebugEnabled()) {
                log.debug("收到 MQTT 消息但未找到对应回调，rawTopic={}", rawTopic);
            }
            return;
        }

        byte[] payload = publish.getPayloadAsBytes();

        // 将 Mqtt5Publish 的 userProperties 转换为 Map<String, String>
        Map<String, String> properties = extractUserProperties(publish);

        ConsumerMessage consumerMessage = new ConsumerMessage(payload)
                .setTopic(rawTopic)
                .setQos(publish.getQos().getCode())
                .setRetained(publish.isRetain())
                .setProperties(properties)
                .setTimestamp(System.currentTimeMillis());

        callback.accept(consumerMessage);
    }

    /**
     * 从共享订阅缓存中查找匹配的回调（支持通配符匹配）
     * <p>
     * 遍历所有共享订阅 topic（格式：$share/{groupId}/businessTopic），
     * 提取业务 topic 部分，通过通配符匹配找到对应的回调。
     * <p>
     * 示例：
     * <ul>
     *     <li>共享订阅：$share/GID_AGENT_DEVICE/device/+/status</li>
     *     <li>实际 topic：device/123/status</li>
     *     <li>匹配规则：device/+/status 应该匹配 device/123/status</li>
     * </ul>
     *
     * @param actualTopic 实际收到的消息 topic（具体值，不含通配符）
     * @return 匹配的回调函数，如果未找到则返回 null
     */
    private Consumer<ConsumerMessage> findSharedSubscriptionCallback(String actualTopic) {
        for (Map.Entry<String, Consumer<ConsumerMessage>> entry : SHARED_LISTENER_CACHE.entrySet()) {
            String sharedTopic = entry.getKey();
            // 提取业务 topic 部分（去掉 $share/{groupId}/ 前缀）
            String businessTopic = extractBusinessTopic(sharedTopic);
            if (businessTopic == null) {
                continue;
            }
            // 通配符匹配：businessTopic（如：device/+/status）应该匹配 actualTopic（如：device/123/status）
            if (matchesTopicPattern(businessTopic, actualTopic)) {
                if (log.isDebugEnabled()) {
                    log.debug("共享订阅通配符匹配成功：sharedTopic={}, businessTopic={}, actualTopic={}",
                            sharedTopic, businessTopic, actualTopic);
                }
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 从完整的共享订阅 topic 中提取业务 topic
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic）
     * @return 业务 topic，如果格式不正确则返回 null
     */
    private String extractBusinessTopic(String sharedTopic) {
        if (!sharedTopic.startsWith("$share/")) {
            return null;
        }
        int firstSlash = sharedTopic.indexOf('/', "$share/".length());
        if (firstSlash > 0 && firstSlash + 1 < sharedTopic.length()) {
            return sharedTopic.substring(firstSlash + 1);
        }
        return null;
    }

    /**
     * 判断实际 topic 是否匹配通配符 pattern
     * <p>
     * 支持 MQTT 通配符：
     * <ul>
     *     <li>+：单级通配符（匹配一个层级）</li>
     *     <li>#：多级通配符（匹配零个或多个层级，只能出现在末尾）</li>
     * </ul>
     * <p>
     * 示例：
     * <ul>
     *     <li>device/+/status 匹配 device/123/status</li>
     *     <li>device/+/status 不匹配 device/123/456/status</li>
     *     <li>device/# 匹配 device/123/status</li>
     * </ul>
     *
     * @param pattern     通配符 pattern（如：device/+/status）
     * @param actualTopic 实际 topic（如：device/123/status）
     * @return 是否匹配
     */
    private boolean matchesTopicPattern(String pattern, String actualTopic) {
        if (pattern.equals(actualTopic)) {
            return true;
        }

        String[] patternParts = pattern.split("/");
        String[] actualParts = actualTopic.split("/");

        // 处理多级通配符 #（只能出现在末尾）
        if (pattern.endsWith("/#")) {
            String prefixPattern = pattern.substring(0, pattern.length() - 2);
            return actualTopic.startsWith("%s/".formatted(prefixPattern)) || actualTopic.equals(prefixPattern);
        }
        if (pattern.equals("#")) {
            return true;
        }

        // 单级通配符 + 匹配
        if (patternParts.length != actualParts.length) {
            return false;
        }

        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String actualPart = actualParts[i];

            // 多级通配符 #：匹配剩余所有层级（不在末尾的情况，理论上不应该出现，但兼容处理）
            if (patternPart.equals("#")) {
                return true;
            }
            // 单级通配符 +：匹配任意一个层级，继续下一级（不需要检查）
            // 精确匹配：必须完全相等，否则返回 false
            else if (!patternPart.equals("+") && !patternPart.equals(actualPart)) {
                return false;
            }
            // 如果是 + 或精确匹配，继续循环
        }

        return true;
    }

    /**
     * 从 Mqtt5Publish 中提取 userProperties 并转换为 Map<String, String>
     *
     * @param publish MQTT 5.0 发布消息
     * @return userProperties 的 Map 表示，如果不存在则返回 null
     */
    @Nonnull
    private Map<String, String> extractUserProperties(Mqtt5Publish publish) {
        if (publish == null) {
            return Map.of();
        }

        try {
            return publish.getUserProperties().asList().stream()
                    .collect(Collectors.toMap(
                            prop -> prop.getName().toString(),
                            prop -> prop.getValue().toString(),
                            (existing, _) -> existing
                    ));
        } catch (Exception e) {
            log.warn("提取 MQTT userProperties 失败: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * 启动服务
     * <p>
     * <strong>功能说明：</strong>
     * 启动MQTT客户端的核心服务，包括连接监控器、心跳管理器等。
     * 在组件初始化完成后调用，确保所有服务正常运行。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查必要组件是否已初始化</li>
     *   <li>启动连接监控器</li>
     *   <li>启动心跳管理器</li>
     *   <li>检查网络质量监控状态</li>
     *   <li>记录服务启动完成日志</li>
     * </ol>
     * <p>
     * <strong>启动的服务：</strong>
     * <ul>
     *   <li><strong>连接监控器</strong>：监控MQTT连接状态变化</li>
     *   <li><strong>心跳管理器</strong>：维护连接活跃性</li>
     *   <li><strong>网络质量监控</strong>：监控网络状况</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>只有在组件完全初始化后才会启动服务</li>
     *   <li>如果组件未初始化，会记录警告日志并跳过启动</li>
     *   <li>网络质量监控状态会被检查但不会启动</li>
     * </ul>
     */
    private void startServices() {
        // 检查必要组件是否已初始化
        if (connectionMonitor == null || heartbeatManager == null) {
            log.warn("MQTT组件未完全初始化，跳过服务启动");
            return;
        }

        // 启动连接监控器
        connectionMonitor.start();

        // 启动心跳管理器
        heartbeatManager.start();

        // 检查网络质量监控状态
        if (networkQualityManager != null) {
            log.info("网络质量监控状态: {}", networkQualityManager.isMonitoring() ? "运行中" : "已停止");
        }

        log.info("MQTT服务启动完成，客户端ID: {}", clientId);
    }


    // ==================== 私有方法 - 状态检查 ====================

    /**
     * 检查连接是否未就绪
     * <p>
     * <strong>功能说明：</strong>
     * 检查MQTT连接是否处于就绪状态，用于消息发布前的状态验证。
     * 只有当连接管理器、MQTT客户端都存在且连接正常时，连接才被认为是就绪的。
     * <p>
     * <strong>检查内容：</strong>
     * <ul>
     *   <li>连接管理器是否存在</li>
     *   <li>MQTT客户端实例是否存在</li>
     *   <li>连接状态是否为已连接</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>消息发布前的连接状态验证</li>
     *   <li>操作执行前的连接就绪性检查</li>
     *   <li>连接状态的实时监控</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>该方法在消息发布前被调用</li>
     *   <li>如果连接未就绪，会记录警告日志</li>
     *   <li>使用连接管理器的状态进行判断</li>
     * </ul>
     *
     * @return 如果连接未就绪返回true，否则返回false
     */
    private boolean isConnectionNotReady() {
        return connectionManager == null ||
                connectionManager.getMqttClient() == null ||
                !isConnected(); // 使用连接管理器的状态
    }

    /**
     * 获取连接状态
     * <p>
     * <strong>功能说明：</strong>
     * 获取当前MQTT连接的状态，用于判断客户端是否已连接到服务器。
     * 该方法委托给连接管理器，确保状态信息的一致性。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查连接管理器是否存在</li>
     *   <li>调用连接管理器的isConnected()方法</li>
     *   <li>返回连接状态结果</li>
     * </ol>
     * <p>
     * <strong>返回值说明：</strong>
     * <ul>
     *   <li><strong>true</strong>：已连接到MQTT服务器</li>
     *   <li><strong>false</strong>：未连接到MQTT服务器</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果连接管理器为null，返回false</li>
     *   <li>状态信息来自连接管理器，确保一致性</li>
     *   <li>该方法不会抛出异常</li>
     * </ul>
     *
     * @return 如果已连接返回true，否则返回false
     */
    private boolean isConnected() {
        return connectionManager != null && connectionManager.isConnected();
    }

    // ==================== 公共方法 - 网络质量监控 ====================

    /**
     * 获取网络质量统计信息
     * <p>
     * <strong>功能说明：</strong>
     * 获取当前网络质量的统计信息，包括延迟、丢包率、网络类型等指标。
     * 这些信息用于网络状况分析和网络切换决策。
     * <p>
     * <strong>返回内容：</strong>
     * <ul>
     *   <li><strong>网络延迟</strong>：平均延迟时间</li>
     *   <li><strong>丢包率</strong>：网络丢包百分比</li>
     *   <li><strong>网络类型</strong>：当前使用的网络类型</li>
     *   <li><strong>统计时间</strong>：统计数据的采集时间</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络状况监控和报告</li>
     *   <li>网络切换决策</li>
     *   <li>性能分析和优化</li>
     *   <li>故障排查和诊断</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果网络质量管理器未初始化，返回null</li>
     *   <li>统计数据是实时采集的，反映当前网络状况</li>
     *   <li>建议定期调用以获取最新的网络质量信息</li>
     * </ul>
     *
     * @return 网络质量统计信息，如果未初始化则返回null
     */
    public NetworkQualityStats getNetworkQualityStats() {
        if (networkQualityManager != null) {
            return networkQualityManager.getNetworkQualityStats();
        }
        return null;
    }

    /**
     * 手动触发网络质量检查
     * <p>
     * <strong>功能说明：</strong>
     * 手动触发一次网络质量检查，立即执行网络延迟和丢包率测试。
     * 该方法可以用于需要实时网络状况信息的场景。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查网络质量管理器是否已初始化</li>
     *   <li>触发网络质量检查</li>
     *   <li>记录检查结果</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络切换前的质量评估</li>
     *   <li>故障排查时的网络诊断</li>
     *   <li>性能测试时的网络状况确认</li>
     *   <li>用户主动的网络质量检查</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果网络质量管理器未初始化，会记录警告日志</li>
     *   <li>检查过程可能需要几秒钟时间</li>
     *   <li>检查结果会更新网络质量统计信息</li>
     * </ul>
     */
    public void checkNetworkQuality() {
        if (networkQualityManager != null) {
            networkQualityManager.triggerNetworkQualityCheck();
        } else {
            log.warn("网络质量管理器未初始化，无法触发网络质量检查");
        }
    }

    /**
     * 启动网络质量监控
     * <p>
     * <strong>功能说明：</strong>
     * 启动网络质量监控服务，开始定期采集网络质量数据。
     * 监控服务会在后台持续运行，定期更新网络质量统计信息。
     * <p>
     * <strong>监控内容：</strong>
     * <ul>
     *   <li><strong>网络延迟</strong>：定期测试网络延迟</li>
     *   <li><strong>丢包率</strong>：检测网络丢包情况</li>
     *   <li><strong>网络类型</strong>：监控网络类型变化</li>
     *   <li><strong>连接稳定性</strong>：评估连接质量</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>应用启动时启动监控</li>
     *   <li>网络切换后重新启动监控</li>
     *   <li>故障恢复后启动监控</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果网络质量管理器未初始化，会记录警告日志</li>
     *   <li>监控服务会在后台持续运行</li>
     *   <li>可以通过stopNetworkQualityMonitoring()停止监控</li>
     * </ul>
     */
    public void startNetworkQualityMonitoring() {
        if (networkQualityManager != null) {
            networkQualityManager.startMonitoring();
        } else {
            log.warn("网络质量管理器未初始化，无法启动网络质量监控");
        }
    }

    /**
     * 停止网络质量监控
     * <p>
     * <strong>功能说明：</strong>
     * 停止网络质量监控服务，停止采集网络质量数据。
     * 停止后，网络质量统计信息将不再更新。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>应用关闭时停止监控</li>
     *   <li>网络切换时临时停止监控</li>
     *   <li>资源节省时停止监控</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果网络质量管理器未初始化，会记录警告日志</li>
     *   <li>停止后可以通过startNetworkQualityMonitoring()重新启动</li>
     *   <li>停止监控不会影响已采集的统计数据</li>
     * </ul>
     */
    public void stopNetworkQualityMonitoring() {
        if (networkQualityManager != null) {
            networkQualityManager.stopMonitoring();
        } else {
            log.warn("网络质量管理器未初始化，无法停止网络质量监控");
        }
    }

    /**
     * 检查网络质量监控是否正在运行
     * <p>
     * <strong>功能说明：</strong>
     * 检查网络质量监控服务是否正在运行，用于判断监控状态。
     * 该方法返回监控服务的当前运行状态。
     * <p>
     * <strong>返回值说明：</strong>
     * <ul>
     *   <li><strong>true</strong>：监控服务正在运行</li>
     *   <li><strong>false</strong>：监控服务已停止或未初始化</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>监控状态检查</li>
     *   <li>服务状态报告</li>
     *   <li>监控控制逻辑</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果网络质量管理器未初始化，返回false</li>
     *   <li>该方法不会抛出异常</li>
     *   <li>状态信息是实时的</li>
     * </ul>
     *
     * @return 如果监控正在运行返回true，否则返回false
     */
    public boolean isNetworkQualityMonitoring() {
        return networkQualityManager != null && networkQualityManager.isMonitoring();
    }

    /**
     * 重置网络质量统计
     * <p>
     * <strong>功能说明：</strong>
     * 重置网络质量统计数据，清除历史统计信息。
     * 重置后，统计信息将从零开始重新采集。
     * <p>
     * <strong>重置内容：</strong>
     * <ul>
     *   <li><strong>延迟统计</strong>：清除延迟历史数据</li>
     *   <li><strong>丢包统计</strong>：清除丢包历史数据</li>
     *   <li><strong>网络类型统计</strong>：清除网络类型变化历史</li>
     *   <li><strong>时间统计</strong>：重置统计开始时间</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络环境变化后重新开始统计</li>
     *   <li>测试环境下的统计重置</li>
     *   <li>故障恢复后的统计清理</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果网络质量管理器未初始化，会记录警告日志</li>
     *   <li>重置操作不可逆，历史数据将丢失</li>
     *   <li>重置后需要重新启动监控才能采集新数据</li>
     * </ul>
     */
    public void resetNetworkQualityStats() {
        if (networkQualityManager != null) {
            networkQualityManager.resetNetworkQualityStats();
        } else {
            log.warn("网络质量管理器未初始化，无法重置网络质量统计");
        }
    }

    // ==================== 私有方法 - 资源清理 ====================

    /**
     * 断开MQTT连接并清理相关资源
     * <p>
     * <strong>功能说明：</strong>
     * 执行MQTT连接断开和资源清理的通用逻辑，包括停止各种管理器、
     * 断开连接、清理事件流等操作。该方法被disconnect()和destroy()共同使用。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>停止心跳管理器</li>
     *   <li>停止连接监控器</li>
     *   <li>停止网络质量监控</li>
     *   <li>断开MQTT连接</li>
     *   <li>清理所有事件流</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>该方法会停止所有相关的监控和管理服务</li>
     *   <li>事件流清理后需要重新建立连接才能恢复</li>
     *   <li>异常情况下会记录错误日志但不抛出异常</li>
     * </ul>
     */
    private void disconnectAndCleanup() {
        try {
            // 停止心跳管理器
            if (heartbeatManager != null) {
                heartbeatManager.stop();
            }

            // 停止连接监控器
            if (connectionMonitor != null) {
                connectionMonitor.stop();
            }

            // 停止网络质量监控
            if (networkQualityManager != null) {
                networkQualityManager.stopMonitoring();
            }

            // 断开MQTT连接
            if (connectionManager != null) {
                connectionManager.disconnect();
            }

        } catch (Exception e) {
            log.error("断开连接和清理资源时发生异常: {}", e.getMessage(), e);
        }
    }

    // ==================== 公共方法 - 连接管理 ====================

    /**
     * 正常断开连接（用户主动断开）
     * <p>
     * <strong>功能说明：</strong>
     * 用户主动断开MQTT连接，执行完整的资源清理操作。
     * 该方法会停止所有相关服务并清理事件流。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>用户主动断开连接</li>
     *   <li>应用程序正常关闭</li>
     *   <li>连接重置操作</li>
     * </ul>
     */
    @Override
    public void disconnect() {
        log.info("开始执行MQTT客户端正常断开操作，客户端ID: {}", clientId);

        // 调用通用的断开和清理方法
        disconnectAndCleanup();

        log.info("MQTT客户端正常断开完成，客户端ID: {}", clientId);
    }

    /**
     * 处理网络类型发布失败
     *
     * @param networkType   发布失败的网络类型
     * @param publishResult 发布结果
     */
    private void handleNetworkTypePublishFailure(NetworkTypeEnum networkType, PublishResult<NetworkTypeEnum> publishResult) {
        switch (publishResult.getFailureReason()) {
            case BUFFER_OVERFLOW:
                log.warn("网络类型缓冲区溢出，事件被丢弃: {}", networkType);
                // 网络类型事件丢失通常不是严重问题，可以忽略
                break;

            case SINK_TERMINATED:
                log.error("事件总线已终止，无法发布网络类型事件: {}", networkType);
                // 需要重新初始化事件总线或重新订阅
                break;

            case EXCEPTION:
                log.error("发布网络类型事件时发生异常: {}", networkType, publishResult.getException());
                // 异常处理逻辑
                break;

            default:
                log.warn("网络类型事件发布失败: {}, 原因: {}", networkType, publishResult.getFailureReason());
                break;
        }
    }
}
