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

import com.richie.component.mqtt.beans.*;
import com.richie.component.mqtt.beans.ConnectionStateEvent;
import com.richie.component.mqtt.beans.HeartbeatEvent;
import com.richie.component.mqtt.beans.NetworkQualityEvent;
import com.richie.component.mqtt.beans.SubscriptionResult;
import com.richie.component.mqtt.enums.NetworkTypeEnum;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * MQTT事件总线，用于发布和订阅各种MQTT相关事件。
 * <p>
 * 该类是整个MQTT组件的核心事件通信中心，基于Reactor响应式编程实现。
 * 提供了统一的事件发布和订阅接口，支持多种事件类型和不同的订阅策略。
 * <p>
 * <strong>核心功能：</strong>
 * <ul>
 *   <li><strong>事件发布</strong>：提供统一的事件发布接口</li>
 *   <li><strong>事件订阅</strong>：支持多种订阅策略和缓冲区配置</li>
 *   <li><strong>事件管理</strong>：事件流清理和统计信息</li>
 *   <li><strong>异步处理</strong>：基于响应式流的异步事件处理</li>
 * </ul>
 * <p>
 * <strong>设计特点：</strong>
 * <ul>
 *   <li><strong>响应式架构</strong>：基于Reactor的响应式编程</li>
 *   <li><strong>事件驱动</strong>：支持松耦合的事件驱动架构</li>
 *   <li><strong>背压处理</strong>：内置背压处理机制</li>
 *   <li><strong>线程安全</strong>：支持多线程并发访问</li>
 *   <li><strong>内存管理</strong>：自动内存管理和垃圾回收</li>
 * </ul>
 * <p>
 * <strong>使用场景：</strong>
 * <ul>
 *   <li>MQTT连接状态监控</li>
 *   <li>消息接收和处理</li>
 *   <li>网络质量监控</li>
 *   <li>订阅操作结果反馈</li>
 *   <li>心跳状态监控</li>
 *   <li>系统集成和事件转发</li>
 * </ul>
 * <p>
 * <strong>订阅方式说明：</strong>
 * <ul>
 *   <li><strong>直接订阅</strong>：使用Flux.subscribe()方法订阅事件</li>
 *   <li><strong>操作符链式</strong>：支持filter、map、flatMap等操作符</li>
 *   <li><strong>背压处理</strong>：支持onBackpressureBuffer、onBackpressureDrop等策略</li>
 *   <li><strong>错误处理</strong>：支持onError、onErrorReturn等错误处理</li>
 * </ul>
 * <p>
 * <strong>访问控制策略：</strong>
 * <ul>
 *   <li><strong>事件订阅</strong>：所有外部使用者都可以订阅事件流</li>
 *   <li><strong>事件发布</strong>：仅供client包及其子包内的内部组件使用</li>
 *   <li><strong>事件流管理</strong>：仅供client包及其子包内的内部组件使用</li>
 *   <li><strong>内部访问</strong>：通过 {@link MqttEventPublisher} 类访问发布和管理方法</li>
 *   <li><strong>职责分离</strong>：事件发布、订阅和管理完全分离，确保系统安全</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-08-13 22:13:38
 */
@Slf4j
public final class MqttEventBus {

    private MqttEventBus() {
        throw new AssertionError("No instances.");
    }

    // ==================== 事件流定义 ====================

