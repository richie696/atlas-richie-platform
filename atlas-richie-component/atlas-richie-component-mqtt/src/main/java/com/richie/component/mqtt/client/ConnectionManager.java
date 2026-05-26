package com.richie.component.mqtt.client;

import com.richie.component.mqtt.beans.ConnectionState;
import com.richie.component.mqtt.beans.ConnectionStateEvent;
import com.richie.component.mqtt.config.FastRecoveryConfig;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.config.ServerInfo;
import com.richie.component.mqtt.enums.MqttProtocolEnum;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.richie.component.mqtt.exceptions.RydeenMqttClientException;
import com.richie.component.mqtt.utils.MqttSslUtils;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.richie.component.mqtt.beans.ConnectionState.ABNORMAL_DISCONNECT;
import static com.richie.component.mqtt.beans.ConnectionState.SESSION_EXPIRED;

/**
 * MQTT连接管理器
 * <p>
 * 负责MQTT客户端的连接建立、断开、重连等连接相关操作。该类是整个MQTT组件的核心连接管理组件，
 * 负责维护客户端与MQTT服务器之间的连接状态，处理网络切换、重连恢复等场景。
 * <p>
 * <strong>组件生命周期阶段：</strong>
 * <ul>
 *   <li><strong>初始化阶段</strong>：构造函数、initial()方法</li>
 *   <li><strong>连接建立阶段</strong>：connect()、validateServerConfig()等方法</li>
 *   <li><strong>运行维护阶段</strong>：状态监控、网络切换、重连等</li>
 *   <li><strong>清理阶段</strong>：disconnect()、资源释放等</li>
 * </ul>
 * <p>
 * <strong>主要职责：</strong>
 * <ul>
 *   <li>管理MQTT客户端连接的生命周期</li>
 *   <li>处理网络类型切换（公网/VPC）</li>
 *   <li>实现智能重连和故障恢复</li>
 *   <li>提供连接状态监控和事件广播</li>
 *   <li>支持多网络环境下的服务器配置</li>
 *   <li><strong>自动重试机制</strong>：首次连接失败后自动重试，直到连接成功（使用指数退避 + 抖动策略）</li>
 * </ul>
 * <p>
 * <strong>自动重试特性：</strong>
 * <ul>
 *   <li><strong>无限重试模式</strong>：当配置 {@code max-fast-reconnect-attempts <= 0} 时，会无限重试直到连接成功</li>
 *   <li><strong>有限重试模式</strong>：当配置 {@code max-fast-reconnect-attempts > 0} 时，达到最大次数后停止重试</li>
 *   <li><strong>指数退避策略</strong>：重试间隔按指数增长（1倍、2倍、4倍...），最大不超过5分钟</li>
 *   <li><strong>随机抖动</strong>：每次重试间隔添加20%~100%的随机抖动，避免多个客户端同时重试</li>
 *   <li><strong>线程安全</strong>：使用同步锁和原子变量确保重试过程的线程安全</li>
 *   <li><strong>支持中断</strong>：支持线程中断，中断后会立即停止重试</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Slf4j
public class ConnectionManager {

    // ==================== 静态常量 ====================

    /**
     * 连接锁
     */
    private static final Object CONNECT_LOCK = new Object();

    /**
     * 等待连接成功超时时间（毫秒）
     */
    private static final long WAIT_CONNECTED_TIMEOUT = 10000L;

    // ==================== 实例变量 ====================

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
    private NetworkTypeEnum networkType;

    /**
     * MQTT 5.0异步客户端
     */
    @Getter
    private Mqtt5AsyncClient mqttClient;

    /**
     * 连接状态标志
     */
    private final AtomicBoolean isConnected = new AtomicBoolean(false);

    /**
     * 重连标志
     */
    @Getter
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    /**
     * 最后连接时间
     */
    private final AtomicLong lastConnectedTime = new AtomicLong(0);

    /**
     * 当前连接开始时间
     */
    private final AtomicLong currentConnectionStartTime = new AtomicLong(0);

    /**
     * 连接状态广播线程
     */
    private final AtomicBoolean isBroadcast = new AtomicBoolean(true);

    /**
     * 连接配置构建器
     */
    private ConnectionConfigBuilder configBuilder;

    // ==================== 构造函数 ====================

    /**
     * 构造MQTT连接管理器
     * <p>
     * <strong>生命周期阶段：</strong> 初始化阶段
     * <p>
     * <strong>功能说明：</strong>
     * 创建MQTT连接管理器实例，初始化必要的组件和后台服务。
     * 构造函数会启动连接状态广播、订阅网络类型变化事件等。
     * <p>
     * <strong>初始化内容：</strong>
     * <ul>
     *   <li>保存客户端配置和ID</li>
     *   <li>启动连接状态广播线程</li>
     *   <li>订阅网络类型变化事件</li>
     *   <li>创建连接配置构建器</li>
     * </ul>
     * <p>
     * <strong>参数要求：</strong>
     * <ul>
     *   <li>properties：MQTT客户端配置，不能为null</li>
     *   <li>clientId：客户端唯一标识，不能为空</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>构造函数会立即启动后台服务</li>
     *   <li>使用虚拟线程提高性能</li>
     *   <li>支持优雅关闭和资源清理</li>
     *   <li>异常情况下会记录日志但不会中断初始化</li>
     * </ul>
     *
     * @param properties MQTT客户端配置
     * @param clientId   客户端唯一标识
     * @throws IllegalArgumentException 当参数无效时
     */
    public ConnectionManager(MqttClientProperties properties, String clientId) {
        this.properties = properties;
        this.clientId = clientId;
        this.networkType = properties.getServer().getDefaultNetworkType();
        this.configBuilder = new ConnectionConfigBuilder(properties, clientId, networkType);

        // 设置连接开始时间为当前时间
        this.configBuilder.setConnectionStartTime(System.currentTimeMillis());

        initial();
    }

    // ==================== 公共方法 - 连接管理 ====================

    /**
     * 建立MQTT连接（带自动重试）
     * <p>
     * <strong>生命周期阶段：</strong> 连接建立阶段
     * <p>
     * <strong>功能说明：</strong>
     * 建立与MQTT服务器的连接，包括配置验证、客户端构建、连接建立和状态管理。
     * 该方法使用同步锁确保连接过程的线程安全，支持连接超时和失败重试。
     * <strong>首次连接失败后会自动重试，直到连接成功为止</strong>（使用指数退避 + 抖动策略）。
     * <p>
     * <strong>自动重试机制：</strong>
     * <ul>
     *   <li><strong>重试策略</strong>：指数退避 + 随机抖动</li>
     *   <li><strong>重试间隔计算</strong>：
     *     <ul>
     *       <li>第1次重试：基础间隔 × 1 × 抖动因子（20%~100%）</li>
     *       <li>第2次重试：基础间隔 × 2 × 抖动因子</li>
     *       <li>第3次重试：基础间隔 × 4 × 抖动因子</li>
     *       <li>以此类推，最大退避时间不超过5分钟</li>
     *     </ul>
     *   </li>
     *   <li><strong>无限重试模式</strong>：当配置的 {@code max-fast-reconnect-attempts <= 0} 时，会无限重试直到连接成功</li>
     *   <li><strong>有限重试模式</strong>：当配置的 {@code max-fast-reconnect-attempts > 0} 时，达到最大次数后停止重试</li>
     *   <li><strong>抖动策略</strong>：每次重试间隔添加20%~100%的随机抖动，避免多个客户端同时重试造成服务器压力</li>
     * </ul>
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查当前连接状态，如果已连接则直接返回</li>
     *   <li>检查重连状态，如果正在重连则跳过</li>
     *   <li>读取快速恢复配置（重试间隔、最大重试次数等）</li>
     *   <li>进入重试循环：
     *     <ul>
     *       <li>执行单次连接尝试（调用 {@link #attemptConnect(boolean)}）</li>
     *       <li>如果连接成功，立即返回</li>
     *       <li>如果连接失败，计算退避时间并等待后重试</li>
     *       <li>如果达到最大重试次数（有限模式），停止重试并返回失败</li>
     *     </ul>
     *   </li>
     *   <li>更新连接状态和事件广播</li>
     * </ol>
     * <p>
     * <strong>配置说明：</strong>
     * <ul>
     *   <li>{@code fast-reconnect-interval}：基础重试间隔（毫秒），默认1000ms</li>
     *   <li>{@code max-fast-reconnect-attempts}：最大重试次数，0或负数表示无限重试，默认10次</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>如果已连接则直接返回true，不会重复连接</li>
     *   <li>使用同步锁防止并发连接，确保线程安全</li>
     *   <li>支持连接超时配置，超时后会触发重试</li>
     *   <li>自动处理连接失败分析，记录详细的错误信息</li>
     *   <li>重连过程中会阻止新的连接建立请求，避免资源浪费</li>
     *   <li>使用指数退避 + 抖动策略，避免对服务器造成过大压力</li>
     *   <li>支持线程中断，中断后会立即停止重试</li>
     *   <li>每10次重试输出ERROR级别日志，其他重试输出WARN级别日志</li>
     * </ul>
     * <p>
     * <strong>使用示例：</strong>
     * <pre>{@code
     * // 配置无限重试（推荐生产环境）
     * platform.component.mqtt.fast-recovery.max-fast-reconnect-attempts: 0
     *
     * // 配置有限重试（最多10次）
     * platform.component.mqtt.fast-recovery.max-fast-reconnect-attempts: 10
     * platform.component.mqtt.fast-recovery.fast-reconnect-interval: 1000
     * }</pre>
     *
     * @return 连接是否成功（true：连接成功，false：连接失败且达到最大重试次数）
     * @throws RuntimeException 当连接过程中发生严重错误时
     * @see #attemptConnect(boolean)
     * @see FastRecoveryConfig
     */
    public boolean connect() {
        // 使用同步锁防止并发连接，确保线程安全
        synchronized (CONNECT_LOCK) {
            // 检查是否已连接，如果已连接则直接返回
            if (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
                return true;
            }

            // 重连状态检查：如果重连正在进行，阻止新的连接建立
            if (reconnecting.get()) {
                log.debug("重连正在进行中，跳过新的连接建立请求，客户端ID: {}", clientId);
                return false;
            }

            // ==================== 自动重试配置 ====================
            // 获取快速恢复配置，用于控制重试行为
            var fastRecoveryConfig = properties.getFastRecovery();

            // 基础退避时间：从配置中获取，默认1000ms
            // 这是第一次重试的基准间隔时间
            long baseBackoffMs = fastRecoveryConfig.getFastReconnectInterval();

            // 最大退避时间：基础间隔的32倍，但不超过5分钟（300秒）
            // 防止退避时间过长，影响连接恢复速度
            long maxBackoffMs = Math.max(baseBackoffMs * 32, 300_000L);

            // 最大重试次数：从配置中获取
            // 如果配置为0或负数，则启用无限重试模式（直到连接成功）
            int maxAttempts = fastRecoveryConfig.getMaxFastReconnectAttempts();
            boolean infiniteRetry = maxAttempts <= 0;

            // ==================== 重试循环 ====================
            int attempts = 0;

            // 自动重试循环：持续重试直到连接成功或达到最大重试次数
            // 支持线程中断，中断后会立即停止重试
            // 注意：每次循环迭代前都要检查连接状态，避免重复连接
            for (; ; ) {
                // 检查线程中断状态
                if (Thread.currentThread().isInterrupted()) {
                    log.warn("连接重试被中断，客户端ID: {}", clientId);
                    return false;
                }

                // 检查是否已连接（可能在重试过程中其他线程已成功连接）
                if (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
                    log.debug("检测到连接已建立，退出重试循环，客户端ID: {}", clientId);
                    return true;
                }

                attempts++;

                // 执行单次连接尝试
                // isFirstAttempt=true 表示首次尝试，会输出诊断信息
                boolean connected = attemptConnect(attempts == 1);

                // 连接成功：立即退出循环，恢复正常运行
                if (connected) {
                    log.info("MQTT 5.0 连接成功，客户端ID: {}, 尝试次数: {}", clientId, attempts);
                    return true;
                }

                // ==================== 重试次数检查 ====================
                // 如果达到最大重试次数且不是无限重试模式，则停止重试
                // 无限重试模式（maxAttempts <= 0）会一直重试直到连接成功
                if (!infiniteRetry && attempts >= maxAttempts) {
                    log.error("MQTT 5.0 连接失败，已达到最大重试次数: {}, 客户端ID: {}", maxAttempts, clientId);
                    return false;
                }

                // ==================== 指数退避 + 抖动策略 ====================
                // 指数退避计算：每次重试间隔翻倍，最大不超过 maxBackoffMs
                // 公式：baseBackoffMs × 2^(attempts-1)，最大指数为10（即最多翻倍10次）
                // 例如：baseBackoffMs=1000ms
                //   - 第1次重试：1000 × 2^0 = 1000ms
                //   - 第2次重试：1000 × 2^1 = 2000ms
                //   - 第3次重试：1000 × 2^2 = 4000ms
                //   - 第11次及以后：保持 maxBackoffMs（5分钟）
                long exp = Math.min(maxBackoffMs, baseBackoffMs << Math.min(attempts - 1, 10));

                // 抖动因子：20%~100% 的随机值
                // 作用：避免多个客户端同时重试造成服务器压力（thundering herd problem）
                // 例如：如果计算出的退避时间为2000ms，抖动后可能是 400ms ~ 2000ms 之间的随机值
                double jitterFactor = ThreadLocalRandom.current().nextDouble(0.2, 1.0);

                // 最终退避时间：指数退避时间 × 抖动因子，最小不低于1000ms
                // 确保即使抖动后也不会太快重试，避免对服务器造成压力
                long sleepMs = Math.max(1000L, (long) (exp * jitterFactor));

                // ==================== 日志输出策略 ====================
                // 每10次重试输出ERROR级别日志，其他重试输出WARN级别日志
                // 避免日志过多，同时保证重要信息能被记录
                if (attempts % 10 == 0) {
                    log.error("MQTT 5.0 连接失败，第 {} 次尝试，{}ms 后重试...", attempts, sleepMs);
                } else {
                    log.warn("MQTT 5.0 连接失败，第 {} 次尝试，{}ms 后重试...", attempts, sleepMs);
                }

                // ==================== 退避等待 ====================
                // 在下次重试前等待计算出的退避时间
                // 支持线程中断，中断后会立即停止重试
                // 注意：在等待期间释放锁，允许其他线程检查连接状态
                try {
                    // 在等待前释放锁，避免阻塞其他操作
                    // 等待期间，如果连接成功，下次循环会检测到并退出
                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("连接重试被中断，客户端ID: {}", clientId);
                    return false;
                }
            }
        }
    }

    /**
     * 执行单次连接尝试
     * <p>
     * <strong>功能说明：</strong>
     * 执行一次MQTT连接尝试，包括配置验证、客户端构建、连接建立等。
     * 该方法被 {@link #connect()} 方法调用，用于实现自动重试机制。
     * 每次重试都会调用此方法执行一次完整的连接流程。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>验证服务器配置（服务器地址、认证信息等）</li>
     *   <li>如果是首次尝试，输出连接诊断信息（便于问题排查）</li>
     *   <li>断开现有连接（如果存在）</li>
     *   <li>构建MQTT 5.0异步客户端实例</li>
     *   <li>构建连接选项（KeepAlive、会话过期时间等）</li>
     *   <li>发起异步连接请求</li>
     *   <li>注册连接回调处理（成功/失败事件）</li>
     *   <li>阻塞等待连接完成或超时</li>
     *   <li>等待回调执行完成，确保状态更新</li>
     *   <li>更新连接状态和事件广播</li>
     * </ol>
     * <p>
     * <strong>线程安全：</strong>
     * <ul>
     *   <li>使用同步锁 {@code CONNECT_LOCK} 确保连接过程的线程安全</li>
     *   <li>使用原子变量 {@code connectionResult} 和 {@code callbackExecuted} 跟踪连接结果</li>
     *   <li>支持并发场景下的安全重试</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>诊断信息仅在首次尝试时输出，避免日志过多</li>
     *   <li>每次重试都会创建新的客户端实例，确保连接状态干净</li>
     *   <li>使用异步连接，通过 {@code CompletableFuture} 处理连接结果</li>
     *   <li>连接超时后会取消连接请求，避免资源泄漏</li>
     *   <li>回调执行完成后才会返回结果，确保状态一致性</li>
     * </ul>
     *
     * @param isFirstAttempt 是否为首次尝试
     *                       <ul>
     *                         <li>{@code true}：首次尝试，会输出连接诊断信息</li>
     *                         <li>{@code false}：重试尝试，不输出诊断信息（避免日志过多）</li>
     *                       </ul>
     * @return 连接是否成功
     * <ul>
     *   <li>{@code true}：连接成功，可以正常使用</li>
     *   <li>{@code false}：连接失败，会触发重试机制</li>
     * </ul>
     * @see #connect()
     */
    private boolean attemptConnect(boolean isFirstAttempt) {
        // 注意：此方法在 connect() 方法的 synchronized (CONNECT_LOCK) 块内调用
        // 因此不需要再次加锁，避免死锁
        try {
            // 设置重连标志，防止并发连接
            if (!reconnecting.compareAndSet(false, true)) {
                // 如果重连标志已设置，说明其他线程正在连接，直接返回
                log.debug("连接正在进行中，跳过重复连接尝试，客户端ID: {}", clientId);
                return mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED;
            }

            // 再次检查连接状态（可能在设置 reconnecting 标志期间连接已成功）
            if (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
                reconnecting.set(false);
                return true;
            }

            // 验证服务器配置
            validateServerConfig();

            // 获取服务器地址和端口
            String host = getServerHost();
            int port = getServerPort();

            // 连接前诊断（仅在首次尝试时输出）
            if (isFirstAttempt) {
                diagnoseBeforeConnection(host, port);
            }

            if (mqttClient != null) {
                mqttClient.disconnect();
            }

            // 构建MQTT客户端
            var clientBuilder = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier(clientId)
                    .serverHost(host)
                    .serverPort(port);

            // 配置 SSL/TLS（如果需要）
            MqttProtocolEnum protocol = properties.getServer().getProtocol();
            if (MqttSslUtils.needsSsl(protocol)) {
                try {
                    ServerInfo.Ssl ssl = properties.getServer().getSsl();

                    // 使用公共工具类创建 TrustManagerFactory 和 KeyManagerFactory
                    TrustManagerFactory trustManagerFactory = MqttSslUtils.createTrustManagerFactory(ssl, protocol);
                    KeyManagerFactory keyManagerFactory = MqttSslUtils.createKeyManagerFactory(ssl, protocol);

                    // HiveMQ 使用 sslConfig() 方法配置 SSL/TLS
                    // 根据 HiveMQ 1.3.12 的 API，使用 sslConfig() 方法配置 SSL 上下文
                    // 注意：HiveMQ 的 sslConfig() 方法链式调用，需要调用 applySslConfig() 应用配置
                    var sslConfigBuilder = clientBuilder.sslConfig();

                    if (trustManagerFactory != null) {
                        sslConfigBuilder = sslConfigBuilder.trustManagerFactory(trustManagerFactory);
                    }
                    if (keyManagerFactory != null) {
                        sslConfigBuilder = sslConfigBuilder.keyManagerFactory(keyManagerFactory);
                    }

                    // 禁用主机名验证（适用于简单TLS模式和某些自签名证书场景）
                    sslConfigBuilder = sslConfigBuilder.hostnameVerifier((hostname, session) -> true);

                    // 应用 SSL 配置
                    sslConfigBuilder.applySslConfig();

                    log.debug("已配置 SSL/TLS 连接 - 协议: {}, 客户端ID: {}, X.509证书: {}",
                            protocol, clientId, (trustManagerFactory != null && keyManagerFactory != null));
                } catch (Exception e) {
                    log.error("配置 SSL/TLS 失败: {}, 客户端ID: {}", e.getMessage(), clientId, e);
                    throw new RydeenMqttClientException("SSL/TLS 配置失败: " + e.getMessage(), e);
                }
            }

            mqttClient = clientBuilder.buildAsync();

            // 构建连接选项
            Mqtt5Connect connect = configBuilder.buildMqtt5Connect();

            // 发起异步连接请求
            // 使用 CompletableFuture 处理异步连接结果，不阻塞当前线程
            CompletableFuture<Mqtt5ConnAck> connectFuture = mqttClient.connect(connect);

            // ==================== 连接结果跟踪 ====================
            // 使用原子变量跟踪连接结果，确保线程安全
            // connectionResult：连接是否成功（true=成功，false=失败）
            // callbackExecuted：回调是否已执行（用于确保状态更新完成）
            final AtomicBoolean connectionResult = new AtomicBoolean(false);
            final AtomicBoolean callbackExecuted = new AtomicBoolean(false);

            // ==================== 连接回调处理 ====================
            // 注册连接完成回调，处理连接成功/失败的情况
            // 回调会在连接完成（成功或失败）时异步执行
            connectFuture.whenComplete((connAck, ex) -> {
                callbackExecuted.set(true);
                reconnecting.set(false);
                if (ex != null) {
                    log.error("MQTT 5.0 连接失败: {}", ex.getMessage());
                    analyzeConnectionFailure(ex);
                    isConnected.set(false);
                    connectionResult.set(false);
                    PublishResult<ConnectionStateEvent> publishResult = MqttEventPublisher.publishConnectionState(ConnectionStateEvent.of(clientId, ConnectionState.CONNECTION_FAILED, networkType));
                    if (publishResult.isFailed()) {
                        log.warn("连接失败事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                        handleConnectionStatePublishFailure(ConnectionState.CONNECTION_FAILED, publishResult);
                    }
                } else {
                    log.info("MQTT 5.0 连接成功 - 客户端ID: {}, 服务器: {}:{}, 网络类型: {}",
                            clientId, host, port, networkType.name());

                    // 记录连接成功时间
                    long currentTime = System.currentTimeMillis();
                    lastConnectedTime.set(currentTime);

                    // 只在首次连接建立时记录连接开始时间
                    if (currentConnectionStartTime.get() == 0) {
                        currentConnectionStartTime.set(currentTime);
                        log.debug("记录连接开始时间: {}", currentTime);
                    }
                    isConnected.set(true);
                    connectionResult.set(true);
                    PublishResult<ConnectionStateEvent> publishResult = MqttEventPublisher.publishConnectionState(ConnectionStateEvent.of(clientId, ConnectionState.CONNECTED, networkType));
                    if (publishResult.isFailed()) {
                        log.warn("连接成功事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                        handleConnectionStatePublishFailure(ConnectionState.CONNECTED, publishResult);
                    }
                }
            });

            // ==================== 等待连接完成 ====================
            // 阻塞等待连接完成或超时
            // 超时时间从配置中获取（getConnectionTimeout()）
            try {
                // 阻塞等待连接完成，最多等待 getConnectionTimeout() 秒
                // 如果连接成功，connectFuture.get() 会返回 Mqtt5ConnAck，不会抛出异常
                // 如果连接失败，connectFuture.get() 会抛出异常
                Mqtt5ConnAck connAck = connectFuture.get(getConnectionTimeout(), TimeUnit.SECONDS);

                // 等待回调执行完成，确保连接状态已更新
                // 最多等待2秒，确保回调有足够时间执行
                long waitStart = System.currentTimeMillis();
                long maxWaitMs = 2000L; // 增加到2秒，确保回调有足够时间执行
                while (!callbackExecuted.get() && (System.currentTimeMillis() - waitStart) < maxWaitMs) {
                    TimeUnit.MILLISECONDS.sleep(10);
                }

                // 检查回调是否已执行
                if (!callbackExecuted.get()) {
                    log.warn("连接回调未在 {}ms 内执行，客户端ID: {}，但连接可能已成功", maxWaitMs, clientId);
                    // 即使回调未执行，如果 connectFuture.get() 没有抛出异常，说明连接已成功
                    // 直接检查客户端状态
                    if (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
                        log.info("通过客户端状态检查确认连接成功，客户端ID: {}", clientId);
                        isConnected.set(true);
                        reconnecting.set(false);
                        return true;
                    }
                }

                // 返回连接结果：优先使用回调结果，如果回调未执行则使用客户端状态
                // 双重检查确保连接真正成功
                boolean result = callbackExecuted.get()
                        ? (connectionResult.get() && mqttClient.getState() == MqttClientState.CONNECTED)
                        : (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED);

                if (result && !isConnected.get()) {
                    // 如果连接成功但 isConnected 标志未设置，则设置它
                    isConnected.set(true);
                    log.debug("连接成功，已更新 isConnected 标志，客户端ID: {}", clientId);
                }

                return result;
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("MQTT 5.0 连接超时");
                connectFuture.cancel(true);
                isConnected.set(false);
                connectionResult.set(false);
                PublishResult<ConnectionStateEvent> publishResult = MqttEventPublisher.publishConnectionState(ConnectionStateEvent.of(clientId, ConnectionState.CONNECTION_TIMEOUT, networkType));
                if (publishResult.isFailed()) {
                    log.warn("连接超时事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                    handleConnectionStatePublishFailure(ConnectionState.CONNECTION_TIMEOUT, publishResult);
                }
                return false;
            }
        } catch (Exception e) {
            log.error("连接失败，错误原因：{}", e.getMessage());
            reconnecting.set(false);
            return false;
        } finally {
            // 确保在异常情况下也清除重连标志
            // 注意：如果连接成功，reconnecting 标志会在回调中清除
            // 这里作为兜底，确保标志被清除
            if (!isConnected.get()) {
                reconnecting.set(false);
            }
        }
    }

    /**
     * 断开MQTT连接
     * <p>
     * <strong>生命周期阶段：</strong> 清理阶段
     * <p>
     * <strong>功能说明：</strong>
     * 安全地断开与MQTT服务器的连接，清理相关资源，并更新连接状态。
     * 该方法会停止连接状态广播，发布断开连接事件，确保资源正确释放。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查当前连接状态</li>
     *   <li>调用MQTT客户端断开连接</li>
     *   <li>更新内部连接状态标志</li>
     *   <li>停止连接状态广播</li>
     *   <li>发布断开连接事件</li>
     *   <li>记录断开连接日志</li>
     * </ol>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>只有在已连接状态下才会执行断开操作</li>
     *   <li>会停止连接状态广播线程</li>
     *   <li>异常情况下会记录错误日志但不抛出异常</li>
     *   <li>支持优雅断开，不会强制终止连接</li>
     * </ul>
     */
    public void disconnect() {
        if (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
            try {
                mqttClient.disconnect();
                isConnected.set(false);

                // 计算本次连接的有效时长
                long connectionStartTime = currentConnectionStartTime.get();
                if (connectionStartTime > 0) {
                    long connectionDuration = System.currentTimeMillis() - connectionStartTime;
                    log.info("本次连接有效时长: {} 毫秒 (约 {} 分钟)",
                            connectionDuration, connectionDuration / (1000 * 60));

                    // 重置连接开始时间，为下次连接做准备
                    currentConnectionStartTime.set(0);
                }

                PublishResult<Boolean> connectedResult = MqttEventPublisher.publishConnected(false);
                if (connectedResult.isFailed()) {
                    log.warn("连接状态广播失败: {}, 原因: {}", clientId, connectedResult.getFailureReason());
                }

                PublishResult<ConnectionStateEvent> publishResult = MqttEventPublisher.publishConnectionState(ConnectionStateEvent.of(clientId, ConnectionState.DISCONNECTED, networkType));
                if (publishResult.isFailed()) {
                    log.warn("连接断开事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                    handleConnectionStatePublishFailure(ConnectionState.DISCONNECTED, publishResult);
                }
                isBroadcast.set(false);
                log.info("MQTT连接已断开，客户端ID: {}", clientId);
            } catch (Exception e) {
                log.error("断开连接失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 处理连接状态变化事件
     * <p>
     * <strong>生命周期阶段：</strong> 运行维护阶段
     * <p>
     * <strong>功能说明：</strong>
     * 处理MQTT连接状态变化事件，只处理必要的技术异常（ABNORMAL_DISCONNECT、SESSION_EXPIRED），
     * 其他业务相关的事件由调用者通过事件订阅自行处理。确保技术异常得到及时处理，避免连接不稳定。
     * <p>
     * <strong>处理策略：</strong>
     * <ul>
     *   <li><strong>技术异常直接处理</strong>：ABNORMAL_DISCONNECT、SESSION_EXPIRED（立即重连）</li>
     *   <li><strong>业务状态事件忽略</strong>：CONNECTED、CONNECTION_FAILED等（由调用者处理）</li>
     *   <li><strong>职责分离</strong>：组件只处理技术问题，业务逻辑由调用者决定</li>
     * </ul>
     * <p>
     * <strong>设计优势：</strong>
     * <ul>
     *   <li><strong>技术保障优先</strong>：影响连接稳定性的异常立即处理</li>
     *   <li><strong>业务逻辑分离</strong>：业务相关事件完全由调用者处理</li>
     *   <li><strong>性能优化</strong>：减少不必要的事件处理逻辑</li>
     *   <li><strong>职责清晰</strong>：组件专注技术，调用者专注业务</li>
     * </ul>
     * <p>
     * <strong>事件循环防护：</strong>
     * <ul>
     *   <li><strong>来源过滤</strong>：只处理外部来源的事件，不处理自己发布的事件</li>
     *   <li><strong>循环检测</strong>：避免事件在组件内部形成循环</li>
     *   <li><strong>重连状态防护</strong>：重连过程中只处理CONNECTED状态，避免重复重连</li>
     * </ul>
     * <p>
     * <strong>重连状态防护机制：</strong>
     * <ul>
     *   <li><strong>重连中屏蔽</strong>：当reconnecting=true时，只处理CONNECTED状态事件</li>
     *   <li><strong>避免重复重连</strong>：防止在网络恢复前创建多个客户端实例</li>
     *   <li><strong>客户端踢下线防护</strong>：确保同一时间只有一个活跃的客户端实例</li>
     * </ul>
     *
     * @param event 连接状态变化事件
     */
    public void handleConnectionStateEvent(ConnectionStateEvent event) {

        // 重连状态防护：当重连正在进行时，只处理CONNECTED状态事件
        if (reconnecting.get() && event.getState() != ConnectionState.CONNECTED) {
            log.debug("重连正在进行中，跳过非CONNECTED状态事件: {}，客户端ID: {}", event.getState(), event.getClientId());
            return;
        }

        switch (event.getState()) {
            case ABNORMAL_DISCONNECT:
                log.warn("检测到异常断开，准备重连，线程ID：{}, 客户端ID: {}", Thread.currentThread().threadId(), event.getClientId());
                // 技术异常：立即重连，确保连接稳定性
                safeReconnect();
                log.info("异常断开已处理，重连已触发，客户端ID: {}", event.getClientId());
                break;

            case SESSION_EXPIRED:
                log.warn("检测到会话过期，准备重新建立连接，客户端ID: {}", event.getClientId());
                // 技术异常：立即重连，确保连接稳定性
                safeReconnect();
                log.info("会话过期已处理，重连已触发，客户端ID: {}", event.getClientId());
                break;

            case CONNECTED:
                log.debug("收到连接成功事件，客户端ID: {}", event.getClientId());
                // 连接成功事件，可以处理重连完成后的状态更新
                break;

            default:
                log.debug("收到业务相关事件，由调用者处理: {}，客户端ID: {}", event.getState(), event.getClientId());
                break;
        }
    }

    /**
     * 安全的重连方法，避免重复调用
     * <p>
     * <strong>生命周期阶段：</strong> 运行维护阶段
     * <p>
     * <strong>功能说明：</strong>
     * 提供安全的重连入口，防止重复重连请求，确保重连过程的唯一性。
     * 该方法会检查快速恢复配置和当前重连状态，只有在合适的情况下才启动重连流程。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查快速恢复功能是否启用</li>
     *   <li>验证当前连接状态，避免已连接时重连</li>
     *   <li>验证当前是否有重连正在进行</li>
     *   <li>调用内部重连重试机制</li>
     *   <li>确保重连过程的原子性</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络异常后的自动重连</li>
     *   <li>服务器故障恢复后的重连</li>
     *   <li>网络切换后的重连</li>
     *   <li>手动触发的重连请求</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>只有在快速恢复功能启用时才会执行</li>
     *   <li>防止已连接状态下的无效重连</li>
     *   <li>防止并发重连，确保重连过程的唯一性</li>
     *   <li>是外部调用的主要重连接口</li>
     *   <li>内部调用fastReconnectWithRetry()方法</li>
     * </ul>
     */
    public void safeReconnect() {
        // 获取快速恢复配置
        var fastRecoveryConfig = properties.getFastRecovery();

        // 检查是否启用快速恢复
        if (!fastRecoveryConfig.isEnabled()) {
            log.debug("快速恢复已禁用，跳过重连请求");
            return;
        }

        // 检查当前连接状态，避免已连接时重连
        if (mqttClient.getState() == MqttClientState.CONNECTED) {
            log.debug("当前已连接，跳过重连请求，客户端ID: {}", clientId);
            return;
        }

        if (reconnecting.get()) {
            log.debug("重连已在进行中，跳过当前重连请求");
            return;
        }

        log.info("开始执行重连流程，客户端ID: {}", clientId);
        fastReconnectWithRetry();
    }

    /**
     * 网络切换连接方法
     * <p>
     * <strong>生命周期阶段：</strong> 运行维护阶段
     * <p>
     * <strong>功能说明：</strong>
     * 在网络环境发生变化时，重新建立连接以适配新的网络环境。
     * 该方法会先断开当前连接，然后重新建立连接，确保网络切换的平滑性。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>断开当前MQTT连接</li>
     *   <li>清理连接相关资源</li>
     *   <li>使用新的网络配置重新连接</li>
     *   <li>更新连接状态和事件</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>WiFi与移动网络切换</li>
     *   <li>公网与VPC网络切换</li>
     *   <li>网络质量变化时的主动切换</li>
     *   <li>负载均衡场景下的服务器切换</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>会中断当前连接，可能影响正在进行的消息传输</li>
     *   <li>异常情况下会记录错误日志但不抛出异常</li>
     *   <li>建议在网络切换前确保重要消息已发送完成</li>
     *   <li>支持自动重连和故障恢复</li>
     * </ul>
     */
    public void switchNetworkConnection() {
        try {
            // 断开当前连接
            disconnect();

            // 重新建立连接
            connect();
        } catch (Exception e) {
            log.error("网络切换失败: {}", e.getMessage());
        }
    }

    // ==================== 公共方法 - 网络配置 ====================

    /**
     * 设置公网服务器
     * <p>
     * <strong>生命周期阶段：</strong> 运行维护阶段
     * <p>
     * <strong>功能说明：</strong>
     * 动态配置公网环境的MQTT服务器地址和端口。
     * 该方法允许在运行时修改服务器配置，支持动态配置调整。
     * <p>
     * <strong>配置内容：</strong>
     * <ul>
     *   <li>公网服务器主机地址</li>
     *   <li>公网服务器端口号</li>
     * </ul>
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
     *   <li>配置变更不会立即生效，需要重新连接</li>
     *   <li>建议在配置变更后调用changeNetworkType()方法</li>
     *   <li>会记录DEBUG级别的配置变更日志</li>
     *   <li>支持运行时动态配置</li>
     * </ul>
     *
     * @param host 公网服务器主机地址
     * @param port 公网服务器端口号
     */
    public void setPublicServer(String host, int port) {
        properties.getServer().setHost(host);
        properties.getServer().setPort(port);
        log.debug("设置公网服务器: {}:{}", host, port);
    }

    /**
     * 设置VPC服务器
     *
     * @param host VPC服务器主机地址
     * @param port VPC服务器端口号
     */
    public void setVpcServer(String host, int port) {
        properties.getServer().setVpcHost(host);
        properties.getServer().setVpcPort(port);
        log.debug("设置VPC服务器: {}:{}", host, port);
    }

    /**
     * 设置认证信息
     *
     * @param username MQTT用户名
     * @param password MQTT密码
     */
    public void setAuthorization(String username, String password) {
        properties.getServer().setUsername(username);
        properties.getServer().setPassword(password);
        log.debug("设置认证信息: username={}", username);
    }

    /**
     * 切换网络类型并重新连接
     * <p>
     * <strong>生命周期阶段：</strong> 运行维护阶段
     * <p>
     * <strong>功能说明：</strong>
     * 动态切换网络类型（公网/VPC），并自动重新建立连接。
     * 该方法会检查网络类型是否发生变化，只有在需要切换时才执行切换操作。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查网络类型是否发生变化</li>
     *   <li>更新内部网络类型标识</li>
     *   <li>记录网络类型切换日志</li>
     *   <li>调用网络切换连接方法</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>从公网环境切换到VPC环境</li>
     *   <li>从VPC环境切换到公网环境</li>
     *   <li>网络策略调整时的主动切换</li>
     *   <li>故障转移时的网络切换</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>只有在网络类型真正发生变化时才会执行切换</li>
     *   <li>切换过程会中断当前连接</li>
     *   <li>支持自动重连和故障恢复</li>
     *   <li>建议在业务低峰期执行网络切换</li>
     * </ul>
     *
     * @param networkType 目标网络类型
     */
    public void changeNetworkType(NetworkTypeEnum networkType) {
        if (this.networkType == networkType) {
            log.debug("网络类型未发生变化，跳过切换");
            return;
        }

        log.info("切换网络类型: {} -> {}", this.networkType, networkType);
        this.networkType = networkType;
        switchNetworkConnection();
    }

    /**
     * 切换网络类型并指定服务器地址
     *
     * @param networkType 目标网络类型
     * @param host        服务器地址
     * @param port        服务器端口
     */
    public void changeServer(NetworkTypeEnum networkType, String host, int port) {
        if (networkType == NetworkTypeEnum.PUBLIC) {
            setPublicServer(host, port);
        } else {
            setVpcServer(host, port);
        }
        changeNetworkType(networkType);
    }

    /**
     * 切换网络类型（使用配置中的服务器地址）
     *
     * @param networkType 目标网络类型
     */
    public void changeServer(NetworkTypeEnum networkType) {
        changeNetworkType(networkType);
    }

    // ==================== 公共方法 - 状态查询 ====================

    /**
     * 等待连接完成
     * <p>
     * <strong>生命周期阶段：</strong> 连接建立阶段
     * <p>
     * <strong>功能说明：</strong>
     * 阻塞等待MQTT连接建立完成，支持超时机制，避免无限等待。
     * 该方法通常用于同步场景，确保在连接建立后再进行后续操作。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查当前连接状态</li>
     *   <li>循环等待连接建立</li>
     *   <li>检查超时条件</li>
     *   <li>返回连接结果或抛出异常</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>应用启动时等待连接建立</li>
     *   <li>网络切换后等待重连完成</li>
     *   <li>测试场景下的连接验证</li>
     *   <li>同步操作前的连接状态确认</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>会阻塞当前线程直到连接建立或超时</li>
     *   <li>超时时间可通过WAIT_CONNECTED_TIMEOUT常量配置</li>
     *   <li>超时后会抛出IllegalStateException异常</li>
     *   <li>支持线程中断，会抛出InterruptedException</li>
     * </ul>
     *
     * @throws InterruptedException  等待被中断
     * @throws IllegalStateException 等待超时后连接仍未建立
     */
    public void waitForConnected() throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (!isConnected.get()) {
            if (System.currentTimeMillis() - startTime > WAIT_CONNECTED_TIMEOUT) {
                throw new IllegalStateException("MQTT client is not connected after waiting " + WAIT_CONNECTED_TIMEOUT + "ms");
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    /**
     * 检查是否已连接
     *
     * @return 是否已连接
     */
    boolean isConnected() {
        return mqttClient.getState() == MqttClientState.CONNECTED;
    }

    /**
     * 获取当前连接的有效时长
     * <p>
     * <strong>功能说明：</strong>
     * 获取当前连接的有效时长，从连接建立开始计算。
     * 如果当前未连接，返回0。
     * <p>
     * <strong>返回值：</strong>
     * <ul>
     *   <li>已连接：返回从连接建立到现在的毫秒数</li>
     *   <li>未连接：返回0</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>连接状态监控</li>
     *   <li>性能统计</li>
     *   <li>连接质量评估</li>
     *   <li>运维监控</li>
     * </ul>
     *
     * @return 当前连接的有效时长（毫秒），未连接时返回0
     */
    public long getCurrentConnectionDuration() {
        if (!isConnected.get()) {
            return 0;
        }

        long connectionStartTime = currentConnectionStartTime.get();
        if (connectionStartTime == 0) {
            return 0;
        }

        return System.currentTimeMillis() - connectionStartTime;
    }

    /**
     * 设置广播标志
     *
     * @param broadcast 是否开启广播
     */
    public void setBroadcast(boolean broadcast) {
        this.isBroadcast.set(broadcast);
    }

    /**
     * 检查是否开启广播
     *
     * @return 如果开启广播返回true，否则返回false
     */
    public boolean isBroadcast() {
        return isBroadcast.get();
    }

    /**
     * 获取MQTT QoS级别
     *
     * @return MQTT QoS级别
     */
    public MqttQos getMqttQos() {
        var qos = MqttQos.fromCode(properties.getServer().getQos().getValue());
        if (qos == null) {
            qos = MqttQos.AT_LEAST_ONCE;
        }
        return qos;
    }

    // ==================== 私有方法 - 重连逻辑 ====================

    /**
     * 快速重连
     * <p>
     * <strong>生命周期阶段：</strong> 运行维护阶段
     * <p>
     * <strong>功能说明：</strong>
     * 执行单次快速重连操作，包括客户端ID验证、网络连通性检查、连接重建等。
     * 该方法是重连机制的核心实现，被fastReconnectWithRetry()方法调用。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>验证客户端ID唯一性</li>
     *   <li>检查网络连通性</li>
     *   <li>断开现有连接（如果存在）</li>
     *   <li>调用connect()方法重新连接</li>
     *   <li>返回重连结果</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络异常后的快速恢复</li>
     *   <li>服务器故障后的重连</li>
     *   <li>网络切换后的连接重建</li>
     *   <li>重连重试机制中的单次重连</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>会验证客户端ID的唯一性</li>
     *   <li>会检查网络连通性，避免无效重连</li>
     *   <li>异常情况下会记录错误日志并返回false</li>
     *   <li>是私有方法，仅供内部使用</li>
     * </ul>
     *
     * @return 重连是否成功
     */
    private boolean fastReconnect() {
        if (!isClientIdUnique()) {
            log.error("客户端ID冲突检测: {}", clientId);
            return false;
        }

        try {
            // 检查网络连通性
            if (!checkNetworkConnectivity()) {
                log.warn("网络不可达，跳过重连");
                return false;
            }

            // 断开现有连接
            if (mqttClient != null && mqttClient.getState() == MqttClientState.CONNECTED) {
                mqttClient.disconnect();
            }

            // 重新连接
            return connect();
        } catch (Exception e) {
            log.error("快速重连失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 快速重连重试机制
     * <p>
     * <strong>生命周期阶段：</strong> 运行维护阶段
     * <p>
     * <strong>功能说明：</strong>
     * 提供智能重连重试机制，支持配置化的重试策略，包括最大重试次数、重试间隔等。
     * 该方法使用原子操作确保重连过程的唯一性，防止并发重连。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>使用CAS操作设置重连标志</li>
     *   <li>检查快速重连配置是否启用</li>
     *   <li>循环执行重连操作直到成功或达到最大重试次数</li>
     *   <li>在重连间隔中休眠</li>
     *   <li>记录重连结果和耗时</li>
     *   <li>清理重连标志</li>
     * </ol>
     * <p>
     * <strong>配置参数：</strong>
     * <ul>
     *   <li>最大重连尝试次数</li>
     *   <li>重连间隔时间</li>
     *   <li>快速重连功能开关</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>使用原子操作防止并发重连</li>
     *   <li>支持线程中断处理</li>
     *   <li>会记录每次重连的尝试次数和结果</li>
     *   <li>是私有方法，仅供内部使用</li>
     *   <li>内部调用fastReconnect()方法</li>
     *   <li>重连过程中会持续检查连接状态，连接成功后立即停止重连</li>
     * </ul>
     */
    private void fastReconnectWithRetry() {
        if (reconnecting.get()) {
            log.debug("已有线程正在重连，当前线程跳过。");
            return;
        }

        try {
            // 获取快速恢复配置
            var fastRecoveryConfig = properties.getFastRecovery();

            // 检查是否启用快速重连
            if (!fastRecoveryConfig.isEnableFastReconnect()) {
                log.info("快速重连已禁用，跳过重连流程");
                return;
            }

            int attempts = 0;
            while (attempts++ < fastRecoveryConfig.getMaxFastReconnectAttempts() && !isConnected()) {
                log.info("快速重连尝试第 {} 次", attempts);

                if (fastReconnect()) {
                    log.info("快速重连成功，耗时: {}ms", System.currentTimeMillis() - lastConnectedTime.get());
                    return;
                }

                // 重连后立即检查连接状态，避免不必要的等待
                if (isConnected()) {
                    isConnected.set(true);
                    log.info("重连过程中检测到连接已建立，停止重连流程");
                    return;
                }

                try {
                    // 使用配置的快速重连间隔
                    TimeUnit.MILLISECONDS.sleep(fastRecoveryConfig.getFastReconnectInterval());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!isConnected()) {
                log.error("快速重连失败，已达到最大重试次数: {}", fastRecoveryConfig.getMaxFastReconnectAttempts());
            }
        } finally {
            reconnecting.set(false);
        }
    }

    // ==================== 私有方法 - 初始化 ====================

    /**
     * 初始化连接管理器
     * <p>
     * <strong>生命周期阶段：</strong> 初始化阶段
     * <p>
     * <strong>功能说明：</strong>
     * 初始化连接管理器的核心组件，包括网络类型订阅、配置构建器创建、连接状态广播等。
     * 该方法在构造函数中被调用，负责设置初始状态和启动必要的后台服务。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>订阅网络类型变化事件</li>
     *   <li>订阅技术异常事件（ABNORMAL_DISCONNECT、SESSION_EXPIRED）</li>
     *   <li>创建连接配置构建器</li>
     *   <li>启动虚拟线程进行连接状态广播</li>
     *   <li>设置广播循环和异常处理</li>
     * </ol>
     * <p>
     * <strong>初始化内容：</strong>
     * <ul>
     *   <li>网络类型事件订阅</li>
     *   <li>技术异常事件订阅（只处理必要的技术异常）</li>
     *   <li>连接配置构建器</li>
     *   <li>连接状态广播线程</li>
     *   <li>异常处理和资源清理</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>在构造函数中自动调用</li>
     *   <li>使用虚拟线程提高性能</li>
     *   <li>支持优雅关闭和资源清理</li>
     *   <li>异常情况下会记录日志但不会中断初始化</li>
     *   <li>连接管理器会自动处理技术异常，确保连接稳定性</li>
     * </ul>
     */
    private void initial() {
        // 订阅网络类型变化事件
        MqttEventBus.networkTypeFlow.subscribe(networkType -> {
            this.networkType = networkType;
            this.configBuilder = new ConnectionConfigBuilder(properties, clientId, networkType);

            // 设置连接开始时间为当前时间
            this.configBuilder.setConnectionStartTime(System.currentTimeMillis());
        });

        // 订阅连接状态变化事件，只处理必要的技术异常
        MqttEventBus.connectionStateFlow
                .filter(event -> clientId.equals(event.getClientId())) // 只处理当前客户端的事件
                .filter(event -> event.getState().in(ABNORMAL_DISCONNECT, SESSION_EXPIRED)) // 只订阅技术异常事件
                .subscribe(
                        this::handleConnectionStateEvent,
                        error -> log.error("技术异常事件订阅错误: {}", error.getMessage()),
                        () -> log.debug("技术异常事件订阅完成")
                );

        // 使用虚拟线程启动广播
        Thread.startVirtualThread(() -> {
            try {
                while (isBroadcast.get()) {
                    PublishResult<Boolean> publishResult = MqttEventPublisher.publishConnected(isConnected.get());
                    if (publishResult.isFailed()) {
                        log.debug("连接状态广播失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                    }
                    TimeUnit.SECONDS.sleep(1L); // 每秒广播一次
                }
            } catch (InterruptedException e) {
                log.debug("连接状态广播线程被中断");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("连接状态广播异常: {}", e.getMessage(), e);
            } finally {
                log.debug("连接状态广播线程结束");
            }
        });
    }

    // ==================== 私有方法 - 工具方法 ====================

    /**
     * 获取连接超时时间
     *
     * @return 连接超时时间（秒）
     */
    private long getConnectionTimeout() {
        return properties.getServer().getConnectionTimeout();
    }

    /**
     * 验证服务器配置
     * <p>
     * <strong>生命周期阶段：</strong> 连接建立阶段
     * <p>
     * <strong>功能说明：</strong>
     * 在建立连接前验证MQTT服务器配置的完整性和有效性。
     * 该方法会检查必要的配置项是否存在，确保连接参数的正确性。
     * <p>
     * <strong>验证内容：</strong>
     * <ul>
     *   <li>服务器配置对象是否存在</li>
     *   <li>密码是否为空</li>
     *   <li>其他必要的连接参数</li>
     * </ul>
     * <p>
     * <strong>异常处理：</strong>
     * <ul>
     *   <li>配置缺失时抛出RydeenMqttClientException</li>
     *   <li>密码为空时抛出IllegalArgumentException</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>在connect()方法开始时调用</li>
     *   <li>验证失败会阻止连接建立</li>
     *   <li>是连接建立的前置条件</li>
     * </ul>
     */
    private void validateServerConfig() {
        if (properties.getServer() == null) {
            throw new RydeenMqttClientException("MQTT 客户端初始化异常，缺少必要的配置信息。");
        }

        if (StringUtils.isBlank(properties.getServer().getPassword())) {
            throw new IllegalArgumentException("MQTT 密码不能为空");
        }
    }

    /**
     * 获取服务器地址
     *
     * @return 服务器地址
     */
    private String getServerHost() {
        return networkType == NetworkTypeEnum.PUBLIC ?
                properties.getServer().getHost() :
                properties.getServer().getVpcHost();
    }

    /**
     * 获取服务器端口
     *
     * @return 服务器端口
     */
    private int getServerPort() {
        return networkType == NetworkTypeEnum.PUBLIC ?
                properties.getServer().getPort() :
                properties.getServer().getVpcPort();
    }

    /**
     * 检查网络连通性
     * <p>
     * <strong>生命周期阶段：</strong> 运行维护阶段
     * <p>
     * <strong>功能说明：</strong>
     * 通过Socket连接测试检查目标MQTT服务器的网络连通性。
     * 该方法在重连前调用，避免在网络不可达的情况下进行无效的重连尝试。
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>获取目标服务器地址和端口</li>
     *   <li>创建Socket连接进行连通性测试</li>
     *   <li>使用配置的超时时间</li>
     *   <li>返回连通性测试结果</li>
     * </ol>
     * <p>
     * <strong>配置参数：</strong>
     * <ul>
     *   <li>网络连通性检测超时时间</li>
     *   <li>目标服务器地址和端口</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>使用try-with-resources确保Socket资源释放</li>
     *   <li>异常情况下返回false，不会抛出异常</li>
     *   <li>在重连前调用，提高重连效率</li>
     *   <li>支持配置化的超时时间</li>
     * </ul>
     *
     * @return 网络是否可达
     */
    private boolean checkNetworkConnectivity() {
        try {
            String host = getServerHost();
            int port = getServerPort();

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port),
                        properties.getFastRecovery().getNetworkConnectTimeout());
                return true;
            }
        } catch (Exception e) {
            log.debug("网络连通性检测失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查客户端ID唯一性
     *
     * @return 客户端ID是否唯一
     */
    private boolean isClientIdUnique() {
        if (StringUtils.isBlank(clientId)) {
            log.error("客户端ID为空");
            return false;
        }
        return true;
    }

    /**
     * 连接前诊断
     * <p>
     * <strong>生命周期阶段：</strong> 连接建立阶段
     * <p>
     * <strong>功能说明：</strong>
     * 在建立MQTT连接前输出详细的诊断信息，帮助开发者了解连接配置和网络环境。
     * 该方法会记录客户端配置、服务器信息、网络类型等关键信息。
     * <p>
     * <strong>诊断内容：</strong>
     * <ul>
     *   <li>客户端ID和网络类型</li>
     *   <li>服务器地址和端口</li>
     *   <li>用户名和认证信息</li>
     *   <li>心跳间隔和会话过期时间</li>
     *   <li>其他连接相关配置</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>连接建立前的配置验证</li>
     *   <li>问题排查和调试</li>
     *   <li>连接参数确认</li>
     *   <li>网络环境诊断</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>在connect()方法中调用</li>
     *   <li>输出INFO级别的日志信息</li>
     *   <li>有助于问题排查和配置验证</li>
     *   <li>不会影响连接建立的性能</li>
     * </ul>
     */
    private void diagnoseBeforeConnection(String host, int port) {
        log.info("==== MQTT连接诊断信息 ====");
        log.info("客户端ID: {}", clientId);
        log.info("服务器地址: {}:{}", host, port);
        log.info("网络类型: {}", networkType.name());
        log.info("用户名: {}", properties.getServer().getUsername());
        log.info("心跳间隔: {}秒", properties.getFastRecovery().getKeepAliveInterval());
        log.info("会话过期时间: {}秒", properties.getMqtt5().getSessionExpiryInterval());
        log.info("========================");
    }

    /**
     * 分析连接失败原因
     * <p>
     * <strong>生命周期阶段：</strong> 连接建立阶段
     * <p>
     * <strong>功能说明：</strong>
     * 分析MQTT连接失败的具体原因，提供详细的错误分类和解决建议。
     * 该方法会解析异常信息，识别常见的连接失败原因，帮助开发者快速定位问题。
     * <p>
     * <strong>错误分类：</strong>
     * <ul>
     *   <li><strong>认证失败</strong>：用户名密码错误、权限不足</li>
     *   <li><strong>客户端ID冲突</strong>：ID重复、格式不正确</li>
     *   <li><strong>网络问题</strong>：连接被拒绝、超时、网络不可达</li>
     *   <li><strong>协议错误</strong>：参数格式错误、版本不兼容</li>
     *   <li><strong>其他错误</strong>：未知错误类型</li>
     * </ul>
     * <p>
     * <strong>分析流程：</strong>
     * <ol>
     *   <li>提取异常的错误信息</li>
     *   <li>根据关键词进行错误分类</li>
     *   <li>输出具体的错误原因</li>
     *   <li>记录异常类型和详细信息</li>
     * </ol>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>连接失败后的错误分析</li>
     *   <li>问题排查和调试</li>
     *   <li>错误日志记录</li>
     *   <li>用户友好的错误提示</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>在连接失败回调中调用</li>
     *   <li>输出ERROR级别的日志信息</li>
     *   <li>不会抛出异常，只记录分析结果</li>
     *   <li>有助于快速定位连接问题</li>
     * </ul>
     */
    private void analyzeConnectionFailure(Throwable ex) {
        String errorMessage = ex.getMessage();
        String exceptionClassName = ex.getClass().getSimpleName();
        log.error("=== 连接失败原因分析 ===");
        log.error("异常类型: {}", exceptionClassName);

        if (errorMessage != null) {
            // ==================== 服务器主动关闭连接（无 DISCONNECT）====================
            // 这是最常见的错误，通常表示服务器端拒绝了连接请求
            if (errorMessage.contains("Server closed connection without DISCONNECT") ||
                    errorMessage.contains("Server closed connection")) {
                log.error("【服务器主动关闭连接】服务器在未发送 DISCONNECT 消息的情况下关闭了连接");
                log.error("可能原因及排查建议：");
                log.error("1. 【认证失败】用户名或密码错误");
                log.error("   - 检查配置的用户名: {}", properties.getServer().getUsername());
                log.error("   - 检查密码是否正确（密码已隐藏，请手动验证）");
                log.error("   - 确认腾讯云 MQTT 实例的访问凭证是否正确");
                log.error("2. 【客户端ID冲突】同一客户端ID被其他实例使用");
                log.error("   - 当前客户端ID: {}", clientId);
                log.error("   - 检查是否有其他实例使用相同的客户端ID");
                log.error("   - 建议：使用唯一标识符（如UUID）作为客户端ID后缀");
                log.error("3. 【KeepAlive 配置问题】心跳间隔设置不当");
                log.error("   - 当前心跳间隔: {}秒", properties.getFastRecovery().getKeepAliveInterval());
                log.error("   - 腾讯云建议：KeepAlive 应在 30-300 秒之间");
                log.error("   - 如果设置过短，服务器可能认为客户端异常而关闭连接");
                log.error("4. 【会话过期时间配置问题】会话过期时间设置不当");
                log.error("   - 当前会话过期时间: {}秒", properties.getMqtt5().getSessionExpiryInterval());
                log.error("   - 腾讯云限制：会话过期时间不能超过 7 天（604800秒）");
                log.error("   - 如果设置为 0，表示会话立即过期（Clean Start）");
                log.error("5. 【协议版本不匹配】MQTT 协议版本问题");
                log.error("   - 当前使用: MQTT 5.0");
                log.error("   - 确认腾讯云 MQTT 实例支持 MQTT 5.0 协议");
                log.error("6. 【服务器端限制】连接数、QoS 等限制");
                log.error("   - 检查腾讯云 MQTT 实例的连接数限制");
                log.error("   - 检查 QoS 级别是否符合服务器要求");
                log.error("7. 【网络问题】防火墙、超时等");
                log.error("   - 服务器地址: {}:{}", getServerHost(), getServerPort());
                log.error("   - 检查网络连通性：telnet {} {}", getServerHost(), getServerPort());
                log.error("   - 检查防火墙是否允许 MQTT 连接（端口 1883/8883）");
                log.error("8. 【SSL/TLS 配置问题】如果使用加密连接");
                log.error("   - 检查 SSL/TLS 证书配置是否正确");
                log.error("   - 确认端口是否正确（8883 为加密端口，1883 为非加密端口）");
            }
            // ==================== 认证失败 ====================
            else if (errorMessage.contains("Not authorized") || errorMessage.contains("Authentication failed") ||
                    errorMessage.contains("Bad user name or password")) {
                log.error("【认证失败】用户名或密码错误");
                log.error("   - 检查配置的用户名: {}", properties.getServer().getUsername());
                log.error("   - 检查密码是否正确");
                log.error("   - 确认腾讯云 MQTT 实例的访问凭证是否正确");
            }
            // ==================== 客户端ID问题 ====================
            else if (errorMessage.contains("Identifier rejected") || errorMessage.contains("Client ID") ||
                    errorMessage.contains("Client identifier")) {
                log.error("【客户端ID被拒绝】可能存在ID冲突或格式错误");
                log.error("   - 当前客户端ID: {}", clientId);
                log.error("   - 检查是否有其他实例使用相同的客户端ID");
                log.error("   - 检查客户端ID格式是否符合腾讯云要求（长度、字符集等）");
            }
            // ==================== 网络问题 ====================
            else if (errorMessage.contains("Connection refused") || errorMessage.contains("Connect timeout") ||
                    errorMessage.contains("Connection timed out")) {
                log.error("【网络问题】连接被拒绝或超时");
                log.error("   - 服务器地址: {}:{}", getServerHost(), getServerPort());
                log.error("   - 检查网络连通性");
                log.error("   - 检查防火墙设置");
                log.error("   - 检查连接超时配置: {}秒", getConnectionTimeout());
            }
            // ==================== 协议错误 ====================
            else if (errorMessage.contains("Malformed") || errorMessage.contains("Protocol") ||
                    errorMessage.contains("Invalid")) {
                log.error("【协议错误】连接参数格式问题");
                log.error("   - 检查 MQTT 协议版本配置");
                log.error("   - 检查连接参数格式是否正确");
            }
            // ==================== 其他错误 ====================
            else {
                log.error("【未知错误】连接失败原因: {}", errorMessage);
                log.error("   - 建议查看腾讯云 MQTT 控制台的连接日志");
                log.error("   - 建议联系腾讯云技术支持获取详细错误信息");
            }
        } else {
            log.error("【无错误信息】连接失败但无具体错误信息");
            log.error("   - 异常类型: {}", exceptionClassName);
            log.error("   - 建议查看完整异常堆栈信息");
            log.error("   - 建议检查网络连接和服务器状态");
        }

        // 输出当前连接配置信息，便于排查
        log.error("--- 当前连接配置信息 ---");
        log.error("客户端ID: {}", clientId);
        log.error("服务器地址: {}:{}", getServerHost(), getServerPort());
        log.error("网络类型: {}", networkType.name());
        log.error("用户名: {}", properties.getServer().getUsername());
        log.error("心跳间隔: {}秒", properties.getFastRecovery().getKeepAliveInterval());
        log.error("会话过期时间: {}秒", properties.getMqtt5().getSessionExpiryInterval());
        log.error("连接超时: {}秒", getConnectionTimeout());
        log.error("========================");
    }

    /**
     * 处理连接状态事件发布失败
     *
     * @param state         发布失败的状态
     * @param publishResult 发布结果
     */
    private void handleConnectionStatePublishFailure(ConnectionState state, PublishResult<ConnectionStateEvent> publishResult) {
        switch (publishResult.getFailureReason()) {
            case BUFFER_OVERFLOW:
                log.warn("连接状态缓冲区溢出，状态事件被丢弃: {}", state);
                // 连接状态事件丢失通常不是严重问题，可以忽略
                break;

            case SINK_TERMINATED:
                log.error("事件总线已终止，无法发布连接状态事件: {}", state);
                // 需要重新初始化事件总线或重新订阅
                break;

            case EXCEPTION:
                log.error("发布连接状态事件时发生异常: {}", state, publishResult.getException());
                // 异常处理逻辑
                break;

            default:
                log.warn("连接状态事件发布失败: {}, 原因: {}", state, publishResult.getFailureReason());
                break;
        }
    }
}
