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
package com.richie.component.nats.bus;

import com.richie.component.nats.config.NatsProperties;
import com.richie.component.nats.connection.NatsConnectionManager;
import com.richie.component.nats.exception.NatsException;
import com.richie.component.nats.exception.NatsRpcException;
import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.pipeline.NatsSubscriberFactory;
import com.richie.component.nats.strategy.NatsErrorStrategy;
import com.richie.component.nats.strategy.NatsHeaderInjector;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import com.richie.component.nats.strategy.NatsTracingSupport;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core NATS 门面
 *
 * <p>所有操作基于 Core NATS 协议：fire-and-forget，无持久化，无 ACK。
 * 每次操作自动完成序列化、上下文注入、链路追踪等横切关注点。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class NatsBus {

    private final NatsConnectionManager connectionManager;
    private final NatsMessageSerializer serializer;
    private final NatsHeaderInjector headerInjector;
    private final NatsTracingSupport tracingSupport;
    private final NatsSubscriberFactory subscriberFactory;
    private final NatsErrorStrategy errorStrategy;
    private final NatsProperties properties;

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
     * 发布消息到指定 subject
     *
     * @param subject NATS subject
     * @param message 消息对象（自动序列化）
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
     * 订阅指定 subject 的消息
     *
     * @param subject NATS subject（支持通配符）
     * @param handler 业务处理 Handler
     * @return Subscription 对象（可用于 unsubscribe）
     */
    public Subscription subscribe(String subject, NatsMessageHandler handler) {
        return subscribe(subject, null, handler);
    }

    /**
     * 订阅指定 subject 的消息（Queue Group 负载均衡）
     *
     * @param subject    NATS subject（支持通配符）
     * @param queueGroup Queue Group 名称
     * @param handler    业务处理 Handler
     * @return Subscription 对象
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
        return subscription;
    }

    // ===== RPC 同步请求-响应 =====

    /**
     * 发送 RPC 请求并同步等待响应
     *
     * @param subject      NATS subject
     * @param request      请求对象
     * @param responseType 响应类型
     * @param timeout      超时时间
     * @param <T>          请求类型
     * @param <R>          响应类型
     * @return 响应对象
     */
    public <T, R> R request(String subject, T request, Class<R> responseType, Duration timeout) {
        try {
            return requestAsync(subject, request, responseType, timeout).get();
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof NatsRpcException rpcEx) {
                throw rpcEx;
            }
            throw new NatsException("RPC request failed for subject: " + subject, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NatsRpcException.timeout(subject, e);
        }
    }

    // ===== RPC 异步请求-响应 =====

    /**
     * 发送 RPC 请求并异步等待响应
     *
     * @param subject      NATS subject
     * @param request      请求对象
     * @param responseType 响应类型
     * @param timeout      超时时间
     * @param <T>          请求类型
     * @param <R>          响应类型
     * @return CompletableFuture 响应
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

                    if (cause instanceof java.util.concurrent.TimeoutException
                            || cause instanceof java.util.concurrent.CancellationException) {
                        throw NatsRpcException.timeout(subject, cause);
                    }
                    if (cause.getClass().getSimpleName().contains("NoResponders")) {
                        throw NatsRpcException.noResponders(subject, cause);
                    }
                    throw NatsRpcException.other(subject, cause);
                });
    }

    private Throwable unwrap(Throwable e) {
        while (e instanceof java.util.concurrent.CompletionException && e.getCause() != null) {
            e = e.getCause();
        }
        return e;
    }
}