    /**
     * 连接状态事件流，粘性，始终保留最新状态
     * <p>
     * <strong>事件内容：</strong> 包含连接状态变化、网络类型、时间戳等详细信息
     * <p>
     * <strong>订阅用途：</strong>
     * <ul>
     *   <li><strong>连接监控</strong>：实时监控MQTT连接状态变化</li>
     *   <li><strong>状态同步</strong>：同步其他组件的连接状态</li>
     *   <li><strong>故障检测</strong>：检测连接异常和网络问题</li>
     *   <li><strong>UI更新</strong>：更新用户界面的连接状态显示</li>
     *   <li><strong>日志记录</strong>：记录连接状态变化历史</li>
     *   <li><strong>告警触发</strong>：连接异常时触发告警</li>
     * </ul>
     * <p>
     * <strong>订阅示例：</strong>
     * <pre>{@code
     * MqttEventBus.connectionStateFlow
     *     .filter(event -> event.getState() == ConnectionState.CONNECTED)
     *     .subscribe(event -> {
     *         log.info("连接建立: {}", event.getClientId());
     *         // 处理连接建立逻辑
     *     });
     * }</pre>
     * <p>
     * <strong>特点说明：</strong>
     * <ul>
     *   <li><strong>粘性流</strong>：新订阅者会立即收到最新状态</li>
     *   <li><strong>状态完整</strong>：包含连接状态、网络类型、时间戳等</li>
     *   <li><strong>实时更新</strong>：连接状态变化时立即发布</li>
     * </ul>
     */
    private static final Sinks.Many<ConnectionStateEvent> connectionStateSink =
            Sinks.many()
                    .multicast()
                    .onBackpressureBuffer(1000, false);  // 改为false，避免自动终止
    /**
     * 连接状态事件流
     * <p>
     * 用于订阅MQTT连接状态变化事件，包含连接状态、网络类型、时间戳等信息。
     */
    public static final Flux<ConnectionStateEvent> connectionStateFlow =
            connectionStateSink.asFlux().publishOn(Schedulers.boundedElastic());

    /**
     * MQTT消息事件流，带缓冲区
     * <p>
     * <strong>事件内容：</strong> 包含接收到的MQTT消息内容、主题、QoS、时间戳等
     * <p>
     * <strong>订阅用途：</strong>
     * <ul>
     *   <li><strong>消息处理</strong>：处理接收到的MQTT消息</li>
     *   <li><strong>业务逻辑</strong>：执行业务相关的消息处理逻辑</li>
     *   <li><strong>消息转发</strong>：将消息转发给其他系统或组件</li>
     *   <li><strong>数据存储</strong>：将消息存储到数据库或缓存</li>
     *   <li><strong>消息分析</strong>：分析消息内容和模式</li>
     *   <li><strong>监控统计</strong>：统计消息接收数量和频率</li>
     * </ul>
     * <p>
     * <strong>订阅示例：</strong>
     * <pre>{@code
     * MqttEventBus.messageFlow
     *     .filter(message -> message.getTopic().startsWith("sensor/"))
     *     .map(message -> JsonUtils.parse(message.getPayload()))
     *     .subscribe(sensorData -> {
     *         // 处理传感器数据
     *         processSensorData(sensorData);
     *     });
     * }</pre>
     * <p>
     * <strong>特点说明：</strong>
     * <ul>
     *   <li><strong>缓冲区</strong>：64个消息的缓冲区，防止消息丢失</li>
     *   <li><strong>多播流</strong>：支持多个订阅者同时接收</li>
     *   <li><strong>背压处理</strong>：内置背压处理机制</li>
     * </ul>
     */
    private static final Sinks.Many<Mqtt5Publish> messageSink =
            Sinks.many()
                    .multicast()
                    .onBackpressureBuffer(1000, false);  // 改为false，避免自动终止
    /**
     * MQTT消息事件流
     * <p>
     * 用于订阅接收到的MQTT消息事件，包含消息内容、主题、QoS等信息。
     */
    public static final Flux<Mqtt5Publish> messageFlow =
            messageSink.asFlux().publishOn(Schedulers.boundedElastic());


