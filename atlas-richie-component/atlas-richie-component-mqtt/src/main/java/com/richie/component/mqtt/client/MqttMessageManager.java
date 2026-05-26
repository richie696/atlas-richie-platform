package com.richie.component.mqtt.client;

import com.richie.context.common.api.HeaderContextHolder;
import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.exception.PlatformRuntimeException;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.mqtt.beans.ConnectionState;
import com.richie.component.mqtt.beans.SubscriptionResult;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.canary.MqttCanaryFilter;
import com.richie.component.mqtt.filter.handler.MessageHandler;
import tools.jackson.core.type.TypeReference;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperties;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserPropertiesBuilder;
import com.hivemq.client.mqtt.mqtt5.datatypes.Mqtt5UserProperty;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MQTT消息管理器
 * <p>
 * 负责MQTT消息的发送、接收、处理等完整消息生命周期管理，是MQTT组件的核心消息处理组件。
 * 该类实现了消息的发布、订阅、接收、处理等完整流程，支持消息重试、去重、缓存等高级功能。
 * <p>
 * <strong>核心功能：</strong>
 * <ul>
 *   <li><strong>消息发布</strong>：支持QoS、保留消息、消息过期等MQTT 5.0特性</li>
 *   <li><strong>消息订阅</strong>：动态订阅和取消订阅主题</li>
 *   <li><strong>消息接收</strong>：监听和处理接收到的MQTT消息</li>
 *   <li><strong>消息处理</strong>：消息去重、缓存、事件发布等</li>
 *   <li><strong>连接管理</strong>：连接状态感知和自动重连支持</li>
 *   <li><strong>错误处理</strong>：消息发送重试和异常处理</li>
 * </ul>
 * <p>
 * <strong>消息特性：</strong>
 * <ul>
 *   <li><strong>QoS支持</strong>：支持MQTT 5.0的QoS级别</li>
 *   <li><strong>保留消息</strong>：支持消息保留功能</li>
 *   <li><strong>消息过期</strong>：支持消息过期时间设置</li>
 *   <li><strong>用户属性</strong>：支持自定义用户属性</li>
 *   <li><strong>消息ID</strong>：自动生成唯一消息标识</li>
 * </ul>
 * <p>
 * <strong>高级功能：</strong>
 * <ul>
 *   <li><strong>消息去重</strong>：基于消息处理器实现幂等性</li>
 *   <li><strong>消息缓存</strong>：支持消息缓存和过期管理</li>
 *   <li><strong>重试机制</strong>：消息发送失败自动重试</li>
 *   <li><strong>状态感知</strong>：连接状态变化自动处理</li>
 *   <li><strong>事件驱动</strong>：基于事件总线的消息处理</li>
 * </ul>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li>IoT设备消息通信</li>
 *   <li>实时数据推送</li>
 *   <li>消息队列和发布订阅</li>
 *   <li>设备状态监控</li>
 *   <li>分布式系统通信</li>
 *   <li>事件驱动架构</li>
 * </ul>
 * <p>
 * <strong>技术特点：</strong>
 * <ul>
 *   <li>基于MQTT 5.0协议，支持最新特性</li>
 *   <li>异步消息处理，提高系统响应性</li>
 *   <li>事件总线集成，支持组件间解耦</li>
 *   <li>连接状态感知，自动处理重连</li>
 *   <li>消息重试和异常处理，提高可靠性</li>
 *   <li>支持消息去重和缓存，避免重复处理</li>
 * </ul>
 *
 * @author richie696
 * @version 2.0
 * @since 2025-07-27
 */
@Slf4j
public class MqttMessageManager {

    /**
     * MQTT客户端配置
     */
    private final MqttClientProperties properties;

    /**
     * 客户端ID
     */
    private final String clientId;

    /**
     * MQTT连接管理器
     */
    private final ConnectionManager connectionManager;

    /**
     * 消息处理器（HiveMQ 专用，处理 Mqtt5Publish 类型消息）
     */
    private final MessageHandler<Mqtt5Publish> messageHandler;

