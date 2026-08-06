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
package cn.richie696.component.nats.bus;

import cn.richie696.component.nats.config.NatsProperties;
import cn.richie696.component.nats.connection.NatsConnectionManager;
import cn.richie696.component.nats.exception.NatsException;
import cn.richie696.component.nats.exception.NatsRpcException;
import cn.richie696.component.nats.pipeline.NatsMessageHandler;
import cn.richie696.component.nats.pipeline.NatsSubscriberFactory;
import cn.richie696.component.nats.strategy.NatsErrorStrategy;
import cn.richie696.component.nats.strategy.NatsHeaderInjector;
import cn.richie696.component.nats.strategy.NatsMessageSerializer;
import cn.richie696.component.nats.strategy.NatsTracingSupport;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Core NATS 门面
 *
 * <p>所有操作基于 Core NATS 协议：fire-and-forget，无持久化，无 ACK。
 * 每次操作自动完成序列化、上下文注入、链路追踪等横切关注点。</p>
 *
 * <p><b>线程安全</b>：实例由 Spring 单例持有；{@link #subscriptionDispatchers}
 * 使用 {@link ConcurrentHashMap} 保证并发安全，
 * 业务 publish/request 可被任意线程并发调用。</p>
 *
 * <p><b>生命周期</b>：调用方对返回的 {@link Subscription} 负责，
 * 应在不再需要时调用 {@link #unsubscribe(Subscription)} 以释放 dispatcher 线程。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class NatsBus {

    /** NATS 连接持有者。 */
    private final NatsConnectionManager connectionManager;
    /** 消息体序列化器。 */
    private final NatsMessageSerializer serializer;
    /** 出站 Header 注入器（追踪、上下文等）。 */
    private final NatsHeaderInjector headerInjector;
    /** 链路追踪门面。 */
    private final NatsTracingSupport tracingSupport;
    /** 订阅管道工厂，用于在 Handler 前织入追踪/上下文/幂等装饰器。 */
    private final NatsSubscriberFactory subscriberFactory;
    /** 错误处理策略（发布失败、消费失败）。 */
    private final NatsErrorStrategy errorStrategy;
    /** 外部配置，提供 RPC 默认超时。 */
    private final NatsProperties properties;
    /**
     * Subscription → Dispatcher 映射表。
     * <p>每个订阅独占 dispatcher 以保证回调线程隔离；
     * 通过此映射在 {@link #unsubscribe(Subscription)} 时释放 dispatcher，
     * 避免 NATS 客户端为该订阅保留的工作线程泄漏。</p>
     */
    private final ConcurrentMap<Subscription, Dispatcher> subscriptionDispatchers = new ConcurrentHashMap<>();

    /**
     * 构造 Core NATS 门面，所有依赖由 Spring 注入。
     *
     * @param connectionManager NATS 连接持有者
     * @param serializer        消息体序列化器
     * @param headerInjector    出站 Header 注入器
     * @param tracingSupport    链路追踪门面
     * @param subscriberFactory 订阅管道工厂
     * @param errorStrategy     错误处理策略
     * @param properties        外部配置
     */
    public NatsBus(NatsConnectionManager connectionManager,
                   NatsMessageSerializer serializer,
                   NatsHeaderInjector headerInjector,
                   NatsTracingSupport tracingSupport,
                   NatsSubscriberFactory subscriberFactory,
                   NatsErrorStrategy errorStrategy,
                   NatsProperties properties) {
        this.connectionManager = connectionManager;
        this.serializer = serializer;
        this.headerInjector = headerInjector;
        this.tracingSupport = tracingSupport;
        this.subscriberFactory = subscriberFactory;
        this.errorStrategy = errorStrategy;
        this.properties = properties;
    }

    // ===== 发布（fire-and-forget）=====

    /**
     * 发布消息到指定 subject（fire-and-forget）。
     * <p>消息体走序列化器，Header 中会注入当前上下文与随机 {@code nats-message-id}，
     * 同时启动 OpenTelemetry Producer Span 并把上下文设到当前线程，
     * 以保证整个发布链路可被追踪。</p>
     *
     * @param subject NATS subject
     * @param message 消息对象（自动序列化）
     * @throws NatsException 当底层连接或序列化失败时抛出（span 已标记为失败）
     */
    public void publish(String subject, Object message) {
        byte[] data = serializer.serialize(message);
        Headers headers = new Headers();
        headerInjector.inject(headers);
        headers.put("nats-message-id", UUID.randomUUID().toString());

        Span span = tracingSupport.startProducerSpan(subject, headers);
        try (Scope ignored = io.opentelemetry.context.Context.current().with(span).makeCurrent()) {
            connectionManager.getConnection().publish(subject, headers, data);
            tracingSupport.finishSpan(span, true, null);
        } catch (Exception e) {
            tracingSupport.finishSpan(span, false, e.getMessage());
            errorStrategy.onPublishError(subject, data, e);
            throw new NatsException("Failed to publish message to subject: " + subject, e);
        }
    }

    // ===== 订阅 =====

    /**
     * 订阅指定 subject 的消息（不带 queue group）。
     * <p>等同 {@link #subscribe(String, String, NatsMessageHandler)} 的 {@code queueGroup=null} 重载。</p>
     *
     * @param subject NATS subject（支持通配符）
     * @param handler 业务处理 Handler
     * @return Subscription 对象（可用于 {@link #unsubscribe(Subscription)}）
     */
    public Subscription subscribe(String subject, NatsMessageHandler handler) {
        return subscribe(subject, null, handler);
    }

    /**
     * 订阅指定 subject 的消息（Queue Group 负载均衡）。
     * <p>当 {@code queueGroup} 非空且非空字符串时，订阅加入同一个 queue group，
     * 同一组内的多实例只会被分派一次，实现服务端水平扩展与负载均衡。
     * 不管是否使用 queue group，每一个订阅都独占一个 dispatcher 以提供专用工作线程。</p>
     *
     * @param subject    NATS subject（支持通配符）
     * @param queueGroup Queue Group 名称；为 {@code null} 或空白时退化为普通订阅
     * @param handler    业务处理 Handler
     * @return Subscription 对象（持有独立 dispatcher，通过 {@link #unsubscribe(Subscription)} 释放）
     */
    public Subscription subscribe(String subject, String queueGroup, NatsMessageHandler handler) {
        Connection conn = connectionManager.getConnection();
        NatsMessageHandler pipelinedHandler = subscriberFactory.buildAsyncPipeline(handler);

        Dispatcher dispatcher = conn.createDispatcher();

        Subscription subscription;
        if (queueGroup != null && !queueGroup.isBlank()) {
            subscription = dispatcher.subscribe(subject, queueGroup, msg -> {
                try {
                    pipelinedHandler.handle(msg);
                } catch (Exception e) {
                    errorStrategy.onConsumeError(msg.getSubject(), msg, e);
                }
            });
        } else {
            subscription = dispatcher.subscribe(subject, msg -> {
                try {
                    pipelinedHandler.handle(msg);
                } catch (Exception e) {
                    errorStrategy.onConsumeError(msg.getSubject(), msg, e);
                }
            });
        }

        log.info("NATS subscribed to [{}]{}", subject,
                queueGroup != null ? " (queue: " + queueGroup + ")" : "");
        subscriptionDispatchers.put(subscription, dispatcher);
        return subscription;
    }

    /**
     * 取消订阅并释放该订阅专属 dispatcher 的工作线程。
     * <p>优先关闭 dispatcher（同时取消所有订阅），若映射缺失则降级为仅取消订阅；
     * 调用此方法替代直接调用 {@link Subscription#unsubscribe()}，避免 dispatcher 线程泄漏。</p>
     *
     * @param subscription 由 {@link #subscribe(String, NatsMessageHandler)} 等方法返回的句柄
     */
    public void unsubscribe(Subscription subscription) {
        Dispatcher dispatcher = subscriptionDispatchers.remove(subscription);
        if (dispatcher != null) {
            connectionManager.getConnection().closeDispatcher(dispatcher);
        } else {
            subscription.unsubscribe();
        }
    }

    // ===== RPC 同步请求-响应 =====

    /**
     * 发送 RPC 请求并同步等待响应。
     * <p>底层走 {@link #requestAsync} 异步通道，避免阻塞 NATS 回调线程；
     * 在同步阻塞处统一处理中断、{@link NatsRpcException} 透传与其他异常包装。</p>
     *
     * @param subject      NATS subject
     * @param request      请求对象
     * @param responseType 响应类型
     * @param timeout      超时时间
     * @param <T>          请求类型
     * @param <R>          响应类型
     * @return 响应对象
     * @throws NatsRpcException 超时、无应答或服务端错误时抛出
     * @throws NatsException    其他底层异常
     */
    public <T, R> R request(String subject, T request, Class<R> responseType, Duration timeout) {
        try {
            return requestAsync(subject, request, responseType, timeout).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NatsRpcException rpcEx) {
                throw rpcEx;
            }
            throw new NatsException("RPC request failed for subject: " + subject, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NatsRpcException.timeout(subject, e);
        }
    }

    /**
     * 发送 RPC 请求并使用 {@code request.default-timeout} 等待响应。
     *
     * @param subject      NATS subject
     * @param request      请求对象
     * @param responseType 响应类型
     * @param <T>          请求类型
     * @param <R>          响应类型
     * @return 响应对象
     */
    public <T, R> R request(String subject, T request, Class<R> responseType) {
        return request(subject, request, responseType, properties.getRequest().getDefaultTimeout());
    }

    // ===== RPC 异步请求-响应 =====

    /**
     * 发送 RPC 请求并异步等待响应。
     * <p>内部按响应结果分支：成功时反序列化 payload 并完成 span；
     * 失败时通过 {@link #unwrap(Throwable)} 拆出根因并按超时/无应答/其他分类抛出
     * {@link NatsRpcException}，调用方能精准识别错误类型。</p>
     *
     * @param subject      NATS subject
     * @param request      请求对象
     * @param responseType 响应类型
     * @param timeout      超时时间
     * @param <T>          请求类型
     * @param <R>          响应类型
     * @return CompletableFuture，链式失败时已包装为对应的 {@link NatsRpcException}
     */
    public <T, R> CompletableFuture<R> requestAsync(String subject, T request,
                                                    Class<R> responseType, Duration timeout) {
        byte[] data = serializer.serialize(request);
        Headers headers = new Headers();
        headerInjector.inject(headers);

        Span span = tracingSupport.startClientSpan(subject, headers);
        Connection conn = connectionManager.getConnection();

        return conn.requestWithTimeout(subject, headers, data, timeout)
                .thenApply(msg -> {
                    tracingSupport.finishSpan(span, true, null);
                    return serializer.deserialize(msg.getData(), responseType);
                })
                .exceptionally(e -> {
                    Throwable cause = unwrap(e);
                    tracingSupport.finishSpan(span, false, cause.getMessage());

                    if (cause instanceof TimeoutException
                            || cause instanceof CancellationException) {
                        throw NatsRpcException.timeout(subject, cause);
                    }
                    // NoResponders 是 NATS 协议级事件，SDK 没有公开异常类型，故按类名特征判断
                    if (cause.getClass().getSimpleName().contains("NoResponders")) {
                        throw NatsRpcException.noResponders(subject, cause);
                    }
                    throw NatsRpcException.other(subject, cause);
                });
    }

    /**
     * 异步发送 RPC 请求并使用 {@code request.default-timeout}。
     *
     * @param subject      NATS subject
     * @param request      请求对象
     * @param responseType 响应类型
     * @param <T>          请求类型
     * @param <R>          响应类型
     * @return CompletableFuture 响应
     */
    public <T, R> CompletableFuture<R> requestAsync(String subject, T request, Class<R> responseType) {
        return requestAsync(subject, request, responseType, properties.getRequest().getDefaultTimeout());
    }

    /**
     * 沿 {@link CompletionException} 链路拆包直到取到真正的根因。
     * <p>{@code CompletableFuture.exceptionally} 回调里通常会包裹一层 {@code CompletionException}，
     * 不拆包会丢给客户端糟糕的栈帧且难以匹配异常类型，这里循环剥离直到碰到真实业务异常或不再有 cause。</p>
     *
     * @param e 起始异常
     * @return 拆包后的根因异常（可能等于入参本身）
     */
    private Throwable unwrap(Throwable e) {
        while (e instanceof CompletionException && e.getCause() != null) {
            e = e.getCause();
        }
        return e;
    }
}