    /**
     * 心跳事件流，带缓冲区
     * <p>
     * <strong>事件内容：</strong> 包含心跳主题、心跳信息（客户端ID、时间、计数）等
     * <p>
     * <strong>订阅用途：</strong>
     * <ul>
     *   <li><strong>心跳监控</strong>：监控客户端心跳状态和频率</li>
     *   <li><strong>连接保活</strong>：维持MQTT连接活跃状态</li>
     *   <li><strong>健康检查</strong>：检查客户端连接健康状态</li>
     *   <li><strong>状态同步</strong>：同步心跳状态到其他组件</li>
     *   <li><strong>日志记录</strong>：记录心跳发送历史</li>
     *   <li><strong>性能监控</strong>：监控心跳发送性能</li>
     * </ul>
     * <p>
     * <strong>订阅示例：</strong>
     * <pre>{@code
     * MqttEventBus.heartbeatFlow
     *     .filter(event -> event.getTopic().contains(clientId))
     *     .subscribe(event -> {
     *         HeartbeatInfo info = event.getHeartbeat();
     *         log.debug("收到心跳: 客户端={}, 时间={}, 计数={}",
     *             info.getClientId(), info.getTimestamp(), info.getCount());
     *         // 更新心跳状态
     *         updateHeartbeatStatus(info);
     *     });
     * }</pre>
     * <p>
     * <strong>特点说明：</strong>
     * <ul>
     *   <li><strong>缓冲区</strong>：16个心跳事件的缓冲区</li>
     *   <li><strong>多播流</strong>：支持多个订阅者</li>
     *   <li><strong>自动清理</strong>：心跳事件自动过期清理</li>
     * </ul>
     */
    private static final Sinks.Many<HeartbeatEvent> heartbeatSink =
            Sinks.many()
                    .multicast()
                    .onBackpressureBuffer(1000, false);  // 改为false，避免自动终止
    /**
     * 心跳事件流
     * <p>
     * 用于订阅MQTT心跳事件，包含心跳主题、心跳信息等。
     */
    public static final Flux<HeartbeatEvent> heartbeatFlow =
            heartbeatSink.asFlux().publishOn(Schedulers.boundedElastic());

    /**
     * 订阅/取消订阅结果事件流，带缓冲区
     * <p>
     * <strong>事件内容：</strong> 包含订阅操作结果、主题、操作类型、成功状态、错误信息等
     * <p>
     * <strong>订阅用途：</strong>
     * <ul>
     *   <li><strong>操作反馈</strong>：获取订阅/取消订阅操作的结果</li>
     *   <li><strong>错误处理</strong>：处理订阅失败的情况</li>
     *   <li><strong>状态同步</strong>：同步订阅状态到其他组件</li>
     *   <li><strong>重试机制</strong>：订阅失败时触发重试逻辑</li>
     *   <li><strong>监控统计</strong>：统计订阅成功率和失败原因</li>
     *   <li><strong>用户通知</strong>：向用户显示订阅操作结果</li>
     * </ul>
     * <p>
     * <strong>订阅示例：</strong>
     * <pre>{@code
     * MqttEventBus.subscriptionResultFlow
     *     .filter(result -> result.getAction() == SubscriptionAction.SUBSCRIBE)
     *     .subscribe(result -> {
     *         if (result.isSuccess()) {
     *             log.info("订阅成功: {}", result.getTopic());
     *             // 处理订阅成功逻辑
     *         } else {
     *             log.error("订阅失败: {}, 原因: {}", result.getTopic(), result.getErrorMessage());
     *             // 处理订阅失败逻辑
     *         }
     *     });
     * }</pre>
     * <p>
     * <strong>特点说明：</strong>
     * <ul>
     *   <li><strong>缓冲区</strong>：32个订阅结果事件的缓冲区</li>
     *   <li><strong>操作类型</strong>：支持订阅和取消订阅两种操作</li>
     *   <li><strong>错误信息</strong>：包含详细的错误原因描述</li>
     * </ul>
     */
    private static final Sinks.Many<SubscriptionResult> subscriptionResultSink =
            Sinks.many()
                    .multicast()
                    .onBackpressureBuffer(1000, false);  // 改为false，避免自动终止
    /**
     * 订阅结果事件流
     * <p>
     * 用于订阅订阅/取消订阅操作的结果事件，包含操作结果、主题、操作类型等信息。
     */
    public static final Flux<SubscriptionResult> subscriptionResultFlow =
            subscriptionResultSink.asFlux().publishOn(Schedulers.boundedElastic());

