package com.richie.component.mqtt.client;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.mqtt.beans.ConsumerListener;
import com.richie.component.mqtt.beans.ConsumerMessage;
import com.richie.component.mqtt.beans.HeartbeatInfo;
import com.richie.component.mqtt.beans.MqttServerInfo;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.enums.ClientTypeEnum;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.exceptions.MqttConfigErrorException;
import com.richie.component.mqtt.exceptions.RydeenConsumerException;
import com.richie.component.mqtt.exceptions.RydeenMqttClientException;
import com.richie.component.mqtt.filter.handler.MessageHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import com.richie.component.mqtt.generator.impl.DefaultMqttClientDeviceIdGenerator;
import com.richie.component.mqtt.utils.ConnectionOptionWrapper;
import tools.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 阿里云MQTT客户端
 *
 * @author richie696
 * @version 1.0
 * @since 2022-09-09 13:38:33
 */
@Slf4j
@Component("mqtt_3")
@ConditionalOnProperty(prefix = "platform.component.mqtt", name = "mqtt-version", havingValue = "mqtt_3_1_1")
public final class PahoMqttClient extends AbstractMqttClientApi implements MqttClientApi {

    /**
     * 消息处理器（Paho 专用，处理 ConsumerMessage 类型消息）
     */
    private final MessageHandler<ConsumerMessage> messageHandler;

    /**
     * 阿里云 MQTT 客户端连接对象
     */
    private MqttClient mqttClient;

    /**
     * 心跳计划排程
     */
    private final ScheduledExecutorService heartbeatService = Executors.newScheduledThreadPool(1);

    /**
     * 队列持久化（推荐使用文件持久化，防止弱网或重启丢失消息）
     */
    private static final MqttClientPersistence FILE_PERSISTENCE = new MqttDefaultFilePersistence("/tmp/mqtt_persistence");

    /**
     * 心跳重试锁
     * <p>当发生断网时给心跳线程上锁，防止出现因为心跳包导致出现多线程订阅客户端的情况
     */
    private static final AtomicBoolean HB_RETRY_LOCK = new AtomicBoolean(false);

    private static final Object CONNECT_LOCK = new Object();

    /**
     * 发布消息最大重试次数
     */
    private static final int MAX_PUBLISH_RETRY = 5;
    /**
     * 发布消息重试间隔（毫秒）
     */
    private static final long PUBLISH_RETRY_INTERVAL_MS = 2000;

    /**
     * 构造Paho MQTT客户端（MQTT 3.1.1）
     *
     * @param properties MQTT客户端配置属性
     * @param deviceIdGenerator 设备ID生成器
     * @param messageHandler 消息处理器
     */
    public PahoMqttClient(MqttClientProperties properties, DefaultMqttClientDeviceIdGenerator deviceIdGenerator, @Qualifier("pahoMessageHandler") MessageHandler<ConsumerMessage> messageHandler) {
        this.properties = properties;
        this.messageHandler = messageHandler;
        this.deviceIdGenerator = deviceIdGenerator;
        if (!properties.getEnable()) {
            return;
        }
        if (Objects.isNull(properties.getServer())) {
            throw new RydeenMqttClientException("MQTT 客户端初始化异常，缺少必要的配置信息。");
        }
        this.networkType = properties.getServer().getDefaultNetworkType();
        if (this.properties.getClientType() == ClientTypeEnum.SERVER || properties.getInitClient()) {
            this.clientId = deviceIdGenerator.generateDeviceId();
            initService();
        }
    }

    /**
     * 下发消息的方法
     *
     * @param topic 下发消息的主题
     * @param value 下发的内容
     */
    @Override
    public void doPublish(String topic, byte[] value) {
        doPublish(topic, value, false);
    }

    @Override
    public void destroy() {
        if (Objects.isNull(mqttClient)) {
            return;
        }
        try {
            mqttClient.disconnect();
        } catch (Exception ignored) {
        } finally {
            try {
                mqttClient.close(true);
            } catch (Exception ignored) {
            }
            // 重要：置空引用，确保后续 initService() 可重建实例，避免在已关闭实例上复用
            this.mqttClient = null;
        }
    }

