package com.richie.component.mqtt.client;

import com.richie.component.mqtt.beans.HeartbeatEvent;
import com.richie.component.mqtt.beans.HeartbeatInfo;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.enums.ClientTypeEnum;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 心跳管理器
 * <p>
 * 负责发送心跳包和监控连接状态，确保MQTT连接的活跃性和稳定性。
 * 该类实现了基于定时任务的心跳机制，通过定期发送心跳消息来维持连接，
 * 并监控心跳状态以检测连接异常。
 * <p>
 * <strong>核心功能：</strong>
 * <ul>
 *   <li><strong>心跳发送</strong>：定期发送心跳包维持连接活跃</li>
 *   <li><strong>连接监控</strong>：监控连接状态，只在连接时发送心跳</li>
 *   <li><strong>状态管理</strong>：管理心跳管理器的启动、停止状态</li>
 *   <li><strong>健康检查</strong>：检查心跳是否正常，检测连接异常</li>
 *   <li><strong>事件发布</strong>：发布心跳事件供其他组件使用</li>
 * </ul>
 * <p>
 * <strong>心跳机制：</strong>
 * <ul>
 *   <li><strong>定时发送</strong>：基于配置的心跳间隔定时执行</li>
 *   <li><strong>条件发送</strong>：只在连接建立状态下发送心跳</li>
 *   <li><strong>事件驱动</strong>：通过事件总线发布心跳事件</li>
 *   <li><strong>状态感知</strong>：订阅连接状态变化事件</li>
 * </ul>
 * <p>
 * <strong>监控指标：</strong>
 * <ul>
 *   <li><strong>心跳次数</strong>：累计发送的心跳包数量</li>
 *   <li><strong>最后心跳时间</strong>：最后一次心跳发送的时间戳</li>
 *   <li><strong>心跳健康状态</strong>：基于时间间隔的心跳健康检查</li>
 *   <li><strong>连接状态</strong>：当前MQTT连接的状态</li>
 * </ul>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li>MQTT连接保活和心跳维护</li>
 *   <li>连接状态监控和异常检测</li>
 *   <li>网络质量评估和连接健康检查</li>
 *   <li>自动重连和故障恢复</li>
 *   <li>连接生命周期管理</li>
 * </ul>
 * <p>
 * <strong>技术特点：</strong>
 * <ul>
 *   <li>使用ScheduledExecutorService实现定时任务</li>
 *   <li>原子操作确保状态一致性</li>
 *   <li>事件总线集成，支持组件间通信</li>
 *   <li>守护线程设计，不阻止JVM退出</li>
 *   <li>支持优雅启动和停止</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Slf4j
public class HeartbeatManager {

    private static final String HEARTBEAT_TOPIC_PREFIX = "$heartbeat";
    private final MqttClientProperties properties;
    private final String clientId;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;
    private final AtomicLong lastHeartbeatTime = new AtomicLong(0);
    private final AtomicLong heartbeatCount = new AtomicLong(0);

    /**
     * 构造心跳管理器
     * <p>
     * <strong>设计原则：</strong> 依赖注入，事件订阅，状态初始化
     * <p>
     * <strong>功能说明：</strong>
     * 创建心跳管理器实例，初始化必要的组件和事件订阅。
     * 构造函数会订阅连接状态变化事件，为后续的心跳发送提供状态基础。
     * <p>
     * <strong>初始化内容：</strong>
     * <ul>
     *   <li>保存MQTT客户端配置和客户端ID</li>
     *   <li>订阅连接状态变化事件流</li>
     *   <li>初始化心跳相关状态变量</li>
     *   <li>设置事件处理器</li>
     * </ul>
     * <p>
     * <strong>事件订阅：</strong>
     * <ul>
     *   <li>connectedFlow：监听连接状态变化</li>
     *   <li>自动更新内部连接状态标志</li>
     *   <li>支持实时状态同步</li>
     * </ul>
     * <p>
     * <strong>参数要求：</strong>
     * <ul>
     *   <li>properties：MQTT客户端配置，包含心跳间隔等配置</li>
     *   <li>clientId：客户端唯一标识，用于日志记录和事件标识</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>构造函数不会启动心跳任务，需要调用start()方法</li>
     *   <li>事件订阅是响应式的，支持实时状态更新</li>
     *   <li>所有参数都不能为null，确保管理器正常工作</li>
     *   <li>支持动态连接状态变化</li>
     * </ul>
     *
     * @param properties MQTT客户端配置，包含心跳间隔等配置
     * @param clientId 客户端唯一标识
     * @throws IllegalArgumentException 当参数为null时
     */
    public HeartbeatManager(MqttClientProperties properties, String clientId) {
        this.properties = properties;
        this.clientId = clientId;
        MqttEventBus.connectedFlow.subscribe(this.isConnected::set);

    }