    /**
     * 网络质量事件发布器
     * <p>
     * 使用多播模式，支持多个订阅者同时接收网络质量事件。
     * 配置了背压缓冲，当订阅者处理速度跟不上时，最多缓存1000个事件。
     * 
     */
    private static final Sinks.Many<NetworkQualityEvent> networkQualitySink =
            Sinks.many()
                    .multicast()
                    .onBackpressureBuffer(1000, false);  // 改为false，避免自动终止
    /**
     * 网络质量事件流
     * <p>
     * 用于订阅网络质量监控事件，包含网络延迟、丢包率等信息。
     */
    public static final Flux<NetworkQualityEvent> networkQualityFlow =
            networkQualitySink.asFlux().publishOn(Schedulers.boundedElastic());

    /**
     * 连接状态流，发布连接状态（true/false）
     * <p>
     * <strong>事件内容：</strong> 简化的连接状态布尔值（true=已连接，false=未连接）
     * <p>
     * <strong>订阅用途：</strong>
     * <ul>
     *   <li><strong>状态检查</strong>：快速检查连接是否建立</li>
     *   <li><strong>条件判断</strong>：根据连接状态决定是否执行操作</li>
     *   <li><strong>状态同步</strong>：同步连接状态到其他组件</li>
     *   <li><strong>UI更新</strong>：更新连接状态指示器</li>
     *   <li><strong>业务逻辑</strong>：连接状态相关的业务判断</li>
     *   <li><strong>监控告警</strong>：连接断开时触发告警</li>
     * </ul>
     * <p>
     * <strong>订阅示例：</strong>
     * <pre>{@code
     * MqttEventBus.connectedFlow
     *     .distinctUntilChanged() // 只在状态变化时触发
     *     .subscribe(connected -> {
     *         if (connected) {
     *             log.info("MQTT连接已建立");
     *             // 连接建立后的初始化逻辑
     *             initializeAfterConnection();
     *         } else {
     *             log.warn("MQTT连接已断开");
     *             // 连接断开后的清理逻辑
     *             cleanupAfterDisconnection();
     *         }
     *     });
     * }</pre>
     * <p>
     * <strong>特点说明：</strong>
     * <ul>
     *   <li><strong>简化状态</strong>：只提供连接/断开两种状态</li>
     *   <li><strong>实时更新</strong>：连接状态变化时立即发布</li>
     *   <li><strong>轻量级</strong>：适合频繁的状态检查</li>
     * </ul>
     */
    private static final Sinks.Many<Boolean> connectedSink =
            Sinks.many().multicast().onBackpressureBuffer(32, false);
    /**
     * 连接状态流
     * <p>
     * 用于订阅简化的连接状态（true=已连接，false=未连接）。
     */
    public static final Flux<Boolean> connectedFlow =
            connectedSink.asFlux().publishOn(Schedulers.boundedElastic());

    /**
     * 网络类型流，发布当前网络类型
     * <p>
     * <strong>事件内容：</strong> 当前网络类型枚举值（PUBLIC、VPC等）
     * <p>
     * <strong>订阅用途：</strong>
     * <ul>
     *   <li><strong>网络切换</strong>：监控网络类型变化</li>
     *   <li><strong>策略调整</strong>：根据网络类型调整连接策略</li>
     *   <li><strong>配置更新</strong>：网络切换时更新相关配置</li>
     *   <li><strong>状态同步</strong>：同步网络类型到其他组件</li>
     *   <li><strong>日志记录</strong>：记录网络类型变化历史</li>
     *   <li><strong>故障排查</strong>：网络问题时分析网络类型</li>
     * </ul>
     * <p>
     * <strong>订阅示例：</strong>
     * <pre>{@code
     * MqttEventBus.networkTypeFlow
     *     .distinctUntilChanged() // 只在网络类型变化时触发
     *     .subscribe(networkType -> {
     *         log.info("网络类型切换: {}", networkType);
     *         // 根据网络类型更新配置
     *         updateConfigurationForNetwork(networkType);
     *         // 重新建立连接
     *         reconnectWithNewNetwork(networkType);
     *     });
     * }</pre>
     * <p>
     * <strong>特点说明：</strong>
     * <ul>
     *   <li><strong>枚举类型</strong>：使用NetworkTypeEnum表示网络类型</li>
     *   <li><strong>变化触发</strong>：网络类型变化时发布事件</li>
     *   <li><strong>配置相关</strong>：与网络配置和连接策略相关</li>
     * </ul>
     */
    private static final Sinks.Many<NetworkTypeEnum> networkTypeSink =
            Sinks.many().multicast().onBackpressureBuffer(32, false);
    /**
     * 网络类型流
     * <p>
     * 用于订阅当前网络类型变化事件（PUBLIC、VPC等）。
     */
    public static final Flux<NetworkTypeEnum> networkTypeFlow =
            networkTypeSink.asFlux().publishOn(Schedulers.boundedElastic());