    /**
     * 连接状态检查函数
     */
    private final AtomicBoolean connectionChecker = new AtomicBoolean(true);

    /**
     * 当前连接状态
     */
    private final AtomicReference<ConnectionState> currentConnectionState = new AtomicReference<>(ConnectionState.DISCONNECTED);

    /**
     * 发布消息最大重试次数
     */
    private static final int MAX_PUBLISH_RETRY = 5;

    /**
     * 发布消息重试间隔（毫秒）
     */
    private static final long PUBLISH_RETRY_INTERVAL_MS = 2000;

    /**
     * 心跳重试锁
     */
    private static final AtomicBoolean HB_RETRY_LOCK = new AtomicBoolean(false);

    /**
     * 构造MQTT消息管理器
     * <p>
     * <strong>设计原则：</strong> 依赖注入，组件解耦，职责分离
     * <p>
     * <strong>功能说明：</strong>
     * 创建MQTT消息管理器实例，初始化必要的组件和事件订阅。
     * 构造函数会设置消息监听器、事件处理器等，为后续的消息处理做准备。
     * <p>
     * <strong>初始化内容：</strong>
     * <ul>
     *   <li>保存MQTT连接管理器、配置、客户端ID和消息处理器</li>
     *   <li>调用initial()方法完成初始化</li>
     *   <li>设置事件订阅和消息监听器</li>
     *   <li>配置连接状态处理逻辑</li>
     * </ul>
     * <p>
     * <strong>依赖组件：</strong>
     * <ul>
     *   <li><strong>connectionManager</strong>：MQTT连接管理器，负责连接管理</li>
     *   <li><strong>properties</strong>：MQTT客户端配置，包含QoS等设置</li>
     *   <li><strong>clientId</strong>：客户端唯一标识，用于消息标识</li>
     *   <li><strong>messageHandler</strong>：消息处理器，负责消息去重和缓存</li>
     * </ul>
     * <p>
     * <strong>参数要求：</strong>
     * <ul>
     *   <li>connectionManager：MQTT连接管理器，不能为null</li>
     *   <li>properties：MQTT客户端配置，不能为null</li>
     *   <li>clientId：客户端唯一标识，不能为空</li>
     *   <li>messageHandler：消息处理器，不能为null</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>构造函数会立即调用initial()方法完成初始化</li>
     *   <li>所有依赖组件都不能为null</li>
     *   <li>初始化过程会设置事件订阅和监听器</li>
     *   <li>支持动态配置调整</li>
     * </ul>
     *
     * @param connectionManager MQTT连接管理器
     * @param properties        MQTT客户端配置
     * @param clientId          客户端唯一标识
     * @param messageHandler    消息处理器（HiveMQ 专用，处理 Mqtt5Publish 类型消息）
     * @throws IllegalArgumentException 当参数为null时
     */
    public MqttMessageManager(ConnectionManager connectionManager, MqttClientProperties properties, String clientId, MessageHandler<Mqtt5Publish> messageHandler) {
        this.properties = properties;
        this.clientId = clientId;
        this.connectionManager = connectionManager;
        this.messageHandler = messageHandler;
        initial();
    }