    @Override
    public void reinitialize() {
        initService();
    }

    @Override
    public void disconnect() {
        try {
            mqttClient.disconnect();
        } catch (MqttException ignored) {
        }
    }

    @Override
    protected void doSubscribe(String topic) {
        if (!properties.getEnable()) {
            log.info("当前MQTT服务被禁用。");
            return;
        }
        if (mqttClient == null) {
            log.warn("MQTT 客户端未初始化，无法订阅主题：{}", topic);
            return;
        }
        try {
            final var topicFilter = new String[]{topic};
            final var qos = new int[]{properties.getServer().getQos().getValue()};
            mqttClient.subscribe(topicFilter, qos);
        } catch (MqttException e) {
            log.error("MQTT客户端订阅主题失败，错误原因：{}", e.getMessage());
        }
    }

    @Override
    protected void doUnsubscribe(String topic) {
        if (mqttClient == null) {
            log.warn("MQTT 客户端未初始化，无法取消订阅主题：{}", topic);
            return;
        }
        try {
            mqttClient.unsubscribe(topic);
        } catch (MqttException e) {
            log.error("MQTT客户端取消订阅主题失败，错误原因：{}", e.getMessage());
        }
    }

    @Override
    public void registerConsumers(@Nonnull Set<ConsumerListener> listeners) {
        for (ConsumerListener listener : listeners) {
            registerConsumer(listener.getTopic(), listener.getCallback());
        }
    }

    @Override
    public void unregisterConsumers(@Nonnull String... topics) {
        for (var topic : topics) {
            unregisterConsumer(topic);
        }
    }

    @Override
    public void unregisterConsumers(@Nonnull Set<String> topics) {
        for (var topic : topics) {
            unregisterConsumer(topic);
        }
    }

    /**
     * MQTT 3.1.1 不支持共享订阅（Shared Subscriptions 是 MQTT 5.0 的特性）
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic）
     * @param callback    业务回调函数
     * @throws UnsupportedOperationException MQTT 3.1.1 协议不支持共享订阅
     */
    @Override
    public void registerSharedConsumer(@Nonnull String sharedTopic, @Nonnull Consumer<ConsumerMessage> callback) {
        throw new UnsupportedOperationException(
                "MQTT 3.1.1 协议不支持共享订阅（Shared Subscriptions），共享订阅是 MQTT 5.0 的特性。请使用 MQTT 5.0 客户端（HiveMqMqttClient）或升级到 MQTT 5.0 协议。");
    }

    /**
     * MQTT 3.1.1 不支持共享订阅（Shared Subscriptions 是 MQTT 5.0 的特性）
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic）
     * @throws UnsupportedOperationException MQTT 3.1.1 协议不支持共享订阅
     */
    @Override
    public void unregisterSharedConsumer(@Nonnull String sharedTopic) {
        throw new UnsupportedOperationException(
                "MQTT 3.1.1 协议不支持共享订阅（Shared Subscriptions），共享订阅是 MQTT 5.0 的特性。请使用 MQTT 5.0 客户端（HiveMqMqttClient）或升级到 MQTT 5.0 协议。");
    }

    /**
     * MQTT 3.1.1 不支持共享订阅（Shared Subscriptions 是 MQTT 5.0 的特性）
     *
     * @param sharedTopic 完整的共享订阅 topic（格式：$share/{groupId}/businessTopic）
     * @throws UnsupportedOperationException MQTT 3.1.1 协议不支持共享订阅
     */
    @Override
    protected void validateSharedTopic(@Nonnull String sharedTopic) {
        throw new UnsupportedOperationException(
                "MQTT 3.1.1 协议不支持共享订阅（Shared Subscriptions），共享订阅是 MQTT 5.0 的特性。请使用 MQTT 5.0 客户端（HiveMqMqttClient）或升级到 MQTT 5.0 协议。");
    }