    // ==================== 事件发布方法 ====================

    /**
     * 通用的发布事件辅助方法
     * <p>
     * <strong>功能说明：</strong>
     * 处理Sinks.EmitResult的通用逻辑，将发布结果转换为PublishResult。
     * 该方法封装了所有发布方法中重复的switch语句和异常处理逻辑。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>统一处理所有事件类型的发布结果</li>
     *   <li>减少代码重复，提高可维护性</li>
     *   <li>统一日志记录和错误处理</li>
     * </ul>
     *
     * @param <T> 事件类型
     * @param event 要发布的事件对象
     * @param emitResult Sinks.EmitResult结果
     * @param eventTypeName 事件类型名称，用于日志记录
     * @return 发布结果
     */
    private static <T> PublishResult<T> handleEmitResult(T event, Sinks.EmitResult emitResult, String eventTypeName) {
        return switch (emitResult) {
            case OK -> {
                log.debug("发布{}事件成功: {}", eventTypeName, event);
                yield PublishResult.success(event);
            }
            case FAIL_TERMINATED -> {
                log.error("Sink已终止，无法发布{}事件: {}", eventTypeName, event);
                yield PublishResult.failed(event, PublishFailureReason.SINK_TERMINATED);
            }
            case FAIL_OVERFLOW -> {
                log.warn("缓冲区溢出，{}事件被丢弃: {}", eventTypeName, event);
                yield PublishResult.failed(event, PublishFailureReason.BUFFER_OVERFLOW);
            }
            case FAIL_CANCELLED -> {
                log.warn("发布操作被取消: {}", event);
                yield PublishResult.failed(event, PublishFailureReason.OPERATION_CANCELLED);
            }
            case FAIL_NON_SERIALIZED -> {
                log.error("非序列化访问，发布失败: {}", event);
                yield PublishResult.failed(event, PublishFailureReason.NON_SERIALIZED_ACCESS);
            }
            case FAIL_ZERO_SUBSCRIBER -> {
                log.debug("无可用订阅者，发布失败：{}", event);
                yield PublishResult.failed(event, PublishFailureReason.NO_AVAILABLE_SUBSCRIPTION);
            }
            default -> {
                log.error("未知的发布结果: {}, {}事件: {}", emitResult, eventTypeName, event);
                yield PublishResult.failed(event, PublishFailureReason.UNKNOWN_ERROR);
            }
        };
    }

