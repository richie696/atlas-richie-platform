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
import com.richie.component.mqtt.beans.NetworkQualityEvent;
import com.richie.component.mqtt.beans.NetworkQualityStats;
import com.richie.component.mqtt.config.MqttClientProperties;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 网络质量监控服务
 * <p>
 * 每秒监控MQTT服务器的网络质量，提供详细的网络统计信息和历史记录。
 * 该类实现了持续的网络质量监控，通过定时任务收集网络性能数据，支持
 * 网络质量趋势分析和故障预警。
 * <p>
 * <strong>核心功能：</strong>
 * <ul>
 *   <li><strong>持续监控</strong>：每秒执行网络质量检测</li>
 *   <li><strong>统计分析</strong>：计算平均延迟、丢包率等指标</li>
 *   <li><strong>历史记录</strong>：保存最近100次网络质量事件</li>
 *   <li><strong>事件发布</strong>：发布网络质量变化事件</li>
 *   <li><strong>故障预警</strong>：网络质量差时发布警告事件</li>
 * </ul>
 * <p>
 * <strong>监控指标：</strong>
 * <ul>
 *   <li><strong>网络延迟</strong>：Ping往返时间，单位毫秒</li>
 *   <li><strong>丢包率</strong>：数据包丢失百分比</li>
 *   <li><strong>成功率</strong>：成功Ping次数占总次数的比例</li>
 *   <li><strong>趋势分析</strong>：网络质量变化趋势</li>
 * </ul>
 * <p>
 * <strong>监控策略：</strong>
 * <ul>
 *   <li><strong>定时监控</strong>：使用@Scheduled注解，每秒执行</li>
 *   <li><strong>异步执行</strong>：避免阻塞定时任务线程</li>
 *   <li><strong>智能过滤</strong>：根据配置和状态决定是否执行</li>
 *   <li><strong>异常处理</strong>：监控失败时记录统计信息</li>
 * </ul>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li>MQTT连接质量实时监控</li>
 *   <li>网络故障诊断和预警</li>
 *   <li>网络性能基准测试</li>
 *   <li>连接质量报告生成</li>
 *   <li>网络切换决策支持</li>
 * </ul>
 * <p>
 * <strong>技术特点：</strong>
 * <ul>
 *   <li>使用Spring的@Scheduled注解实现定时任务</li>
 *   <li>支持异步网络监控，提高系统响应性</li>
 *   <li>使用CircularBuffer保存历史数据</li>
 *   <li>原子操作确保统计数据的准确性</li>
 *   <li>事件总线发布网络质量变化通知</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-08-14
 */
@Slf4j
@Component
public class NetworkQualityManager {

    private final NetworkQualityMonitor networkQualityMonitor;

    private final MqttClientProperties properties;

    private NetworkTypeEnum networkType;

    // 添加clientId字段，延迟初始化
    private String clientId;

    // 监控状态控制
    private final AtomicBoolean monitoring = new AtomicBoolean(false);

    // 网络统计计数器
    private final AtomicLong totalPingCount = new AtomicLong(0);
    private final AtomicLong totalPacketLoss = new AtomicLong(0);
    private final AtomicLong totalLatency = new AtomicLong(0);
    private final AtomicLong successfulPingCount = new AtomicLong(0);

    // 网络质量历史记录（最近100次）
    private final CircularBuffer<NetworkQualityEvent> qualityHistory = new CircularBuffer<>(100);

    /**
     * 构造网络质量管理器
     *
     * @param networkQualityMonitor 网络质量监控器
     * @param properties MQTT客户端配置
     */
    public NetworkQualityManager(NetworkQualityMonitor networkQualityMonitor,
                               MqttClientProperties properties) {
        this.networkQualityMonitor = networkQualityMonitor;
        this.properties = properties;
    }

    /**
     * 初始化网络质量管理器
     * <p>
     * 订阅网络类型变化事件和连接状态变化事件，初始化网络质量监控。
     */
    @PostConstruct
    public void init() {
        MqttEventBus.networkTypeFlow.subscribe(networkType -> this.networkType = networkType);

        // 订阅连接状态变化事件，感知连接状态变化
        MqttEventBus.connectionStateFlow.subscribe(this::handleConnectionStateChange);
    }