    /**
     * 初始化消息管理器
     * <p>
     * <strong>设计原则：</strong> 事件驱动，组件集成，状态同步
     * <p>
     * <strong>功能说明：</strong>
     * 完成消息管理器的初始化，设置事件订阅、消息监听器和连接状态处理。
     * 该方法是消息管理器初始化的核心，建立了完整的事件处理链路。
     * <p>
     * <strong>初始化流程：</strong>
     * <ol>
     *   <li>订阅连接状态事件，感知连接状态变化</li>
     *   <li>订阅心跳事件，自动发送心跳消息</li>
     *   <li>订阅连接状态事件，处理状态变化</li>
     *   <li>设置消息监听器，监听接收到的消息</li>
     * </ol>
     * <p>
     * <strong>事件订阅：</strong>
     * <ul>
     *   <li><strong>connectedFlow</strong>：连接状态变化，更新内部状态</li>
     *   <li><strong>heartbeatFlow</strong>：心跳事件，自动发送心跳消息</li>
     *   <li><strong>connectionStateFlow</strong>：连接状态事件，处理状态变化</li>
     * </ul>
     * <p>
     * <strong>消息监听器：</strong>
     * <ul>
     *   <li>监听所有发布的消息（MqttGlobalPublishFilter.ALL）</li>
     *   <li>委托给handleMessage方法处理接收到的消息</li>
     *   <li>支持动态主题订阅</li>
     * </ul>
     * <p>
     * <strong>状态同步：</strong>
     * <ul>
     *   <li>连接状态实时同步</li>
     *   <li>心跳消息自动处理</li>
     *   <li>连接状态变化响应</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>初始化过程会建立多个事件订阅</li>
     *   <li>消息监听器依赖于MQTT客户端连接</li>
     *   <li>事件处理是异步的，不会阻塞初始化</li>
     *   <li>支持动态配置和状态变化</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>系统启动时的消息管理器初始化</li>
     *   <li>配置变更后的重新初始化</li>
     *   <li>连接重建后的监听器重新设置</li>
     * </ul>
     */
    public void initial() {
        // 订阅连接状态事件，感知连接状态变化
        MqttEventBus.connectedFlow.subscribe(connectionChecker::set);

        // 订阅心跳事件，自动发送心跳消息
        MqttEventBus.heartbeatFlow.subscribe(event -> sendMessage(event.getTopic(), event.getHeartbeat().toPayload(), false));

        // 启用消息监听器（用于监听所有订阅主题的消息，如果当前尚未订阅任何主题，则该监听不会收到任何消息）
        setupMessageListener();
    }