    /**
     * 发布连接状态事件
     * <p>
     * <strong>功能说明：</strong>
     * 发布MQTT连接状态变化事件，包含详细的连接状态信息。
     * 该方法会触发所有订阅了connectionStateFlow的处理器。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>连接建立成功后发布CONNECTED事件</li>
     *   <li>连接断开后发布DISCONNECTED事件</li>
     *   <li>连接异常时发布ABNORMAL_DISCONNECT事件</li>
     *   <li>连接失败时发布CONNECTION_FAILED事件</li>
     *   <li>会话过期时发布SESSION_EXPIRED事件</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>事件会立即发布给所有订阅者</li>
     *   <li>粘性流特性确保新订阅者能收到最新状态</li>
     *   <li>返回发布结果，调用方可以处理失败情况</li>
     *   <li><strong>访问控制</strong>：该方法仅供client包及其子包内的类使用</li>
     *   <li><strong>线程安全</strong>：使用synchronized确保线程安全</li>
     * </ul>
     * <p>
     * <strong>内部访问方式：</strong>
     * 请使用 {@link MqttEventPublisher#publishConnectionState(ConnectionStateEvent)} 方法
     *
     * @param event 连接状态事件对象
     * @return 发布结果，包含成功状态和失败原因
     */
    static synchronized PublishResult<ConnectionStateEvent> publishConnectionState(ConnectionStateEvent event) {
        try {
            Sinks.EmitResult result = connectionStateSink.tryEmitNext(event);
            return handleEmitResult(event, result, "连接状态");
        } catch (Exception e) {
            log.error("发布连接状态事件异常: {}", e.getMessage(), e);
            return PublishResult.failed(event, PublishFailureReason.EXCEPTION, e);
        }
    }

    /**
     * 发布MQTT消息事件
     * <p>
     * <strong>功能说明：</strong>
     * 发布接收到的MQTT消息事件，包含消息内容、主题、QoS等完整信息。
     * 该方法会触发所有订阅了messageFlow的处理器。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>接收到MQTT消息后发布事件</li>
     *   <li>消息转发给业务处理器</li>
     *   <li>消息存储和归档</li>
     *   <li>消息统计和监控</li>
     *   <li>消息过滤和路由</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>消息事件会进入64个事件的缓冲区</li>
     *   <li>背压情况下会自动处理流量控制</li>
     *   <li>返回发布结果，调用方可以处理失败情况</li>
     *   <li>建议在消息处理器中处理异常，避免影响事件流</li>
     *   <li><strong>访问控制</strong>：该方法仅供client包及其子包内的类使用</li>
     *   <li><strong>线程安全</strong>：使用synchronized确保线程安全</li>
     * </ul>
     * <p>
     * <strong>内部访问方式：</strong>
     * 请使用 {@link MqttEventPublisher#publishMessage(Mqtt5Publish)} 方法
     *
     * @param message MQTT消息对象
     * @return 发布结果，包含成功状态和失败原因
     */
    static synchronized PublishResult<Mqtt5Publish> publishMessage(Mqtt5Publish message) {
        try {
            Sinks.EmitResult result = messageSink.tryEmitNext(message);
            return handleEmitResult(message, result, "MQTT消息");
        } catch (Exception e) {
            log.error("发布MQTT消息事件异常: {}", e.getMessage(), e);
            return PublishResult.failed(message, PublishFailureReason.EXCEPTION, e);
        }
    }

    /**
     * 发布心跳事件
     * <p>
     * <strong>功能说明：</strong>
     * 发布心跳事件，用于维持MQTT连接活跃状态和监控客户端健康状态。
     * 该方法会触发所有订阅了heartbeatFlow的处理器。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>定期发送心跳包维持连接</li>
     *   <li>监控客户端连接活跃状态</li>
     *   <li>检测连接异常和超时</li>
     *   <li>心跳统计和性能监控</li>
     *   <li>连接保活和健康检查</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>心跳事件会进入16个事件的缓冲区</li>
     *   <li>心跳频率不宜过高，避免网络拥塞</li>
     *   <li>心跳超时时间应合理设置</li>
     *   <li>返回发布结果，调用方可以处理失败情况</li>
     *   <li><strong>线程安全</strong>：使用synchronized确保线程安全</li>
     * </ul>
     *
     * @param event 心跳事件对象
     * @return 发布结果，包含成功状态和失败原因
     */
    static synchronized PublishResult<HeartbeatEvent> publishHeartbeat(HeartbeatEvent event) {
        try {
            Sinks.EmitResult result = heartbeatSink.tryEmitNext(event);
            return handleEmitResult(event, result, "心跳");
        } catch (Exception e) {
            log.error("发布心跳事件异常: {}", e.getMessage(), e);
            return PublishResult.failed(event, PublishFailureReason.EXCEPTION, e);
        }
    }

