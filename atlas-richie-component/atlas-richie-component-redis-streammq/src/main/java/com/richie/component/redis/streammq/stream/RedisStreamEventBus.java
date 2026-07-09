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
package com.richie.component.redis.streammq.stream;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * Redis Stream 事件总线
 *
 * <p>基于 Reactor 响应式编程模型的 Redis Stream 消息事件总线，提供高性能的异步消息分发能力。
 * 采用与 MqttEventBus 一致的设计风格，确保系统内事件处理的统一性和一致性。
 *
 * <p><strong>设计特点：</strong>
 * <ul>
 *   <li><strong>单例模式</strong>：通过工具类设计，确保全局唯一的事件总线实例</li>
 *   <li><strong>响应式设计</strong>：基于 Reactor 的 Flux 和 Sinks，支持背压和异步处理</li>
 *   <li><strong>包内可见</strong>：发布方法仅对包内可见，保证事件发布的安全性</li>
 *   <li><strong>弹性调度</strong>：使用 boundedElastic 调度器，适合 I/O 密集型操作</li>
 *   <li><strong>背压控制</strong>：内置 1000 容量的缓冲区，防止内存溢出</li>
 * </ul>
 *
 * <p><strong>核心功能：</strong>
 * <ul>
 *   <li><strong>消息分发</strong>：将 Redis Stream 消息事件分发给多个订阅者</li>
 *   <li><strong>异步处理</strong>：支持非阻塞的消息处理模式</li>
 *   <li><strong>多播支持</strong>：一个消息可以被多个消费者同时处理</li>
 *   <li><strong>错误隔离</strong>：单个订阅者的异常不影响其他订阅者</li>
 *   <li><strong>流量控制</strong>：内置背压机制，防止生产者过快导致的内存问题</li>
 * </ul>
 *
 * <p><strong>使用场景：</strong>
 * <ul>
 *   <li><strong>消息路由</strong>：将来自不同 Redis Stream 的消息统一路由</li>
 *   <li><strong>事件驱动</strong>：支持基于事件的异步架构设计</li>
 *   <li><strong>系统集成</strong>：连接 Redis Stream 与应用内的消息处理逻辑</li>
 *   <li><strong>监控审计</strong>：对所有 Stream 消息进行统一监控和审计</li>
 * </ul>
 *
 * <p><strong>订阅示例：</strong>
 * <pre>{@code
 * // 订阅所有 Redis Stream 消消息事件
 * RedisStreamEventBus.messageFlow
 *     .filter(event -> "order-events".equals(event.streamKey()))
 *     .map(event -> (OrderEvent) event.payload())
 *     .subscribe(orderEvent -> {
 *         log.info("处理订单事件: {}", orderEvent.getOrderId());
 *         // 处理订单业务逻辑
 *     });
 *
 * // 监控特定消费者组的消息
 * RedisStreamEventBus.messageFlow
 *     .filter(event -> "payment-processors".equals(event.group()))
 *     .subscribe(event -> {
 *         log.info("支付处理组收到消息: streamKey={}, recordId={}",
 *                 event.streamKey(), event.recordId());
 *     });
 *
 * // 错误处理和重试机制
 * RedisStreamEventBus.messageFlow
 *     .onErrorContinue((error, event) -> {
 *         log.error("处理消息事件异常: {}", error.getMessage(), error);
 *     })
 *     .retry(3)
 *     .subscribe(event -> processEvent(event));
 * }</pre>
 *
 * <p><strong>架构说明：</strong>
 * <ul>
 *   <li><strong>生产端</strong>：Redis Stream 拉取器通过 publishMessage() 发布事件</li>
 *   <li><strong>传输层</strong>：Sinks.Many 提供多播能力和背压控制</li>
 *   <li><strong>消费端</strong>：应用程序通过 messageFlow 订阅和处理事件</li>
 *   <li><strong>调度层</strong>：boundedElastic 调度器确保非阻塞处理</li>
 * </ul>
 *
 * <p><strong>性能特性：</strong>
 * <ul>
 *   <li><strong>高吞吐</strong>：基于 Reactor 的异步处理，支持高并发场景</li>
 *   <li><strong>低延迟</strong>：事件发布后立即分发给订阅者</li>
 *   <li><strong>内存安全</strong>：1000 容量缓冲区 + 背压控制，防止 OOM</li>
 *   <li><strong>线程安全</strong>：publishMessage() 方法使用 synchronized 确保线程安全</li>
 * </ul>
 *
 * @author richie696
 * @since 2025-09-15
 * @see StreamMessageEvent Redis Stream 消息事件模型
 * @see PublishResult 事件发布结果封装
 * @see AbstractStreamConsumer Stream 消费者基类
 */
@Slf4j
public final class RedisStreamEventBus {