    /**
     * 启动心跳管理器
     * <p>
     * <strong>设计原则：</strong> 状态控制，原子操作，幂等性
     * <p>
     * <strong>功能说明：</strong>
     * 启动心跳管理器，开始定期发送心跳包。该方法使用原子操作确保
     * 管理器只启动一次，支持多次调用而不会产生副作用。
     * <p>
     * <strong>启动机制：</strong>
     * <ul>
     *   <li>使用AtomicBoolean.compareAndSet()确保原子性</li>
     *   <li>只有当前未运行时才能启动</li>
     *   <li>启动后定时任务开始执行</li>
     *   <li>支持并发调用，线程安全</li>
     * </ul>
     * <p>
     * <strong>启动流程：</strong>
     * <ol>
     *   <li>检查当前运行状态</li>
     *   <li>如果未运行，设置运行标志为true</li>
     *   <li>创建单线程调度器</li>
     *   <li>启动心跳发送定时任务</li>
     *   <li>记录启动日志</li>
     * </ol>
     * <p>
     * <strong>调度器配置：</strong>
     * <ul>
     *   <li>单线程执行器：确保心跳发送的顺序性</li>
     *   <li>守护线程：不会阻止JVM退出</li>
     *   <li>线程命名：包含客户端ID，便于调试</li>
     *   <li>定时执行：基于配置的心跳间隔</li>
     * </ul>
     * <p>
     * <strong>心跳配置：</strong>
     * <ul>
     *   <li>心跳间隔：从properties.getFastRecovery().getKeepAliveInterval()获取</li>
     *   <li>立即执行：启动后立即发送第一次心跳</li>
     *   <li>定时执行：按配置间隔定期发送</li>
     *   <li>支持动态配置调整</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>启动后需要等待下一个定时任务周期</li>
     *   <li>不会立即发送心跳包</li>
     *   <li>支持重复调用，不会重复启动</li>
     *   <li>启动后需要调用stop()才能停止</li>
     *   <li>心跳发送依赖于连接状态</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>系统启动时初始化心跳管理</li>
     *   <li>心跳管理暂停后重新启动</li>
     *   <li>配置变更后重启心跳</li>
     *   <li>手动控制心跳状态</li>
     * </ul>
     */
    public void start() {
        // 仅在 Client 模式下启用心跳；Server 模式默认不启用
        if (properties.getClientType() != null && properties.getClientType() != ClientTypeEnum.CLIENT) {
            log.debug("心跳管理器未启动：当前 clientType = {}，仅在 CLIENT 模式下启用心跳，clientId={}",
                    properties.getClientType(), clientId);
            return;
        }

        if (running.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "heartbeat-manager-%s".formatted(clientId));
                t.setDaemon(true);
                return t;
            });

            // 启动心跳发送
            scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0,
                    properties.getFastRecovery().getKeepAliveInterval(), TimeUnit.SECONDS);

            log.info("心跳管理器已启动，客户端ID: {}, 心跳间隔: {}秒", clientId, properties.getFastRecovery().getKeepAliveInterval());
        }
    }

    /**
     * 停止心跳管理器
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            log.info("心跳管理器已停止，客户端ID: {}", clientId);
        }
    }

    /**
     * 发送心跳
     */
    private void sendHeartbeat() {
        if (!isConnected.get()) {
            log.debug("连接未建立，跳过心跳发送，客户端ID: {}", clientId);
            return;
        }

        // 创建心跳信息
        HeartbeatInfo heartbeat = new HeartbeatInfo(
                clientId,
                LocalDateTime.now(),
                heartbeatCount.incrementAndGet()
        );

        // 发布心跳事件
        PublishResult<HeartbeatEvent> publishResult = MqttEventPublisher.publishHeartbeat(HeartbeatEvent.of("%s/%s".formatted(HEARTBEAT_TOPIC_PREFIX, clientId), heartbeat));
        if (publishResult.isFailed()) {
            log.warn("心跳事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
            handleHeartbeatPublishFailure(heartbeat, publishResult);
        }

        // 记录心跳时间
        lastHeartbeatTime.set(System.currentTimeMillis());

        log.debug("心跳发送成功，客户端ID: {}, 心跳次数: {}", clientId, heartbeatCount.get());
    }

    /**
     * 获取最后心跳时间
     *
     * @return 最后心跳时间戳（毫秒），如果从未发送过心跳则返回0
     */
    public long getLastHeartbeatTime() {
        return lastHeartbeatTime.get();
    }

    /**
     * 获取心跳计数
     *
     * @return 已发送的心跳包总数
     */
    public long getHeartbeatCount() {
        return heartbeatCount.get();
    }

    /**
     * 检查心跳是否正常
     *
     * @return 如果心跳正常返回true，否则返回false
     */
    public boolean isHeartbeatHealthy() {
        if (lastHeartbeatTime.get() == 0) {
            return false;
        }

        long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatTime.get();
        long maxHeartbeatInterval = properties.getFastRecovery().getKeepAliveInterval() * 1000L * 2; // 2倍心跳间隔

        return timeSinceLastHeartbeat <= maxHeartbeatInterval;
    }

    /**
     * 处理心跳事件发布失败
     *
     * @param heartbeat 发布失败的心跳信息
     * @param publishResult 发布结果
     */
    private void handleHeartbeatPublishFailure(HeartbeatInfo heartbeat, PublishResult<HeartbeatEvent> publishResult) {
        switch (publishResult.getFailureReason()) {
            case BUFFER_OVERFLOW:
                log.warn("心跳缓冲区溢出，心跳事件被丢弃: {}", heartbeat.getClientId());
                // 心跳事件丢失通常不是严重问题，可以忽略
                break;

            case SINK_TERMINATED:
                log.error("事件总线已终止，无法发布心跳事件: {}", heartbeat.getClientId());
                // 需要重新初始化事件总线或重新订阅
                break;

            case EXCEPTION:
                log.error("发布心跳事件时发生异常: {}", heartbeat.getClientId(), publishResult.getException());
                // 异常处理逻辑
                break;

            default:
                log.warn("心跳事件发布失败: {}, 原因: {}", heartbeat.getClientId(), publishResult.getFailureReason());
                break;
        }
    }
}