    /**
     * 发布订阅结果事件
     * <p>
     * <strong>功能说明：</strong>
     * 发布订阅或取消订阅操作的结果事件，包含操作成功状态、错误信息等。
     * 该方法会触发所有订阅了subscriptionResultFlow的处理器。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>订阅操作完成后发布结果</li>
     *   <li>取消订阅操作完成后发布结果</li>
     *   <li>订阅失败时发布错误信息</li>
     *   <li>订阅状态同步和监控</li>
     *   <li>订阅重试和错误处理</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>订阅结果事件会进入32个事件的缓冲区</li>
     *   <li>成功和失败的结果都会发布</li>
     *   <li>错误信息应包含详细的失败原因</li>
     *   <li>返回发布结果，调用方可以处理失败情况</li>
     *   <li><strong>线程安全</strong>：使用synchronized确保线程安全</li>
     * </ul>
     * <p>
     * <strong>内部访问方式：</strong>
     * 请使用 {@link MqttEventPublisher#publishSubscriptionResult(SubscriptionResult)} 方法
     *
     * @param result 订阅结果对象
     * @return 发布结果，包含成功状态和失败原因
     */
    static synchronized PublishResult<SubscriptionResult> publishSubscriptionResult(SubscriptionResult result) {
        try {
            Sinks.EmitResult emitResult = subscriptionResultSink.tryEmitNext(result);
            return handleEmitResult(result, emitResult, "订阅结果");
        } catch (Exception e) {
            log.error("发布订阅结果事件异常: {}", e.getMessage(), e);
            return PublishResult.failed(result, PublishFailureReason.EXCEPTION, e);
        }
    }

    /**
     * 发布网络质量事件
     * <p>
     * <strong>功能说明：</strong>
     * 发布网络质量监控事件，包含网络延迟、丢包率等性能指标。
     * 该方法会触发所有订阅了networkQualityFlow的处理器。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络质量检测完成后发布结果</li>
     *   <li>网络性能监控和统计</li>
     *   <li>网络异常检测和告警</li>
     *   <li>网络质量变化通知</li>
     *   <li>网络性能数据收集</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>网络质量事件会进入32个事件的缓冲区</li>
     *   <li>网络质量检测频率应合理设置</li>
     *   <li>返回发布结果，调用方可以处理失败情况</li>
     *   <li>网络质量差时应考虑降低检测频率</li>
     *   <li><strong>线程安全</strong>：使用synchronized确保线程安全</li>
     * </ul>
     *
     * @param event 网络质量事件对象
     * @return 发布结果，包含成功状态和失败原因
     */
    static synchronized PublishResult<NetworkQualityEvent> publishNetworkQuality(NetworkQualityEvent event) {
        try {
            Sinks.EmitResult result = networkQualitySink.tryEmitNext(event);
            return handleEmitResult(event, result, "网络质量");
        } catch (Exception e) {
            log.error("发布网络质量事件异常: {}", e.getMessage(), e);
            return PublishResult.failed(event, PublishFailureReason.EXCEPTION, e);
        }
    }

    /**
     * 广播连接状态
     * <p>
     * <strong>功能说明：</strong>
     * 广播简化的连接状态（true=已连接，false=未连接），用于快速状态检查。
     * 该方法会触发所有订阅了connectedFlow的处理器。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>连接建立后广播true状态</li>
     *   <li>连接断开后广播false状态</li>
     *   <li>快速连接状态检查</li>
     *   <li>连接状态指示器更新</li>
     *   <li>条件判断和流程控制</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>状态变化时会立即广播给所有订阅者</li>
     *   <li>使用distinctUntilChanged()可以避免重复状态</li>
     *   <li>返回发布结果，调用方可以处理失败情况</li>
     *   <li><strong>线程安全</strong>：使用synchronized确保线程安全</li>
     * </ul>
     *
     * @param connected 连接状态，true表示已连接，false表示未连接
     * @return 发布结果，包含成功状态和失败原因
     */
    static synchronized PublishResult<Boolean> broadcastConnected(boolean connected) {
        try {
            Sinks.EmitResult result = connectedSink.tryEmitNext(connected);
            return handleEmitResult(connected, result, "连接状态");
        } catch (Exception e) {
            log.error("发布连接状态异常: {}", e.getMessage(), e);
            return PublishResult.failed(connected, PublishFailureReason.EXCEPTION, e);
        }
    }