    @Override
    public void initialClient(@Nonnull MqttServerInfo serverInfo) {
        // 如果配置无效则抛出异常
        if (serverInfo.isInvalid()) {
            throw new MqttConfigErrorException("服务器地址信息不能全部为空");
        }
        // 如果PUBLIC地址有效则执行
        if (serverInfo.isValidOfPublic()) {
            setPublicServer(serverInfo.getHost(), serverInfo.getPort());
        }
        // 如果VPC地址有效则执行
        if (serverInfo.isValidOfVpc()) {
            setVpcServer(serverInfo.getVpcHost(), serverInfo.getVpcPort());
        }
        setAuthorization(serverInfo.getUsername(), serverInfo.getPassword());
        this.properties.setGroupId(serverInfo.getGroupId());
        this.deviceIdGenerator.setDeviceId(serverInfo.getDeviceId());
        this.clientId = this.deviceIdGenerator.generateDeviceId();
        this.networkType = serverInfo.getNetworkType();
        this.properties.setType(serverInfo.getServerType());
        if (properties.getEnable()) {
            initService();
        }
    }

    @Override
    public void initialClient(@Nonnull MqttServerInfo serverInfo, boolean enable) {
        properties.setEnable(enable);
        initialClient(serverInfo);
    }

    @Override
    public void changeServer(@Nonnull NetworkTypeEnum networkType, @Nonnull String host, int port) {
        if (networkType == NetworkTypeEnum.PUBLIC) {
            setPublicServer(host, port);
        } else {
            setVpcServer(host, port);
        }
        this.networkType = networkType;
        // 使用连接复用方式切换网络
        switchNetworkConnection();
    }

    @Override
    public void changeServer(@Nonnull NetworkTypeEnum networkType) {
        if (this.networkType == networkType) {
            return;
        }
        this.networkType = networkType;
        // 使用连接复用方式切换网络
        switchNetworkConnection();
    }

    @Override
    public void changeServer() {
        this.networkType = this.networkType.getSwitchNetwork();
    }