    /**
     * 私有构造函数，防止外部实例化
     *
     * @throws AssertionError 如果尝试实例化该类
     */
    private RedisStreamEventBus() {
        throw new AssertionError("No instances.");
    }

    /**
     * Redis Stream 消息事件的多播发布器
     *
     * <p>使用 Sinks.Many 实现一对多的消息分发能力，支持：
     * <ul>
     *   <li><strong>多播模式</strong>：一个事件可被多个订阅者接收</li>
     *   <li><strong>背压缓冲</strong>：1000 容量的环形缓冲区</li>
     *   <li><strong>非覆盖模式</strong>：缓冲区满时不覆盖旧数据，而是应用背压</li>
     * </ul>
     */
    private static final Sinks.Many<StreamMessageEvent<?>> messageSink =
            Sinks.many().multicast().onBackpressureBuffer(1000, false);

    /**
     * Redis Stream 消息事件流
     *
     * <p>对外暴露的消息订阅接口，所有 Redis Stream 消息事件都通过此流分发。
     * 使用 boundedElastic 调度器确保订阅者的处理逻辑在独立线程池中执行，
     * 避免阻塞事件总线的主处理线程。
     *
     * <p><strong>流特性：</strong>
     * <ul>
     *   <li><strong>冷流转热流</strong>：通过 multicast 将冷流转换为热流</li>
     *   <li><strong>弹性调度</strong>：使用 boundedElastic 线程池，适合 I/O 操作</li>
     *   <li><strong>背压支持</strong>：当消费者处理不及时会自动应用背压</li>
     *   <li><strong>错误隔离</strong>：单个订阅者异常不会影响其他订阅者</li>
     * </ul>
     *
     * <p><strong>订阅建议：</strong>
     * <ul>
     *   <li>使用 filter() 过滤感兴趣的 streamKey 或 group</li>
     *   <li>添加 onErrorContinue() 处理订阅异常</li>
     *   <li>考虑使用 buffer() 或 window() 进行批量处理</li>
     *   <li>长时间运行的处理逻辑应使用 subscribeOn() 切换线程</li>
     * </ul>
     */
    public static final Flux<StreamMessageEvent<?>> messageFlow =
            messageSink.asFlux().publishOn(Schedulers.boundedElastic());

    /**
     * 发布 Redis Stream 消息事件（包内可见）
     *
     * <p>此方法仅供包内的 Redis Stream 拉取器调用，用于将从 Redis Stream
     * 拉取的消息转换为事件并发布到事件总线。外部代码无法直接调用此方法，
     * 确保了事件发布的安全性和一致性。
     *
     * <p><strong>发布流程：</strong>
     * <ol>
     *   <li>尝试将事件推送到多播发布器</li>
     *   <li>根据推送结果创建相应的 PublishResult</li>
     *   <li>异常情况下记录错误日志并返回失败结果</li>
     *   <li>返回发布结果供调用方处理</li>
     * </ol>
     *
     * <p><strong>背压处理：</strong>
     * <ul>
     *   <li><strong>缓冲区满</strong>：返回 BUFFER_OVERFLOW 失败结果</li>
     *   <li><strong>发布器终止</strong>：返回 SINK_TERMINATED 失败结果</li>
     *   <li><strong>操作取消</strong>：返回 OPERATION_CANCELLED 失败结果</li>
     *   <li><strong>并发冲突</strong>：返回 NON_SERIALIZED_ACCESS 失败结果</li>
     * </ul>
     *
     * <p><strong>线程安全：</strong>
     * <p>使用 synchronized 关键字确保多线程环境下的线程安全，
     * 避免并发发布时的数据竞争和状态不一致问题。
     *
     * @param <T> 消息载荷的类型参数
     * @param event 要发布的 Redis Stream 消息事件，包含流键、消费者组、记录ID和载荷
     * @return PublishResult 发布结果，包含成功/失败状态和详细信息
     *
     * @throws RuntimeException 当发布过程中发生意外异常时抛出
     *
     * @see StreamMessageEvent Redis Stream 消息事件结构
     * @see PublishResult 发布结果封装类
     * @see Sinks.EmitResult Reactor 发布器的发布结果枚举
     */
    static synchronized <T> PublishResult<StreamMessageEvent<T>> publishMessage(StreamMessageEvent<T> event) {
        try {
            // ��试向多播发布器推送事件，使用 tryEmitNext 避免阻塞
            Sinks.EmitResult result = messageSink.tryEmitNext(event);
            // 将 Reactor 的发布结果转换为业务层的发布结果
            return PublishResult.fromEmit(result, event, "StreamMessage");
        } catch (Exception e) {
            // 记录发布异常的详细信息，便于问题排查
            log.error("发布Stream消息事件异常: {}", e.getMessage(), e);
            // 返回异常类型的失败结果
            return PublishResult.failed(event, PublishFailureReason.EXCEPTION, e);
        }
    }
}