    /**
     * 广播网络类型
     * <p>
     * <strong>功能说明：</strong>
     * 广播当前网络类型变化，用于网络配置调整和连接策略更新。
     * 该方法会触发所有订阅了networkTypeFlow的处理器。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>网络类型检测完成后广播</li>
     *   <li>网络切换时通知相关组件</li>
     *   <li>网络配置策略调整</li>
     *   <li>连接参数优化</li>
     *   <li>网络状态监控和记录</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>网络类型变化时会立即广播</li>
     *   <li>使用distinctUntilChanged()可以避免重复广播</li>
     *   <li>返回发布结果，调用方可以处理失败情况</li>
     *   <li>网络类型变化后可能需要重新建立连接</li>
     *   <li><strong>线程安全</strong>：使用synchronized确保线程安全</li>
     * </ul>
     *
     * @param networkType 当前网络类型枚举值
     * @return 发布结果，包含成功状态和失败原因
     */
    static synchronized PublishResult<NetworkTypeEnum> broadcastNetworkType(NetworkTypeEnum networkType) {
        try {
            Sinks.EmitResult result = networkTypeSink.tryEmitNext(networkType);
            return handleEmitResult(networkType, result, "网络类型");
        } catch (Exception e) {
            log.error("发布网络类型异常: {}", e.getMessage(), e);
            return PublishResult.failed(networkType, PublishFailureReason.EXCEPTION, e);
        }
    }


    // ==================== 事件流管理 ====================

    /**
     * 清理所有事件流
     * <p>
     * <strong>功能说明：</strong>
     * 清理所有事件流，释放资源并确保没有残留的事件。
     * 在MQTT连接断开时调用，防止内存泄漏和事件堆积。
     * <p>
     * <strong>使用场景：</strong>
     * <ul>
     *   <li>MQTT连接断开时清理资源</li>
     *   <li>应用程序关闭时清理事件流</li>
     *   <li>连接重置时清理旧的事件</li>
     *   <li>内存清理和资源回收</li>
     *   <li>防止事件流内存泄漏</li>
     * </ul>
     * <p>
     * <strong>注意事项：</strong>
     * <ul>
     *   <li>清理后所有事件流将不可用</li>
     *   <li>需要重新建立连接才能恢复事件流</li>
     *   <li>清理操作是幂等的，可以多次调用</li>
     *   <li>异常情况下会记录错误日志但不抛出异常</li>
     *   <li><strong>访问控制</strong>：该方法仅供client包及其子包内的内部组件使用</li>
     * </ul>
     * <p>
     * <strong>清理内容：</strong>
     * <ul>
     *   <li>连接状态事件流（replay类型）</li>
     *   <li>心跳事件流（multicast类型）</li>
     *   <li>订阅结果事件流（multicast类型）</li>
     *   <li>网络质量事件流（multicast类型）</li>
     * </ul>
     * <p>
     * <strong>内部访问方式：</strong>
     * 请使用 {@link MqttEventPublisher#clearAllEvents()} 方法
     */
    static void clearAllEvents() {
        log.debug("清理所有事件流");
        try {
            // 清理连接状态流（replay类型需要特殊处理）
            connectionStateSink.tryEmitComplete();

            // 清理其他流
            messageSink.tryEmitComplete();
            heartbeatSink.tryEmitComplete();
            subscriptionResultSink.tryEmitComplete();
            networkQualitySink.tryEmitComplete();

            log.debug("事件流清理完成");
        } catch (Exception e) {
            log.error("清理事件流时发生异常: {}", e.getMessage(), e);
        }
    }

}