    /**
     * 设置客户端ID
     * <p>
     * <strong>功能说明：</strong>
     * 设置客户端ID，用于事件发布和过滤。
     * 该方法应该在MQTT客户端初始化完成后调用。
     * 
     *
     * @param clientId 客户端ID
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
        log.debug("网络质量管理器客户端ID已设置: {}", clientId);
    }

    /**
     * 启动网络质量监控
     * <p>
     * <strong>设计原则：</strong> 状态控制，原子操作，幂等性
     * <p>
     * <strong>功能说明：</strong>
     * 启动网络质量监控服务，开始持续的网络质量检测。该方法使用原子操作
     * 确保监控状态的一致性，支持多次调用而不会产生副作用。
     * <p>
     * <strong>启动机制：</strong>
     * <ul>
     *   <li>使用AtomicBoolean.compareAndSet()确保原子性</li>
     *   <li>只有当前未监控时才能启动</li>
     *   <li>启动后定时任务开始执行</li>
     *   <li>支持并发调用，线程安全</li>
     * </ul>
     * <p>
     * <strong>启动流程：</strong>
     * <ol>
     *   <li>检查当前监控状态</li>
     *   <li>如果未监控，设置监控标志为true</li>
     *   <li>记录启动日志</li>
     *   <li>定时任务开始执行网络监控</li>
     * </ol>
     * <p>
     * <strong>状态变化：</strong>
     * <ul>
     *   <li>monitoring: false → true</li>
     *   <li>定时任务开始执行</li>
     *   <li>网络质量数据开始收集</li>
     *   <li>统计计数器开始累加</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>启动后需要等待下一个定时任务周期</li>
     *   <li>不会立即执行网络监控</li>
     *   <li>支持重复调用，不会重复启动</li>
     *   <li>启动后需要调用stopMonitoring()才能停止</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>系统启动时初始化网络监控</li>
     *   <li>网络监控暂停后重新启动</li>
     *   <li>配置变更后重启监控</li>
     *   <li>手动控制监控状态</li>
     * </ul>
     */
    public void startMonitoring() {
        if (monitoring.compareAndSet(false, true)) {
            log.info("网络质量监控已启动");
        }
    }

    /**
     * 停止网络质量监控
     */
    public void stopMonitoring() {
        if (monitoring.compareAndSet(true, false)) {
            log.info("网络质量监控已停止");
        }
    }

    /**
     * 检查监控是否正在运行
     *
     * @return 如果监控正在运行返回true，否则返回false
     */
    public boolean isMonitoring() {
        return monitoring.get();
    }

    /**
     * 每秒监控网络质量
     * <p>
     * <strong>设计原则：</strong> 定时执行，异步处理，智能过滤
     * <p>
     * <strong>功能说明：</strong>
     * 定时执行网络质量监控，每秒检测一次MQTT服务器的网络状态。
     * 该方法使用Spring的@Scheduled注解实现定时执行，通过异步方式
     * 避免阻塞定时任务线程，提高系统响应性。
     * <p>
     * <strong>执行条件：</strong>
     * <ul>
     *   <li>网络质量监控功能已启用（properties.getEnable()）</li>
     *   <li>监控服务正在运行（monitoring.get()）</li>
     *   <li>服务器地址配置有效（host不为空）</li>
     *   <li>满足所有条件时才会执行监控</li>
     * </ul>
     * <p>
     * <strong>执行流程：</strong>
     * <ol>
     *   <li>检查执行条件（功能启用、监控运行、地址有效）</li>
     *   <li>获取MQTT服务器地址</li>
     *   <li>异步执行网络质量监控（1次Ping，3秒超时）</li>
     *   <li>处理监控结果或异常</li>
     *   <li>更新统计信息和历史记录</li>
     * </ol>
     * <p>
     * <strong>异步处理：</strong>
     * <ul>
     *   <li>使用CompletableFuture.runAsync()异步执行</li>
     *   <li>避免阻塞定时任务线程</li>
     *   <li>支持并发网络监控</li>
     *   <li>提高系统整体响应性</li>
     * </ul>
     * <p>
     * <strong>监控参数：</strong>
     * <ul>
     *   <li>Ping次数：1次（快速检测）</li>
     *   <li>超时时间：3000毫秒（3秒）</li>
     *   <li>执行频率：每秒一次</li>
     *   <li>适合实时网络质量监控</li>
     * </ul>
     * <p>
     * <strong>异常处理：</strong>
     * <ul>
     *   <li>监控失败时创建失败事件</li>
     *   <li>记录错误日志便于排查</li>
     *   <li>失败事件也会被统计和处理</li>
     *   <li>确保监控过程的连续性</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>每秒执行一次，注意资源消耗</li>
     *   <li>异步执行，结果处理有延迟</li>
     *   <li>失败情况下会记录统计信息</li>
     *   <li>建议根据网络环境调整执行频率</li>
     * </ul>
     */
    @Scheduled(fixedRate = 1000)
    public void monitorNetworkQuality() {
        if (!properties.getEnable() || !monitoring.get()) {
            return;
        }

        String host = properties.getServer().getHost();
        int port = properties.getServer().getPort();
        if (host == null || host.trim().isEmpty()) {
            return;
        }

        // 异步监控网络质量，避免阻塞定时任务
        CompletableFuture.runAsync(() -> {
            try {
                NetworkQualityEvent event = networkQualityMonitor.monitorNetworkQuality(host, port, 5);
                processNetworkQualityEvent(event);
            } catch (Exception e) {
                log.error("网络质量监控异常: {}", e.getMessage(), e);
                // 记录失败的监控事件
                NetworkQualityEvent failedEvent = new NetworkQualityEvent(-1, -1.0f);
                processNetworkQualityEvent(failedEvent);
            }
        });
    }

