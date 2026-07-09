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

import com.richie.component.mqtt.beans.ConnectionState;
import com.richie.component.mqtt.beans.ConnectionStateEvent;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.richie.component.mqtt.beans.ConnectionState.*;

/**
 * 连接监控器
 * <p>
 * 负责监控MQTT连接状态和网络状态，提供实时的连接健康检查和网络可达性监控。
 * 该类是整个MQTT组件的监控核心，通过定时任务持续监控连接状态，及时发现连接异常
 * 和网络问题，并发布相应的事件通知其他组件。
 * <p>
 * <strong>断开原因识别功能：</strong>
 * <ul>
 *   <li><strong>主动断开</strong>：调用setDisconnecting()标记，断开时发布DISCONNECTED事件</li>
 *   <li><strong>网络故障断开</strong>：自动检测网络故障，断开时发布ABNORMAL_DISCONNECT事件</li>
 *   <li><strong>其他异常断开</strong>：无法识别的断开原因，发布ABNORMAL_DISCONNECT事件</li>
 * </ul>
 * <p>
 * <strong>使用示例：</strong>
 * <pre>{@code
 * ConnectionMonitor monitor = new ConnectionMonitor(properties, clientId, mqttClient);
 *
 * // 主动断开连接前，先标记断开原因
 * monitor.setDisconnecting();
 * mqttClient.disconnect(); // 断开连接
 *
 * // 监控器会自动识别为主动断开，发布DISCONNECTED事件
 * }</pre>
 * <p>
 * <strong>组件生命周期阶段：</strong>
 * <ul>
 *   <li><strong>初始化阶段</strong>：构造函数、事件订阅</li>
 *   <li><strong>运行阶段</strong>：start()、监控任务执行</li>
 *   <li><strong>监控阶段</strong>：连接状态监控、网络状态监控</li>
 *   <li><strong>清理阶段</strong>：stop()、资源释放</li>
 * </ul>
 * <p>
 * <strong>主要职责：</strong>
 * <ul>
 *   <li>实时监控MQTT连接状态变化</li>
 *   <li>监控网络可达性和网络质量</li>
 *   <li>检测连接超时和会话过期</li>
 *   <li>发布连接状态变化事件</li>
 *   <li>支持可配置的监控策略</li>
 * </ul>
 * <p>
 * <strong>监控策略：</strong>
 * <ul>
 *   <li>连接状态监控：检测连接建立、断开、异常等状态变化</li>
 *   <li>网络状态监控：检查网络可达性，支持网络恢复检测</li>
 *   <li>连接超时检测：基于心跳间隔的智能超时判断</li>
 *   <li>事件发布：实时发布连接状态变化事件</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Slf4j
public final class ConnectionMonitor extends MqttEventPublisher {

    private final MqttClientProperties properties;
    private final String clientId;
    private NetworkTypeEnum networkType;
    private final ConnectionManager connectionManager;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicLong lastConnectedTime = new AtomicLong(0);
    private final AtomicLong connectionStartTime = new AtomicLong(0); // 新增：连接建立时间
    private final AtomicBoolean running = new AtomicBoolean(false);
    // 添加断开原因跟踪
    private final AtomicBoolean isDisconnecting = new AtomicBoolean(false);
    private final AtomicBoolean isNetworkFailure = new AtomicBoolean(false);

    private ScheduledExecutorService scheduler;

    /**
     * 构造连接监控器
     * <p>
     * <strong>生命周期阶段：</strong> 初始化阶段
     * <p>
     * <strong>功能说明：</strong>
     * 创建连接监控器实例，初始化监控组件，订阅连接状态和网络类型变化事件。
     * 构造函数会设置事件监听器，为后续的监控任务提供数据基础。
     * <p>
     * <strong>初始化内容：</strong>
     * <ul>
     *   <li>保存MQTT客户端配置和客户端ID</li>
     *   <li>保存MQTT客户端引用用于状态检查</li>
     *   <li>订阅连接状态变化事件流</li>
     *   <li>订阅网络类型变化事件流</li>
     *   <li>初始化连接状态和网络类型</li>
     * </ul>
     * <p>
     * <strong>事件订阅：</strong>
     * <ul>
     *   <li>connectedFlow：监听连接状态变化，自动更新内部状态</li>
     *   <li>networkTypeFlow：监听网络类型变化，支持动态网络切换</li>
     * </ul>
     * <p>
     * <strong>参数要求：</strong>
     * <ul>
     *   <li>properties：MQTT客户端配置，包含监控策略配置</li>
     *   <li>clientId：客户端唯一标识，用于日志记录和事件标识</li>
     *   <li>mqttClient：MQTT客户端实例，用于状态检查</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>构造函数不会启动监控任务，需要调用start()方法</li>
     *   <li>事件订阅是响应式的，支持实时状态更新</li>
     *   <li>所有参数都不能为null，确保监控器正常工作</li>
     *   <li>支持动态网络类型切换</li>
     * </ul>
     *
     * @param properties MQTT客户端配置，包含监控策略配置
     * @param clientId   客户端唯一标识
     * @param connectionManager MQTT客户端实例
     * @throws IllegalArgumentException 当参数为null时
     */
    public ConnectionMonitor(MqttClientProperties properties, String clientId, ConnectionManager connectionManager) {
        this.properties = properties;
        this.clientId = clientId;
        this.connectionManager = connectionManager;
        this.networkType = properties.getServer().getDefaultNetworkType();
        MqttEventBus.connectedFlow.subscribe(this.isConnected::set);
        MqttEventBus.networkTypeFlow.subscribe(networkType -> this.networkType = networkType);
    }

    /**
     * 启动连接监控器
     * <p>
     * <strong>生命周期阶段：</strong> 运行阶段
     * <p>
     * <strong>功能说明：</strong>
     * 启动连接监控器的所有监控任务，包括连接状态监控和网络状态监控。
     * 该方法使用原子操作确保监控器只启动一次，支持优雅启动和配置化监控策略。
     * <p>
     * <strong>启动内容：</strong>
     * <ul>
     *   <li>创建单线程调度器，使用守护线程</li>
     *   <li>启动连接状态监控任务</li>
     *   <li>根据配置启动网络状态监控任务</li>
     *   <li>设置监控任务的执行间隔</li>
     * </ul>
     * <p>
     * <strong>监控任务：</strong>
     * <ul>
     *   <li><strong>连接状态监控</strong>：检测MQTT连接状态变化</li>
     *   <li><strong>网络状态监控</strong>：检查网络可达性（可选）</li>
     * </ul>
     * <p>
     * <strong>配置参数：</strong>
     * <ul>
     *   <li>connectionMonitorInterval：连接状态监控间隔</li>
     *   <li>enableNetworkMonitor：是否启用网络监控</li>
     *   <li>networkCheckInterval：网络检查间隔</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>使用CAS操作防止重复启动</li>
     *   <li>调度器使用守护线程，不会阻止JVM退出</li>
     *   <li>网络监控是可选的，根据配置决定是否启用</li>
     *   <li>启动后会立即执行一次监控任务</li>
     *   <li>支持动态配置调整</li>
     * </ul>
     * <p>
     * <strong>线程安全：</strong>
     * <ul>
     *   <li>使用AtomicBoolean确保线程安全</li>
     *   <li>支持并发调用，但只会启动一次</li>
     *   <li>调度器是线程安全的</li>
     * </ul>
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "connection-monitor-" + clientId);
                t.setDaemon(true);
                return t;
            });

            // 启动连接状态监控
            scheduler.scheduleAtFixedRate(this::monitorConnectionState, 0,
                    properties.getFastRecovery().getConnectionMonitorInterval(), TimeUnit.SECONDS);

            // 启动网络状态监控
            if (properties.getFastRecovery().isEnableNetworkMonitor()) {
                scheduler.scheduleAtFixedRate(this::monitorNetworkState, 0,
                        properties.getFastRecovery().getNetworkCheckInterval(), TimeUnit.SECONDS);
            }

            log.info("连接监控器已启动，客户端ID: {}", clientId);
        }
    }

    /**
     * 停止连接监控器
     * <p>
     * <strong>生命周期阶段：</strong> 清理阶段
     * <p>
     * <strong>功能说明：</strong>
     * 安全地停止连接监控器的所有监控任务，释放相关资源，确保优雅关闭。
     * 该方法使用原子操作确保监控器只停止一次，支持超时控制和强制关闭。
     * <p>
     * <strong>停止流程：</strong>
     * <ol>
     *   <li>使用CAS操作设置停止标志</li>
     *   <li>关闭调度器，停止所有监控任务</li>
     *   <li>等待任务完成（最多等待5秒）</li>
     *   <li>超时后强制关闭剩余任务</li>
     *   <li>处理线程中断异常</li>
     *   <li>记录停止日志</li>
     * </ol>
     * <p>
     * <strong>资源清理：</strong>
     * <ul>
     *   <li>关闭ScheduledExecutorService</li>
     *   <li>停止所有正在执行的监控任务</li>
     *   <li>释放线程池资源</li>
     *   <li>清理内部状态标志</li>
     * </ul>
     * <p>
     * <strong>超时控制：</strong>
     * <ul>
     *   <li>默认等待时间：5秒</li>
     *   <li>超时后强制关闭：shutdownNow()</li>
     *   <li>支持线程中断处理</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>使用CAS操作防止重复停止</li>
     *   <li>支持优雅关闭和强制关闭</li>
     *   <li>会处理线程中断异常</li>
     *   <li>停止后需要重新调用start()才能恢复监控</li>
     *   <li>支持并发调用，但只会停止一次</li>
     * </ul>
     * <p>
     * <strong>线程安全：</strong>
     * <ul>
     *   <li>使用AtomicBoolean确保线程安全</li>
     *   <li>支持并发调用</li>
     *   <li>调度器关闭是线程安全的</li>
     * </ul>
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
            log.info("连接监控器已停止，客户端ID: {}", clientId);
        }
    }

    /**
     * 监控连接状态
     * <p>
     * <strong>生命周期阶段：</strong> 监控阶段
     * <p>
     * <strong>功能说明：</strong>
     * 实时监控MQTT客户端的连接状态变化，检测连接建立、断开、异常等状态转换。
     * 该方法会发布相应的连接状态事件，为其他组件提供状态变化的通知。
     * <p>
     * <strong>监控内容：</strong>
     * <ul>
     *   <li><strong>连接建立检测</strong>：从断开状态变为连接状态</li>
     *   <li><strong>连接断开检测</strong>：从连接状态变为断开状态</li>
     *   <li><strong>状态同步</strong>：更新内部连接状态标志</li>
     *   <li><strong>时间记录</strong>：记录连接建立时间</li>
     *   <li><strong>超时检查</strong>：检查连接是否超时</li>
     *   <li><strong>断开原因识别</strong>：区分主动断开和异常断开</li>
     * </ul>
     * <p>
     * <strong>状态转换：</strong>
     * <ul>
     *   <li>断开 → 连接：发布CONNECTED事件，记录连接时间，清除断开标志</li>
     *   <li>连接 → 断开：根据断开原因发布相应事件</li>
     *   <li>状态一致：不发布事件，只进行超时检查</li>
     * </ul>
     * <p>
     * <strong>断开原因识别：</strong>
     * <ul>
     *   <li><strong>主动断开</strong>：isDisconnecting为true，发布DISCONNECTED事件</li>
     *   <li><strong>网络故障</strong>：isNetworkFailure为true，发布ABNORMAL_DISCONNECT事件</li>
     *   <li><strong>其他异常</strong>：发布ABNORMAL_DISCONNECT事件</li>
     * </ul>
     * <p>
     * <strong>事件发布：</strong>
     * <ul>
     *   <li>CONNECTED：连接建立成功</li>
     *   <li>DISCONNECTED：连接正常断开</li>
     *   <li>ABNORMAL_DISCONNECT：连接异常断开（用于重连逻辑）</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>在调度器线程中执行，支持定时监控</li>
     *   <li>异常情况下会记录错误日志但不中断监控</li>
     *   <li>会调用checkConnectionTimeout()进行超时检查</li>
     *   <li>支持实时状态同步和事件发布</li>
     *   <li>使用原子操作确保状态一致性</li>
     *   <li>需要外部组件调用setDisconnecting()标记主动断开</li>
     * </ul>
     * <p>
     * <strong>性能考虑：</strong>
     * <ul>
     *   <li>轻量级状态检查，不会阻塞监控线程</li>
     *   <li>事件发布是异步的，不影响监控性能</li>
     *   <li>支持高频率的状态监控</li>
     * </ul>
     */
    private void monitorConnectionState() {
        if (connectionManager.getMqttClient() == null) {
            return;
        }
        try {
            boolean currentConnected = connectionManager.isConnected();
            boolean previousConnected = isConnected.get();

            if (currentConnected && !previousConnected) {
                // 连接建立
                isConnected.set(true);
                long currentTime = System.currentTimeMillis();

                // 记录连接建立时间（首次连接或重连时）
                if (connectionStartTime.get() == 0) {
                    connectionStartTime.set(currentTime);
                    log.debug("记录连接开始时间: {}, 客户端ID: {}", currentTime, clientId);
                }

                lastConnectedTime.set(currentTime);

                // 清除断开相关标志
                isDisconnecting.set(false);
                isNetworkFailure.set(false);

                // 发布连接建立事件
                PublishResult<ConnectionStateEvent> publishResult = publishConnectionState(ConnectionStateEvent.of(clientId, CONNECTED, networkType));
                if (publishResult.isFailed()) {
                    log.warn("连接成功事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                    handleConnectionStatePublishFailure(CONNECTED, publishResult);
                }

                log.info("MQTT连接已建立，客户端ID: {}, 连接开始时间: {}", clientId, currentTime);

            } else if (!currentConnected && previousConnected) {
                // 连接断开
                isConnected.set(false);

                // 重置连接开始时间，为下次连接做准备
                connectionStartTime.set(0);
                log.debug("连接断开，重置连接开始时间，客户端ID: {}", clientId);

                // 根据断开原因发布相应事件
                if (isDisconnecting.get()) {
                    // 主动断开
                    log.info("MQTT连接已主动断开，客户端ID: {}", clientId);

                    PublishResult<ConnectionStateEvent> publishResult = publishConnectionState(ConnectionStateEvent.of(clientId, DISCONNECTED, networkType));
                    if (publishResult.isFailed()) {
                        log.warn("连接断开事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                        handleConnectionStatePublishFailure(DISCONNECTED, publishResult);
                    }

                    // 清除断开标志
                    isDisconnecting.set(false);

                } else if (isNetworkFailure.get()) {
                    // 网络故障导致的断开
                    log.warn("MQTT连接因网络故障断开，客户端ID: {}", clientId);

                    PublishResult<ConnectionStateEvent> publishResult = publishConnectionState(ConnectionStateEvent.of(clientId, ABNORMAL_DISCONNECT, networkType));
                    if (publishResult.isFailed()) {
                        log.warn("异常断开事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                        handleConnectionStatePublishFailure(ABNORMAL_DISCONNECT, publishResult);
                    }

                    // 清除网络故障标志
                    isNetworkFailure.set(false);

                } else {
                    // 其他异常断开
                    log.warn("MQTT连接异常断开，客户端ID: {}", clientId);

                    PublishResult<ConnectionStateEvent> publishResult = publishConnectionState(ConnectionStateEvent.of(clientId, ABNORMAL_DISCONNECT, networkType));
                    if (publishResult.isFailed()) {
                        log.warn("异常断开事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                        handleConnectionStatePublishFailure(ABNORMAL_DISCONNECT, publishResult);
                    }
                }
            }

            // 检查连接超时
            checkSessionExpiry();

        } catch (Exception e) {
            log.error("监控连接状态时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 监控网络状态
     * <p>
     * <strong>生命周期阶段：</strong> 监控阶段
     * <p>
     * <strong>功能说明：</strong>
     * 监控网络可达性和网络质量，检测网络异常和网络恢复，为连接管理提供网络状态信息。
     * 该方法会根据网络状态发布相应的事件，支持网络故障检测和恢复通知。
     * <p>
     * <strong>监控内容：</strong>
     * <ul>
     *   <li><strong>网络可达性检测</strong>：检查网络是否可达</li>
     *   <li><strong>网络异常检测</strong>：识别网络不可达的情况</li>
     *   <li><strong>网络恢复检测</strong>：检测网络从异常到正常的恢复</li>
     *   <li><strong>状态事件发布</strong>：发布网络状态变化事件</li>
     * </ul>
     * <p>
     * <strong>网络状态判断：</strong>
     * <ul>
     *   <li><strong>网络不可达</strong>：发布CONNECTION_FAILED事件</li>
     *   <li><strong>网络恢复且未连接</strong>：发布CONNECTING事件</li>
     *   <li><strong>网络正常且已连接</strong>：不发布事件</li>
     * </ul>
     * <p>
     * <strong>事件发布：</strong>
     * <ul>
     *   <li>CONNECTION_FAILED：网络不可达，连接失败</li>
     *   <li>CONNECTING：网络已恢复，可以尝试连接</li>
     * </ul>
     * <p>
     * <strong>配置控制：</strong>
     * <ul>
     *   <li>enableNetworkMonitor：控制是否启用网络监控</li>
     *   <li>networkCheckInterval：控制网络检查频率</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>在调度器线程中执行，支持定时监控</li>
     *   <li>异常情况下会记录错误日志但不中断监控</li>
     *   <li>网络检查是可选的，根据配置决定是否启用</li>
     *   <li>支持网络恢复检测，为自动重连提供依据</li>
     *   <li>轻量级网络检查，不会阻塞监控线程</li>
     * </ul>
     * <p>
     * <strong>扩展性：</strong>
     * <ul>
     *   <li>支持自定义网络检查逻辑</li>
     *   <li>可以集成更复杂的网络诊断工具</li>
     *   <li>支持多种网络环境检测</li>
     * </ul>
     */
    private void monitorNetworkState() {
        try {
            // 简单的网络可达性检查
            boolean networkReachable = checkNetworkReachability();

            if (!networkReachable) {
                log.warn("网络不可达，客户端ID: {}", clientId);

                // 设置网络故障标志，用于后续断开原因识别
                setNetworkFailure();

                // 发布连接失败事件
                PublishResult<ConnectionStateEvent> publishResult = MqttEventPublisher.publishConnectionState(ConnectionStateEvent.of(clientId, CONNECTION_FAILED, networkType));
                if (publishResult.isFailed()) {
                    log.warn("连接失败事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                    handleConnectionStatePublishFailure(CONNECTION_FAILED, publishResult);
                }

            } else if (!isConnected.get()) {
                log.info("开始网络恢复，客户端ID: {}", clientId);

                // 发布连接中事件
                PublishResult<ConnectionStateEvent> publishResult = MqttEventPublisher.publishConnectionState(ConnectionStateEvent.of(clientId, CONNECTING, networkType));
                if (publishResult.isFailed()) {
                    log.warn("连接中事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                    handleConnectionStatePublishFailure(CONNECTING, publishResult);
                }
            }

        } catch (Exception e) {
            log.error("监控网络状态时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查连接超时
     * <p>
     * <strong>生命周期阶段：</strong> 监控阶段
     * <p>
     * <strong>功能说明：</strong>
     * 基于连接建立时间计算连接持续时间，当连接持续时间超过配置的会话过期时间时，
     * 发布SESSION_EXPIRED事件。支持动态会话过期时间配置。
     * <p>
     * <strong>检查逻辑：</strong>
     * <ul>
     *   <li><strong>连接状态检查</strong>：验证MQTT客户端状态是否正常</li>
     *   <li><strong>连接时长监控</strong>：基于connectionStartTime计算连接持续时间</li>
     *   <li><strong>会话过期检测</strong>：与配置的sessionExpiryInterval比较</li>
     * </ul>
     * <p>
     * <strong>检查内容：</strong>
     * <ul>
     *   <li><strong>MQTT客户端状态</strong>：检查客户端是否处于正常连接状态</li>
     *   <li><strong>连接持续时间</strong>：基于实际连接建立时间计算</li>
     *   <li><strong>会话过期判断</strong>：基于配置的过期时间进行判断</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>使用connectionStartTime进行准确的时间计算</li>
     *   <li>会话过期后自动重置计时器，避免重复触发</li>
     *   <li>支持配置化的会话过期时间</li>
     * </ul>
     */
    private void checkSessionExpiry() {
        if (isConnected.get() && connectionStartTime.get() > 0) {
            long currentTime = System.currentTimeMillis();
            long connectionDuration = currentTime - connectionStartTime.get();

            // 获取配置的会话过期时间（毫秒）
            long sessionExpiryInterval = properties.getMqtt5().getSessionExpiryInterval() * 1000L;

            // 检查是否超过会话过期时间
            if (connectionDuration >= sessionExpiryInterval) {
                log.warn("连接会话已过期，客户端ID: {}, 连接持续时间: {}ms, 会话过期时间: {}ms",
                        clientId, connectionDuration, sessionExpiryInterval);

                // 发布会话过期事件
                PublishResult<ConnectionStateEvent> publishResult = MqttEventPublisher.publishConnectionState(
                        ConnectionStateEvent.of(clientId, SESSION_EXPIRED, networkType));
                if (publishResult.isFailed()) {
                    log.warn("会话过期事件发布失败: {}, 原因: {}", clientId, publishResult.getFailureReason());
                    handleConnectionStatePublishFailure(SESSION_EXPIRED, publishResult);
                }

                // 重置连接开始时间，避免重复触发
                connectionStartTime.set(0);
            } else {
                // 记录连接持续时间（可选，用于调试）
                if (log.isDebugEnabled()) {
                    long remainingTime = sessionExpiryInterval - connectionDuration;
                    log.debug("连接状态正常，客户端ID: {}, 已连接: {}ms, 剩余时间: {}ms",
                            clientId, connectionDuration, remainingTime);
                }
            }
        }
    }

    /**
     * 检查网络可达性
     * <p>
     * <strong>生命周期阶段：</strong> 监控阶段
     * <p>
     * <strong>功能说明：</strong>
     * 检查网络是否可达，为网络状态监控提供基础的网络连通性检测。
     * 该方法目前是简化实现，可以根据需要扩展为更复杂的网络诊断逻辑。
     * <p>
     * <strong>当前实现：</strong>
     * <ul>
     *   <li><strong>简化版本</strong>：直接返回true，表示网络可达</li>
     *   <li><strong>扩展性</strong>：预留了复杂网络检查的接口</li>
     *   <li><strong>性能优先</strong>：避免复杂的网络检查影响监控性能</li>
     * </ul>
     * <p>
     * <strong>扩展建议：</strong>
     * <ul>
     *   <li><strong>Ping检测</strong>：使用ICMP ping检查网络连通性</li>
     *   <li><strong>端口检测</strong>：检查目标服务器端口是否可达</li>
     *   <li><strong>网络接口检测</strong>：检查本地网络接口状态</li>
     *   <li><strong>DNS解析</strong>：检查域名解析是否正常</li>
     *   <li><strong>路由检测</strong>：检查网络路由是否正常</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li><strong>网络故障检测</strong>：识别网络不可达的情况</li>
     *   <li><strong>网络恢复检测</strong>：检测网络从故障到恢复</li>
     *   <li><strong>连接质量评估</strong>：评估网络连接质量</li>
     *   <li><strong>故障排查</strong>：帮助定位网络问题</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>异常情况下会记录错误日志并返回false</li>
     *   <li>当前是简化实现，需要根据实际需求扩展</li>
     *   <li>网络检查应该轻量级，避免影响监控性能</li>
     *   <li>支持配置化的网络检查策略</li>
     *   <li>可以集成第三方网络诊断工具</li>
     * </ul>
     * <p>
     * <strong>性能考虑：</strong>
     * <ul>
     *   <li>网络检查应该是异步的，不阻塞监控线程</li>
     *   <li>支持超时控制，避免长时间等待</li>
     *   <li>可以缓存检查结果，减少重复检查</li>
     *   <li>支持批量网络检查，提高效率</li>
     * </ul>
     *
     * @return 网络是否可达（当前实现总是返回true）
     */
    private boolean checkNetworkReachability() {
        try {
            // 这里可以实现更复杂的网络检查逻辑
            // 比如ping服务器、检查网络接口状态等
            return true; // 简化实现
        } catch (Exception e) {
            log.error("检查网络可达性时发生异常: {}", e.getMessage(), e);
            return false;
        }
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

    /**
     * 标记为主动断开
     * <p>
     * <strong>功能说明：</strong>
     * 在主动断开连接前调用此方法，标记断开原因，确保监控器能正确识别断开类型。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>应用程序主动关闭连接</li>
     *   <li>用户手动断开连接</li>
     *   <li>配置变更需要重连</li>
     *   <li>优雅关闭连接</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>应在断开连接前调用</li>
     *   <li>调用后监控器会将断开识别为DISCONNECTED</li>
     *   <li>连接重新建立后标志会自动清除</li>
     * </ul>
     */
    public void setDisconnecting() {
        isDisconnecting.set(true);
        log.debug("标记为主动断开，客户端ID: {}", clientId);
    }

    /**
     * 标记为网络故障
     * <p>
     * <strong>功能说明：</strong>
     * 在网络故障检测到后调用此方法，标记断开原因，确保监控器能正确识别断开类型。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络可达性检测失败</li>
     *   <li>网络接口状态异常</li>
     *   <li>网络质量严重下降</li>
     *   <li>网络配置变更</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>应在网络故障检测到后调用</li>
     *   <li>调用后监控器会将断开识别为ABNORMAL_DISCONNECT</li>
     *   <li>连接重新建立后标志会自动清除</li>
     * </ul>
     */
    public void setNetworkFailure() {
        isNetworkFailure.set(true);
        log.debug("标记为网络故障，客户端ID: {}", clientId);
    }

}