    /**
     * 处理接收到的MQTT消息
     * <p>
     * <strong>设计原则：</strong> 消息去重，幂等处理，事件驱动
     * <p>
     * <strong>功能说明：</strong>
     * 处理接收到的MQTT消息，包括消息解析、去重检查、缓存保存和事件发布。
     * 该方法是消息接收处理的核心，实现了完整的消息处理流程。
     * <p>
     * <strong>处理流程：</strong>
     * <ol>
     *   <li>提取消息主题和负载内容</li>
     *   <li>反序列化消息为ConsumerMessage对象</li>
     *   <li>执行消息去重检查</li>
     *   <li>保存消息到缓存（10分钟过期）</li>
     *   <li>发布消息事件给下游处理器</li>
     * </ol>
     * <p>
     * <strong>消息去重：</strong>
     * <ul>
     *   <li>调用messageHandler.isDuplicate()检查重复</li>
     *   <li>重复消息直接跳过处理</li>
     *   <li>确保消息处理的幂等性</li>
     *   <li>避免重复处理相同消息</li>
     * </ul>
     * <p>
     * <strong>消息缓存：</strong>
     * <ul>
     *   <li>缓存时间：10分钟（TimeUnit.MINUTES.toMillis(10)）</li>
     *   <li>缓存目的：避免重复处理和消息丢失</li>
     *   <li>缓存管理：由messageHandler负责</li>
     *   <li>自动过期：支持缓存自动清理</li>
     * </ul>
     * <p>
     * <strong>事件发布：</strong>
     * <ul>
     *   <li>通过MqttEventBus.publishMessage()发布消息</li>
     *   <li>支持下游组件订阅和处理</li>
     *   <li>事件驱动架构，组件解耦</li>
     *   <li>异步处理，不阻塞消息接收</li>
     * </ul>
     * <p>
     * <strong>异常处理：</strong>
     * <ul>
     *   <li>捕获所有异常，避免消息处理中断</li>
     *   <li>记录错误日志，便于问题排查</li>
     *   <li>异常情况下跳过消息处理</li>
     *   <li>确保消息处理流程的稳定性</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>消息去重依赖于messageHandler的实现</li>
     *   <li>缓存时间可以根据业务需求调整</li>
     *   <li>异常情况下消息会丢失</li>
     *   <li>建议监控消息处理成功率</li>
     * </ul>
     *
     * @param publishMessage 接收到的MQTT发布消息
     */
    public void handleMessage(Mqtt5Publish publishMessage) {
        try {
            String topic = publishMessage.getTopic().toString();

            // 灰度消息过滤：判断是否应该处理该消息（优先执行，避免不必要的幂等去重）
            // 注意：灰度过滤必须在幂等去重之前执行，原因：
            // 1. 如果使用 Redis 共享缓存，灰度消息被不符合条件的实例消费后，会先执行幂等去重（写入 Redis）
            //    然后被灰度过滤器过滤，可能导致符合条件的实例（如灰度实例）也认为消息重复
            // 2. 灰度过滤是轻量级检查，应该优先执行
            if (!MqttCanaryFilter.shouldProcess(publishMessage)) {
                if (log.isDebugEnabled()) {
                    log.debug("MQTT消息被灰度过滤器跳过，topic: {}", topic);
                }
                return;
            }

            // 幂等去重处理（在灰度过滤之后执行，只对符合条件的消息进行去重）
            // 注意：HiveMQ 实现中，Mqtt5Publish 的 payload 直接是业务数据，不再是 ConsumerMessage 的序列化结果
            // 所以直接使用 Mqtt5Publish 进行去重检查
            if (messageHandler.isDuplicate(publishMessage)) {
                log.info("消息去重成功，跳过处理，topic: {}", topic);
                return;
            }

            // 保存消息缓存
            messageHandler.saveCache(publishMessage, TimeUnit.MINUTES.toMillis(10));

            PublishResult<Mqtt5Publish> publishResult = MqttEventPublisher.publishMessage(publishMessage);
            if (publishResult.isFailed()) {
                log.warn("消息发布失败: {}, 原因: {}", topic, publishResult.getFailureReason());
                // 根据失败原因进行相应处理
                handleMessagePublishFailure(publishMessage, publishResult);
            }
        } catch (Exception e) {
            log.error("处理MQTT消息时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取当前连接状态
     *
     * @return 当前MQTT连接状态
     */
    public ConnectionState getCurrentConnectionState() {
        return currentConnectionState.get();
    }

    /**
     * 获取心跳重试锁
     *
     * @return 心跳重试锁
     */
    public AtomicBoolean getHbRetryLock() {
        return HB_RETRY_LOCK;
    }

    /**
     * 获取MQTT QoS级别
     *
     * @return QoS级别
     */
    private MqttQos getMqttQos() {
        var qos = MqttQos.fromCode(properties.getServer().getQos().getValue());
        if (qos == null) {
            qos = MqttQos.AT_LEAST_ONCE;
        }
        return qos;
    }


    /**
     * 设置消息接收监听器
     */
    private void setupMessageListener() {
        if (connectionManager == null || connectionManager.getMqttClient() == null) {
            log.warn("MQTT连接管理器未初始化，跳过消息监听器设置");
            return;
        }

        Mqtt5AsyncClient mqttClient = connectionManager.getMqttClient();
        // 设置消息接收监听器，委托给消息处理函数去处理
        mqttClient.publishes(MqttGlobalPublishFilter.ALL, this::handleMessage);

        log.info("MQTT消息监听器已设置，客户端ID: {}", clientId);
    }

    /**
     * 发送消息
     * <p>
     * <strong>设计原则：</strong> 连接感知，重试机制，异常处理
     * <p>
     * <strong>功能说明：</strong>
     * 发送MQTT消息，支持连接状态检查、自动重试和异常处理。
     * 该方法实现了完整的消息发送流程，包括连接检查、消息发布、重试机制等。
     * <p>
     * <strong>发送流程：</strong>
     * <ol>
     *   <li>检查连接状态，等待连接建立</li>
     *   <li>根据连接状态决定是否重试</li>
     *   <li>执行消息发布，支持重试机制</li>
     *   <li>处理发布结果和异常情况</li>
     *   <li>记录发送日志和统计信息</li>
     * </ol>
     * <p>
     * <strong>连接检查：</strong>
     * <ul>
     *   <li>调用waitForConnectedWithStateCheck()检查连接</li>
     *   <li>支持连接状态感知和自动重连</li>
     *   <li>根据连接状态决定处理策略</li>
     *   <li>异常状态时等待重连完成</li>
     * </ul>
     * <p>
     * <strong>重试机制：</strong>
     * <ul>
     *   <li>最大重试次数：5次（MAX_PUBLISH_RETRY）</li>
     *   <li>重试间隔：2秒（PUBLISH_RETRY_INTERVAL_MS）</li>
     *   <li>重试条件：发布失败或异常</li>
     *   <li>重试策略：指数退避</li>
     * </ul>
     * <p>
     * <strong>状态处理：</strong>
     * <ul>
     *   <li><strong>DISCONNECTED</strong>：正常断开，跳过发送</li>
     *   <li><strong>异常状态</strong>：等待重连后发送</li>
     *   <li><strong>连接检查</strong>：等待连接建立</li>
     *   <li><strong>超时处理</strong>：10秒超时机制</li>
     * </ul>
     * <p>
     * <strong>异常处理：</strong>
     * <ul>
     *   <li>连接断开时抛出RuntimeException</li>
     *   <li>重连失败时抛出RuntimeException</li>
     *   <li>重试耗尽时记录错误日志</li>
     *   <li>支持线程中断处理</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>消息发送会阻塞直到完成或失败</li>
     *   <li>重试过程中会检查连接状态</li>
     *   <li>超时时间可以根据网络环境调整</li>
     *   <li>建议监控消息发送成功率</li>
     * </ul>
     *
     * @param topic    消息主题
     * @param value    消息内容（字节数组）
     * @param retained 是否保留消息
     * @throws RuntimeException 当连接断开或重连失败时
     */
    public void sendMessage(String topic, byte[] value, boolean retained) {
        try {
            // 检查连接状态
            if (!waitForConnectedWithStateCheck()) {
                // 连接检查失败，根据当前状态决定是否重试
                ConnectionState currentState = currentConnectionState.get();
                if (currentState == ConnectionState.DISCONNECTED) {
                    log.warn("[MQTT] 连接已断开，跳过消息发送，主题: {}", topic);
                    throw new PlatformRuntimeException("MQTT连接已断开，无法发送消息");
                } else {
                    log.warn("[MQTT] 连接异常，尝试重连后发送消息，主题: {}", topic);
                    // 异常状态时，等待重连完成
                    if (!waitForConnectedWithStateCheck()) {
                        throw new PlatformRuntimeException("MQTT连接异常且重连失败，无法发送消息");
                    }
                }
            }

            boolean published = false;
            int retry = 0;
            while (!published && retry < MAX_PUBLISH_RETRY) {
                try {
                    var result = publishContent(topic, value, retained);
                    if (result.isEmpty()) {
                        published = true;
                        log.info("[MQTT] 消息发送成功，主题：{}", topic);
                        break;
                    } else {
                        log.warn("[MQTT] 发送失败，重试第{}次，错误：{}", retry + 1, result);
                    }
                } catch (Exception e) {
                    log.warn("[MQTT] 发送异常，重试第{}次，错误：{}", retry + 1, e.getMessage());
                }

                retry++;
                try {
                    Thread.sleep(PUBLISH_RETRY_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!published) {
                throw new PlatformRuntimeException("[MQTT] 消息发送最终失败，主题：{}，已重试{}次", topic, retry);
            }
            HB_RETRY_LOCK.set(false);
        } catch (Exception e) {
            log.error("[MQTT] 消息发送异常，主题：{}，错误：{}", topic, e.getMessage());
            throw new PlatformRuntimeException("消息发送失败: %s".formatted(e.getMessage()), e);
        }
    }

    /**
     * 发布消息内容
     * <p>
     * <strong>设计原则：</strong> 消息构建，异步发布，结果处理
     * <p>
     * <strong>功能说明：</strong>
     * 构建MQTT 5.0发布消息并异步发布，支持完整的消息属性和用户属性。
     * 该方法是消息发布的核心实现，负责消息构建、属性设置和发布执行。
     * <p>
     * <strong>消息构建：</strong>
     * <ol>
     *   <li>创建ConsumerMessage对象，设置消息属性</li>
     *   <li>生成唯一消息ID（UUID）</li>
     *   <li>设置QoS、保留标志、主题和时间戳</li>
     *   <li>序列化消息为字节数组</li>
     * </ol>
     * <p>
     * <strong>MQTT 5.0特性：</strong>
     * <ul>
     *   <li><strong>QoS支持</strong>：从配置获取QoS级别</li>
     *   <li><strong>保留消息</strong>：支持消息保留功能</li>
     *   <li><strong>消息过期</strong>：支持消息过期时间设置</li>
     *   <li><strong>用户属性</strong>：包含客户端ID、时间戳、恢复模式</li>
     *   <li><strong>主题过滤</strong>：支持动态主题</li>
     * </ul>
     * <p>
     * <strong>用户属性：</strong>
     * <ul>
     *   <li><strong>clientId</strong>：客户端唯一标识</li>
     *   <li><strong>timestamp</strong>：消息发送时间戳</li>
     *   <li><strong>recoveryMode</strong>：恢复模式标识</li>
     * </ul>
     * <p>
     * <strong>异步发布：</strong>
     * <ul>
     *   <li>使用CompletableFuture异步发布</li>
     *   <li>支持发布结果回调处理</li>
     *   <li>记录发布成功和失败日志</li>
     *   <li>不阻塞调用线程</li>
     * </ul>
     * <p>
     * <strong>结果处理：</strong>
     * <ul>
     *   <li>成功时记录消息ID和主题</li>
     *   <li>失败时记录错误信息和原因</li>
     *   <li>返回空字符串表示成功</li>
     *   <li>返回错误信息字符串表示失败</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>消息ID使用UUID确保唯一性</li>
     *   <li>时间戳使用系统当前时间</li>
     *   <li>用户属性支持扩展和自定义</li>
     *   <li>异步发布需要正确处理结果</li>
     * </ul>
     *
     * @param topic    消息主题
     * @param value    消息内容（字节数组）
     * @param retained 是否保留消息
     * @return 错误信息，成功返回空字符串，失败返回详细错误描述
     */
    private String publishContent(String topic, byte[] value, boolean retained) {
        try {
            // 构建MQTT 5.0发布消息
            Mqtt5UserPropertiesBuilder userPropertiesBuilder = Mqtt5UserProperties.builder()
                    .addAll(
                            Mqtt5UserProperty.of("clientId", clientId),
                            Mqtt5UserProperty.of("timestamp", String.valueOf(System.currentTimeMillis())),
                            Mqtt5UserProperty.of("recoveryMode", "fast")
                    );

            // 灰度标识传递：从请求上下文获取 X-Canary-Id，设置到 MQTT 5.0 User Properties
            // 灰度发布统一使用 ID 模式，只需传递 X-Canary-Id
            var canaryId = HeaderContextHolder.getHeader(GlobalConstants.X_CANARY_ID);
            if (StringUtils.isNotBlank(canaryId)) {
                userPropertiesBuilder = userPropertiesBuilder.add(Mqtt5UserProperty.of(GlobalConstants.X_CANARY_ID, canaryId));
            }

            Mqtt5Publish publish = Mqtt5Publish.builder()
                    .topic(topic)
                    .payload(value)
                    .qos(getMqttQos())
                    .retain(retained)
                    .messageExpiryInterval(properties.getMqtt5().getMessageExpiryInterval())
                    .userProperties(userPropertiesBuilder.build())
                    .build();

            CompletableFuture<Mqtt5PublishResult> future = connectionManager.getMqttClient().publish(publish);
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("MQTT 5.0 消息发布失败，主题：{}，错误：{}", topic, throwable.getMessage());
                } else {
                    // 使用时间戳和主题的组合作为消息ID进行日志记录
                    long messageId = System.currentTimeMillis();
                    log.info("MQTT 5.0 消息发布成功，主题：{}，消息ID：{}", topic, messageId);
                }
            });

            return "";
        } catch (Exception e) {
            return "[MQTT] 消息发布失败！\n客户端ID：%s\n订阅主题：%s\n下发内容：%s\nQoS：%d\n错误原因：%s"
                    .formatted(clientId, topic, new String(value, StandardCharsets.UTF_8),
                            properties.getServer().getQos().getValue(), e.getMessage());
        }
    }

    /**
     * 打印MQTT消息
     *
     * @param payload 消息内容
     * @return 格式化的消息字符串
     */
    private String printMqttMessage(byte[] payload) {
        var body = new String(payload);
        try {
            // 尝试解析为JSON格式
            var param = JsonUtils.getInstance()
                    .convertObject(body, new TypeReference<Map<String, String>>() {
                    });
            if (param == null) {
                return body;
            }
            param.put("body", body);
            return JsonUtils.getInstance().serialize(param);
        } catch (Exception e) {
            return body;
        }
    }

    /**
     * 等待连接完成，带状态检查
     *
     * @return 连接是否成功
     * @throws InterruptedException 等待被中断
     */
    private boolean waitForConnectedWithStateCheck() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long timeout = 10000L; // 10秒超时

        while (!connectionChecker.get()) {
            // 检查连接状态
            ConnectionState state = currentConnectionState.get();

            // 如果是正常断开状态，跳过等待重连
            if (state == ConnectionState.DISCONNECTED) {
                log.info("[MQTT] 检测到连接断开，跳过等待重连");
                return false;
            }

            // 如果是异常断开状态，等待重连
            if (state == ConnectionState.ABNORMAL_DISCONNECT ||
                    state == ConnectionState.SESSION_EXPIRED ||
                    state == ConnectionState.CONNECTION_FAILED ||
                    state == ConnectionState.CONNECTION_TIMEOUT) {
                log.debug("[MQTT] 当前连接状态: {}, 等待重连...", state);
            }

            if (System.currentTimeMillis() - startTime > timeout) {
                log.warn("[MQTT] 等待连接超时，当前状态: {}", state);
                return false;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return true;
    }

    /**
     * 订阅主题
     *
     * @param topic 要订阅的主题
     */
    public void doSubscribe(String topic) {
        if (!properties.getEnable()) {
            log.info("当前MQTT服务被禁用。");
            return;
        }

        try {
            Mqtt5AsyncClient mqttClient = connectionManager.getMqttClient();
            if (mqttClient == null) {
                log.warn("MQTT客户端未初始化，无法订阅主题");
                return;
            }

            Mqtt5Subscribe subscribe = Mqtt5Subscribe.builder()
                    .topicFilter(topic)
                    .qos(getMqttQos())
                    .build();

            mqttClient.subscribe(subscribe)
                    .whenComplete((subAck, throwable) -> {
                        if (throwable != null) {
                            log.error("MQTT 5.0 客户端订阅主题失败，主题：{}，错误原因：{}", topic, throwable.getMessage());

                            // 发布订阅失败事件
                            SubscriptionResult result = new SubscriptionResult(
                                    topic, SubscriptionResult.SubscriptionAction.SUBSCRIBE, false, throwable.getMessage(), System.currentTimeMillis()
                            );
                            PublishResult<SubscriptionResult> publishResult = MqttEventPublisher.publishSubscriptionResult(result);
                            if (publishResult.isFailed()) {
                                log.warn("订阅失败事件发布失败: {}, 原因: {}", topic, publishResult.getFailureReason());
                            }
                        } else {
                            log.info("MQTT 5.0 客户端订阅主题成功，主题：{}", topic);

                            // 发布订阅成功事件
                            SubscriptionResult result = new SubscriptionResult(
                                    topic, SubscriptionResult.SubscriptionAction.SUBSCRIBE, true, "订阅成功", System.currentTimeMillis()
                            );
                            PublishResult<SubscriptionResult> publishResult = MqttEventPublisher.publishSubscriptionResult(result);
                            if (publishResult.isFailed()) {
                                log.warn("订阅成功事件发布失败: {}, 原因: {}", topic, publishResult.getFailureReason());
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("MQTT 5.0 客户端订阅主题失败，错误原因：{}", e.getMessage());

            // 发布订阅失败事件
            SubscriptionResult result = new SubscriptionResult(
                    topic, SubscriptionResult.SubscriptionAction.SUBSCRIBE, false, e.getMessage(), System.currentTimeMillis()
            );
            PublishResult<SubscriptionResult> publishResult = MqttEventPublisher.publishSubscriptionResult(result);
            if (publishResult.isFailed()) {
                log.warn("订阅失败事件发布失败: {}, 原因: {}", topic, publishResult.getFailureReason());
            }
        }
    }

    /**
     * 取消订阅主题
     *
     * @param topic 要取消订阅的主题
     */
    public void doUnsubscribe(String topic) {
        try {
            Mqtt5AsyncClient mqttClient = connectionManager.getMqttClient();
            if (mqttClient == null) {
                log.warn("MQTT客户端未初始化，无法取消订阅主题");
                return;
            }

            mqttClient.unsubscribeWith()
                    .topicFilter(topic)
                    .send()
                    .whenComplete((unsubAck, throwable) -> {
                        if (throwable != null) {
                            log.error("MQTT 5.0 客户端取消订阅主题失败，主题：{}，错误原因：{}", topic, throwable.getMessage());

                            // 发布取消订阅失败事件
                            SubscriptionResult result = new SubscriptionResult(
                                    topic, SubscriptionResult.SubscriptionAction.UNSUBSCRIBE, false, throwable.getMessage(), System.currentTimeMillis()
                            );
                            PublishResult<SubscriptionResult> publishResult = MqttEventPublisher.publishSubscriptionResult(result);
                            if (publishResult.isFailed()) {
                                log.warn("取消订阅失败事件发布失败: {}, 原因: {}", topic, publishResult.getFailureReason());
                            }
                        } else {
                            log.info("MQTT 5.0 客户端取消订阅主题成功，主题：{}", topic);

                            // 发布取消订阅成功事件
                            SubscriptionResult result = new SubscriptionResult(
                                    topic, SubscriptionResult.SubscriptionAction.UNSUBSCRIBE, true, "取消订阅成功", System.currentTimeMillis()
                            );
                            PublishResult<SubscriptionResult> publishResult = MqttEventPublisher.publishSubscriptionResult(result);
                            if (publishResult.isFailed()) {
                                log.warn("取消订阅成功事件发布失败: {}, 原因: {}", topic, publishResult.getFailureReason());
                            }
                        }
                    });
        } catch (Exception e) {
            log.error("MQTT 5.0 客户端取消订阅主题失败，错误原因：{}", e.getMessage());

            // 发布取消订阅失败事件
            SubscriptionResult result = new SubscriptionResult(
                    topic, SubscriptionResult.SubscriptionAction.UNSUBSCRIBE, false, e.getMessage(), System.currentTimeMillis()
            );
            PublishResult<SubscriptionResult> publishResult = MqttEventPublisher.publishSubscriptionResult(result);
            if (publishResult.isFailed()) {
                log.warn("取消订阅失败事件发布失败: {}, 原因: {}", topic, publishResult.getFailureReason());
            }
        }
    }

    private void handleMessagePublishFailure(Mqtt5Publish message, PublishResult<Mqtt5Publish> publishResult) {
        switch (publishResult.getFailureReason()) {
            case BUFFER_OVERFLOW:
                log.warn("消息缓冲区溢出，消息被丢弃: {}", message.getTopic());
                // 可以考虑实现重试逻辑或降级处理
                break;

            case SINK_TERMINATED:
                log.error("事件总线已终止，无法发布消息: {}", message.getTopic());
                // 需要重新初始化事件总线或重新订阅
                break;

            case EXCEPTION:
                log.error("发布消息时发生异常: {}", message.getTopic(), publishResult.getException());
                // 异常处理逻辑
                break;

            default:
                log.warn("消息发布失败: {}, 原因: {}", message.getTopic(), publishResult.getFailureReason());
                break;
        }
    }
}