    /**
     * 处理网络质量事件
     * <p>
     * <strong>设计原则：</strong> 事件驱动，数据统计，智能预警
     * <p>
     * <strong>功能说明：</strong>
     * 处理网络质量监控结果，更新统计信息，保存历史记录，发布相关事件。
     * 该方法是网络质量监控的核心处理逻辑，负责数据聚合和事件分发。
     * <p>
     * <strong>处理流程：</strong>
     * <ol>
     *   <li>判断Ping操作是否成功（延迟 >= 0）</li>
     *   <li>更新成功/失败统计计数器</li>
     *   <li>累加延迟时间（仅成功情况）</li>
     *   <li>保存事件到历史记录</li>
     *   <li>发布网络质量事件</li>
     *   <li>检查是否需要发布网络质量警告</li>
     *   <li>定期输出统计信息</li>
     * </ol>
     * <p>
     * <strong>统计更新：</strong>
     * <ul>
     *   <li><strong>成功Ping</strong>：incrementAndGet()成功计数器，累加延迟</li>
     *   <li><strong>失败Ping</strong>：incrementAndGet()丢包计数器</li>
     *   <li><strong>总计数</strong>：每次都会incrementAndGet()总计数器</li>
     *   <li><strong>历史记录</strong>：所有事件都会保存到CircularBuffer</li>
     * </ul>
     * <p>
     * <strong>事件发布：</strong>
     * <ul>
     *   <li><strong>网络质量事件</strong>：每次监控结果都会发布</li>
     *   <li><strong>连接状态警告</strong>：网络质量差时发布POOR_NETWORK事件</li>
     *   <li><strong>警告条件</strong>：延迟 > 100ms 或 丢包率 > 10%</li>
     *   <li><strong>事件数据</strong>：包含网络质量信息和时间戳</li>
     * </ul>
     * <p>
     * <strong>日志输出：</strong>
     * <ul>
     *   <li><strong>调试日志</strong>：每次成功的监控结果</li>
     *   <li><strong>警告日志</strong>：监控失败的情况</li>
     *   <li><strong>统计日志</strong>：每100次Ping输出一次统计</li>
     *   <li><strong>日志级别</strong>：根据情况使用不同级别</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>使用原子操作确保统计数据的准确性</li>
     *   <li>历史记录使用CircularBuffer，自动覆盖旧数据</li>
     *   <li>事件发布是异步的，不会阻塞处理流程</li>
     *   <li>警告事件包含完整的网络质量上下文</li>
     *   <li>统计信息每100次输出一次，避免日志过多</li>
     * </ul>
     *
     * @param event 网络质量事件对象
     */
    private void processNetworkQualityEvent(NetworkQualityEvent event) {
        if (event.getPingLatency() >= 0) {
            // 成功的Ping
            successfulPingCount.incrementAndGet();
            totalLatency.addAndGet(event.getPingLatency());

            log.debug("网络质量监控 - 延迟: {}ms, 丢包率: {}%",
                    event.getPingLatency(), Math.round(event.getPacketLoss() * 100.0f) / 100.0f);
        } else {
            // 失败的Ping，视为丢包
            totalPacketLoss.incrementAndGet();
            log.warn("网络质量监控失败，视为丢包");
        }

        // 总Ping次数增加
        totalPingCount.incrementAndGet();

        // 保存到历史记录
        qualityHistory.add(event);

        // 发布网络质量事件
        PublishResult<NetworkQualityEvent> publishResult = MqttEventPublisher.publishNetworkQuality(event);
        if (publishResult.isFailed()) {
            log.debug("网络质量事件发布失败: {}, 原因: {}", event.getPingLatency(), publishResult.getFailureReason());
            handleNetworkQualityPublishFailure(event, publishResult);
        }

        // 如果网络质量很差，发布连接状态警告事件
        if (event.getPingLatency() > 100 || event.getPacketLoss() > 10) {
            // 检查clientId是否已设置
            if (clientId == null || clientId.isEmpty()) {
                log.debug("客户端ID未设置，跳过网络质量警告事件发布");
                return;
            }

            ConnectionStateEvent poorNetworkEvent = new ConnectionStateEvent(
                    clientId,  // 使用正确的clientId
                    ConnectionState.POOR_NETWORK,
                    networkType,
                    System.currentTimeMillis(),
                    event
            );
            PublishResult<ConnectionStateEvent> connectionStateResult = MqttEventPublisher.publishConnectionState(poorNetworkEvent);
            if (connectionStateResult.isFailed()) {
                log.warn("网络质量警告事件发布失败: {}, 原因: {}", poorNetworkEvent.getState(), connectionStateResult.getFailureReason());
                handleConnectionStatePublishFailure(poorNetworkEvent.getState(), connectionStateResult);
            }
        }

        // 每100次Ping输出一次统计信息
        if (totalPingCount.get() % 100 == 0) {
            logNetworkQualityStats();
        }
    }