    private boolean reconnect() {
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect(10000L);
            }
            mqttClient.connect(new ConnectionOptionWrapper(properties, clientId).getMqttConnectOptions());
            return mqttClient.isConnected();
        } catch (MqttException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[MQTT_LOG] 重连失败，线程ID：{} 错误原因：{}", Thread.currentThread().threadId(), e.getMessage());
            return false;
        }
    }

    private void setAuthorization(String username, String password) {
        properties.getServer().setUsername(username);
        properties.getServer().setPassword(password);
    }

    private void setPublicServer(String host, int port) {
        properties.getServer().setHost(host);
        properties.getServer().setPort(port);
    }

    private void setVpcServer(String host, int port) {
        properties.getServer().setVpcHost(host);
        properties.getServer().setVpcPort(port);
    }

    private void close() {
        destroy();
    }

    /**
     * 初始化服务的方法
     */
    private void initService() {
        synchronized (CONNECT_LOCK) {
            // 避免并发初始化；若已连接则直接返回
            if (this.mqttClient != null && this.mqttClient.isConnected()) {
                return;
            }

            close();
            ConnectionOptionWrapper wrapper;
            try {
                if (this.mqttClient == null) {
                    this.mqttClient = new MqttClient(properties.getServer().getServerUri(this.networkType), this.clientId, FILE_PERSISTENCE);
                    mqttClient.setTimeToWait(properties.getServer().getTimeToWait());
                    mqttClient.setCallback(new RydeenMqttCallback());
                }
                wrapper = new ConnectionOptionWrapper(properties, clientId);
                // 关闭驱动的自动重连，由本客户端托管
                wrapper.getMqttConnectOptions().setAutomaticReconnect(false);
                // 设置cleanSession为false，保证断线期间消息不丢失
                wrapper.getMqttConnectOptions().setCleanSession(false);
            } catch (MqttException | NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RydeenMqttClientException("MQTT 客户端初始化失败: " + e.getMessage(), e);
            }
            // 使用文件持久化，防止弱网或重启丢失消息
            boolean connected = false;
            int attempts = 0;
            final long baseBackoffMs = 5_000L; // 5s 起步
            final long maxBackoffMs = 300_000L; // 封顶 5 分钟
            while (!connected && !Thread.currentThread().isInterrupted()) {
                try {
                    if (!mqttClient.isConnected()) {
                        mqttClient.connect(wrapper.getMqttConnectOptions());
                    }
                    connected = true;
                    attempts = 0; // 成功后重置退避计数
                } catch (MqttException e) {
                    attempts++;
                    long exp = Math.min(maxBackoffMs, baseBackoffMs << Math.min(attempts, 10));
                    double jitterFactor = ThreadLocalRandom.current().nextDouble(0.2, 1.0); // 20%~100% 抖动
                    long sleepMs = Math.max(1_000L, (long) (exp * jitterFactor));
                    int reason = e.getReasonCode();
                    if (attempts % 10 == 0) {
                        log.error("[MQTT] 连接失败(uri={}, code={}, attempt={}), {}ms 后重试...",
                                properties.getServer().getServerUri(this.networkType), reason, attempts, sleepMs);
                    } else {
                        log.warn("[MQTT] 连接失败(uri={}, code={}, attempt={}), {}ms 后重试...",
                                properties.getServer().getServerUri(this.networkType), reason, attempts, sleepMs);
                    }
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        if (properties.getClientType() == ClientTypeEnum.CLIENT) {
            initConsumerHeartbeat();
        }
    }

    /**
     * 下发消息的方法
     *
     * @param topic    下发消息的主题
     * @param value    下发的内容
     * @param retained 是否在服务器中保留本条消息<p style="color:red">（注：服务器只会保留最新下发的1条消息在内存或磁盘文件中，当有新消息进行发送时会被最新的消息覆盖）
     */
    @Override
    public void doPublish(String topic, byte[] value, boolean retained) {
        ConsumerMessage body = new ConsumerMessage(value);
        body
                .setMessageId(UUID.randomUUID().toString())
                .setQos(properties.getServer().getQos().getValue())
                .setRetained(retained)
                .setTopic(topic);
        var message = new MqttMessage(JsonUtils.getInstance().serializeBytes(body));
        boolean published = false;
        int retry = 0;
        while (retry < MAX_PUBLISH_RETRY) {
            var result = publishContent(topic, message);
            if (StringUtils.isBlank(result)) {
                published = true;
                break;
            }
            log.warn("[MQTT] 发布失败，重试第{}次...", retry + 1);
            synchronized (CONNECT_LOCK) {
                if (!mqttClient.isConnected()) {
                    log.info("[MQTT] 连接断开，正在尝试重连...");
                    reconnectWithNetworkSwitch();
                }
            }
            retry++;
            try {
                Thread.sleep(PUBLISH_RETRY_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (!published) {
            log.error("[MQTT] 消息发布最终失败，主题：{}", topic);
        }
        HB_RETRY_LOCK.set(false);
    }

    private String publishContent(String topic, MqttMessage message) {
        if (mqttClient == null) {
            return "[MQTT] 客户端未初始化，无法发布消息 (topic=" + topic + ")";
        }
        try {
            mqttClient.publish(topic, message);
            // 生产环境仅打印元信息，消息体放到 debug
            log.info("[MQTT] 主题：{}，发送成功。QoS={}, retained={}", topic, message.getQos(), message.isRetained());
            if (log.isDebugEnabled()) {
                log.debug("[MQTT] 发送内容：{}", printMqttMessage(message));
            }
            return "";
        } catch (MqttException e) {
            return
                    "[MQTT] 消息发布失败！\n客户端ID：%s\n订阅主题：%s\n下发内容：%s\nQoS：%d\n错误原因：%s"
                            .formatted(clientId, topic, new String(message.getPayload(), StandardCharsets.UTF_8),
                                    properties.getServer().getQos().getValue(), e.getMessage());
        }
    }

    private String printMqttMessage(MqttMessage message) {
        var body = new String(message.getPayload());
        Map<String, String> param = JsonUtils.getInstance().convertObject(message, new TypeReference<>() {
        });
        if (param == null) {
            return body;
        }
        param.remove("payload");
        param.put("body", body);
        return JsonUtils.getInstance().serialize(param, true);
    }

    private void initConsumerHeartbeat() {
        // 仅在 Client 模式下启用心跳；Server 模式默认不启用
        if (properties.getClientType() != null && properties.getClientType() != ClientTypeEnum.CLIENT) {
            log.info("[MQTT_LOG] 心跳未启动：当前 clientType = {}，仅在 CLIENT 模式下启用心跳，clientId={}",
                    properties.getClientType(), clientId);
            return;
        }

        heartbeatService.scheduleWithFixedDelay(() -> {
            try {
                if (!mqttClient.isConnected()) {
                    boolean reconnectResult = false;
                    int retry = 0;
                    while (!reconnectResult && retry < 3) {
                        reconnectResult = reconnectWithNetworkSwitch();
                        if (!reconnectResult) {
                            log.warn("[MQTT_LOG] 客户端自动重连失败（线程ID：{}），重试第{}次", Thread.currentThread().threadId(), retry + 1);
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException ignored) {
                            }
                        }
                        retry++;
                    }
                    if (!reconnectResult) {
                        log.error("[MQTT_LOG] 客户端重连失败，放弃本次心跳");
                        return;
                    }
                }
                if (HB_RETRY_LOCK.get()) {
                    return;
                }
                HB_RETRY_LOCK.set(true);
                var sendTime = LocalDateTime.now();
                log.debug("心跳包 发送时间 = {}, 客户端ID = {}", sendTime, clientId);
                var topic = getHeartbeatTopic();
                byte[] hb = new HeartbeatInfo(clientId, sendTime, 0L).toPayload();
                doPublish(topic, hb);
            } catch (Exception e) {
                log.error("心跳失败，错误原因：{}", e.getMessage(), e);
            }
        }, 0, properties.getFastRecovery().getKeepAliveInterval(), TimeUnit.SECONDS);
    }

    /**
     * 阿里云MQTT消息回执
     *
     * @author richie696
     * @version 1.0
     * @since 2022-09-11 23:28:19
     */
    class RydeenMqttCallback implements MqttCallbackExtended {

        /**
         * 连接成功时触发的回调事件
         *
         * @param reconnect 是否自动重连（如果为true，则表示服务器会自动重连）
         * @param serverUri 连接的服务器URI地址
         */
        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
            log.info("[MQTT_LOG] 连接成功。(Server URI = {}, Reconnect = {})", serverUri, reconnect);
            // MQTT 3.1.1 只支持普通订阅，重新订阅所有普通订阅主题
            LISTENER_CACHE.keySet().forEach(topic -> {
                try {
                    final var topicFilter = new String[]{topic};
                    final var qos = new int[]{properties.getServer().getQos().getValue()};
                    mqttClient.subscribe(topicFilter, qos);
                } catch (MqttException e) {
                    log.error("MQTT客户端订阅主题失败，错误原因：{}", e.getMessage());
                }
            });
        }

        /**
         * 连接丢失时触发的回调事件
         *
         * @param throwable the reason behind the loss of connection.
         */
        @Override
        public void connectionLost(Throwable throwable) {
            log.warn("[MQTT_LOG] 连接已断开，原因：{}，自动重连已开启", throwable.getMessage());
            if (log.isDebugEnabled() && throwable.getCause() != null) {
                log.debug("[MQTT_LOG] 断连详细: ", throwable);
            }
            // 自动重连由Paho内部处理，这里只做日志
        }

        /**
         * 接收到消息时触发的回调事件
         *
         * @param topic         消息来源的主题名称
         * @param originMessage 接收到的消息
         */
        @Override
        public void messageArrived(String topic, MqttMessage originMessage) throws Exception {
            /*
             * 消费消息的回调接口，需要确保该接口不抛异常，该接口运行返回即代表消息消费成功。
             * 消费消息需要保证在规定时间内完成，如果消费耗时超过服务端约定的超时时间，对于
             * 可靠传输的模式，服务端可能会重试推送，业务需要做好幂等去重处理。
             */
            log.info("[MQTT_LOG] 收到来自 {} 主题的消息，消息体为：{}", topic, printMqttMessage(originMessage));

            // MQTT 3.1.1 只支持普通订阅，不支持共享订阅
            if (!LISTENER_CACHE.containsKey(topic)) {
                throw new RydeenConsumerException(
                        "当前消息没有有效的通知订阅，请检查消息监听器是否正确注册。(topic = %s, messageId = %d)"
                                .formatted(topic, originMessage.getId()));
            }

            // 去重后转换为通用消息体
            var consumerMessage = JsonUtils.getInstance().deserializePayload(originMessage.getPayload(), ConsumerMessage.class);

            // 幂等去重处理
            if (messageHandler.isDuplicate(consumerMessage)) {
                log.info("消息去重成功。");
                return;
            }
            messageHandler.saveCache(consumerMessage, TimeUnit.MINUTES.toMillis(10));

            // 通知给业务接口
            LISTENER_CACHE.get(topic).accept(consumerMessage);
        }

        /**
         * 投递消息成功后的回调事件
         *
         * <p>当消息的传递已经完成，并且所有ACK确认已经收到时才会调用该回调函数。
         * <ul>
         *     <li>当 QoS = 0时，一旦消息被传递给网络进行传递，它就会被调用。</li>
         *     <li>当 QoS = 1时，当接收到PUBACK时调用；</li>
         *     <li>当 QoS = 2时，当接收到PUBCOMP时调用。令牌将与消息发布时返回的令牌相同。</li>
         * </ul>
         *
         * @param token 与消息关联的投递令牌
         */
        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            log.info("[MQTT_LOG] 投递 {} 主题消息成功。", token.getTopics()[0]);
        }

    }

    /**
     * 网络切换连接方法 - 复用现有客户端资源
     * 避免完全重建客户端，减少资源浪费
     */
    private void switchNetworkConnection() {
        if (mqttClient == null) {
            // 如果客户端不存在，则初始化
            initService();
            return;
        }

        try {
            // 1. 断开当前连接但保留客户端实例
            if (mqttClient.isConnected()) {
                mqttClient.disconnect(5000L); // 5秒超时断开
            }

            // 2. 更新客户端服务器地址
            String newServerUri = properties.getServer().getServerUri(this.networkType);
            log.info("HiveMQ 切换到新服务器: {}", newServerUri);

            // 3. 重新连接到新服务器
            ConnectionOptionWrapper wrapper = new ConnectionOptionWrapper(properties, clientId);
            wrapper.getMqttConnectOptions().setAutomaticReconnect(true);
            wrapper.getMqttConnectOptions().setCleanSession(false);

            mqttClient.connect(wrapper.getMqttConnectOptions());

            log.info("HiveMQ 网络切换完成，当前网络类型: {}", this.networkType);

        } catch (MqttException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HiveMQ 网络切换失败: {}", e.getMessage());
            // 如果切换失败，回退到重建方式
            log.warn("HiveMQ 网络切换失败，回退到重建客户端方式");
            initService();
        }
    }

    /**
     * 优化的重连方法 - 优先尝试连接复用
     */
    private boolean reconnectWithNetworkSwitch() {
        try {
            // 先尝试在当前网络重连
            if (reconnect()) {
                return true;
            }

            // 当前网络重连失败，尝试切换网络
            log.warn("HiveMQ 当前网络重连失败，尝试切换网络");
            if (this.networkType == NetworkTypeEnum.PUBLIC) {
                // 检查VPC配置是否存在
                if (properties.getServer().getVpcHost() != null && properties.getServer().getVpcPort() != 0) {
                    log.info("HiveMQ 从公网切换到VPC网络进行重连");
                    this.networkType = NetworkTypeEnum.VPC;
                    return reconnect();
                }
            } else {
                // 检查公网配置是否存在
                if (properties.getServer().getHost() != null && properties.getServer().getPort() != 0) {
                    log.info("HiveMQ 从VPC网络切换到公网进行重连");
                    this.networkType = NetworkTypeEnum.PUBLIC;
                    return reconnect();
                }
            }

            return false;
        } catch (Exception e) {
            log.error("HiveMQ 网络切换重连失败: {}", e.getMessage());
            return false;
        }
    }

}