    /**
     * 处理连接状态变化事件
     *
     * @param event 连接状态事件对象
     */
    private void handleConnectionStateChange(ConnectionStateEvent event) {
        // 检查clientId是否已设置
        if (clientId == null || clientId.isEmpty()) {
            log.debug("客户端ID未设置，跳过连接状态事件处理");
            return;
        }

        // 只处理当前客户端的事件
        if (!clientId.equals(event.getClientId())) {
            return;
        }

        log.debug("接收到连接状态变化事件: {}", event.getState());
        // 可以根据需要更新网络质量监控的状态或统计
        // 例如，如果连接断开，可以停止网络质量监控
        if (event.getState() == ConnectionState.DISCONNECTED) {
            stopMonitoring();
            log.warn("连接断开，停止网络质量监控");
        }
    }

    /**
     * 输出网络质量统计信息
     */
    private void logNetworkQualityStats() {
        long total = totalPingCount.get();
        long successful = successfulPingCount.get();
        long lost = totalPacketLoss.get();
        long avgLatency = total > 0 ? totalLatency.get() / total : 0;
        float packetLossRate = total > 0 ? (float) lost / total * 100 : 0;
        packetLossRate = Math.round(packetLossRate * 100.0f) / 100.0f;

        log.info("网络质量统计 - 总Ping次数: {}, 成功: {}, 丢包: {}, 平均延迟: {}ms, 丢包率: {}%",
                total, successful, lost, avgLatency, packetLossRate);
    }

    /**
     * 获取网络质量统计信息
     * <p>
     * <strong>设计原则：</strong> 数据聚合，实时计算，完整统计
     * <p>
     * <strong>功能说明：</strong>
     * 获取完整的网络质量统计信息，包括累计数据、平均值、历史记录等。
     * 该方法实时计算统计指标，提供全面的网络质量分析数据。
     * <p>
     * <strong>统计指标：</strong>
     * <ul>
     *   <li><strong>总Ping次数</strong>：累计执行的Ping操作总数</li>
     *   <li><strong>成功Ping次数</strong>：成功响应的Ping操作数量</li>
     *   <li><strong>丢包次数</strong>：失败或无响应的Ping操作数量</li>
     *   <li><strong>平均延迟</strong>：成功Ping的平均往返时间</li>
     *   <li><strong>丢包率</strong>：丢包次数占总次数的百分比</li>
     *   <li><strong>历史记录</strong>：最近100次网络质量事件</li>
     * </ul>
     * <p>
     * <strong>计算方法：</strong>
     * <ul>
     *   <li><strong>平均延迟</strong>：totalLatency / successfulPingCount</li>
     *   <li><strong>丢包率</strong>：(lost / total) * 100</li>
     *   <li><strong>成功率</strong>：(successful / total) * 100</li>
     *   <li><strong>数据来源</strong>：原子计数器，确保准确性</li>
     * </ul>
     * <p>
     * <strong>数据特点：</strong>
     * <ul>
     *   <li><strong>实时性</strong>：每次调用都重新计算</li>
     *   <li><strong>准确性</strong>：使用原子操作确保数据一致性</li>
     *   <li><strong>完整性</strong>：包含所有关键网络质量指标</li>
     *   <li><strong>历史性</strong>：提供最近100次监控记录</li>
     * </ul>
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络质量报告生成</li>
     *   <li>性能监控面板显示</li>
     *   <li>网络故障诊断分析</li>
     *   <li>连接质量评估</li>
     *   <li>运维监控和告警</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>统计数据会持续累加，直到调用resetNetworkQualityStats()</li>
     *   <li>历史记录使用CircularBuffer，最多保存100条</li>
     *   <li>平均延迟只计算成功的Ping操作</li>
     *   <li>丢包率包含监控失败的情况</li>
     * </ul>
     *
     * @return NetworkQualityStats 包含完整网络质量统计信息的对象
     */
    public NetworkQualityStats getNetworkQualityStats() {
        long total = totalPingCount.get();
        long successful = successfulPingCount.get();
        long lost = totalPacketLoss.get();
        long avgLatency = total > 0 ? totalLatency.get() / total : 0;
        float packetLossRate = total > 0 ? (float) lost / total * 100 : 0;

        return NetworkQualityStats.builder()
                .totalPingCount(total)
                .successfulPingCount(successful)
                .packetLossCount(lost)
                .averageLatency(avgLatency)
                .packetLossRate(packetLossRate)
                .recentQualityEvents(qualityHistory.toList())
                .build();
    }

    /**
     * 手动触发网络质量检查
     */
    public void triggerNetworkQualityCheck() {
        monitorNetworkQuality();
    }

    /**
     * 重置网络质量统计
     */
    public void resetNetworkQualityStats() {
        totalPingCount.set(0);
        totalPacketLoss.set(0);
        totalLatency.set(0);
        successfulPingCount.set(0);
        qualityHistory.clear();
        log.info("网络质量统计已重置");
    }

    /**
     * 处理网络质量事件发布失败
     *
     * @param event         发布失败的网络质量事件
     * @param publishResult 发布结果
     */
    private void handleNetworkQualityPublishFailure(NetworkQualityEvent event, PublishResult<NetworkQualityEvent> publishResult) {
        switch (publishResult.getFailureReason()) {
            case BUFFER_OVERFLOW:
                log.warn("网络质量缓冲区溢出，事件被丢弃: 延迟={}ms", event.getPingLatency());
                // 网络质量事件丢失通常不是严重问题，可以忽略
                break;

            case SINK_TERMINATED:
                log.error("事件总线已终止，无法发布网络质量事件: 延迟={}ms", event.getPingLatency());
                // 需要重新初始化事件总线或重新订阅
                break;

            case EXCEPTION:
                log.error("发布网络质量事件时发生异常: 延迟={}ms", event.getPingLatency(), publishResult.getException());
                // 异常处理逻辑
                break;

            default:
                log.debug("网络质量事件发布失败: 延迟={}ms, 原因: {}", event.getPingLatency(), publishResult.getFailureReason());
                break;
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
}
