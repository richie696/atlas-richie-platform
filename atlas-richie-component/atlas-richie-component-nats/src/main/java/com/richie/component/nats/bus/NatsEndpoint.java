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
package com.richie.component.nats.bus;

import com.richie.component.nats.connection.NatsConnectionManager;
import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.pipeline.NatsSubscriberFactory;
import com.richie.component.nats.strategy.NatsErrorStrategy;
import com.richie.component.nats.strategy.NatsHeaderInjector;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.function.Function;

/**
 * NATS RPC 端点注册
 *
 * <p>基于 Core NATS Request-Reply 模式的服务端 Handler 注册。
 * 接收请求 → 反序列化 → 执行 Handler → 序列化响应 → 发布到 replyTo。</p>
 *
 * <p>所有横切关注点（追踪/上下文/错误处理）通过管道自动处理。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class NatsEndpoint {

    private final NatsConnectionManager connectionManager;
    private final NatsMessageSerializer serializer;
    private final NatsHeaderInjector headerInjector;
    private final NatsSubscriberFactory subscriberFactory;
    private final NatsErrorStrategy errorStrategy;

    public NatsEndpoint(NatsConnectionManager connectionManager,
                        NatsMessageSerializer serializer,
                        NatsHeaderInjector headerInjector,
                        NatsSubscriberFactory subscriberFactory,
                        NatsErrorStrategy errorStrategy) {
        this.connectionManager = connectionManager;
        this.serializer = serializer;
        this.headerInjector = headerInjector;
        this.subscriberFactory = subscriberFactory;
        this.errorStrategy = errorStrategy;
    }

    /**
     * 注册 RPC Handler
     *
     * @param subject     NATS subject（支持通配符）
     * @param requestType 请求类型
     * @param handler     业务处理函数（接收请求，返回响应）
     * @param <T>         请求泛型
     * @param <R>         响应泛型
     */
    public <T, R> void registerHandler(String subject, Class<T> requestType,
                                        Function<T, R> handler) {
        registerHandler(subject, null, requestType, handler);
    }

    /**
     * 注册 RPC Handler（Queue Group 负载均衡）
     *
     * @param subject     NATS subject（支持通配符）
     * @param queueGroup  Queue Group 名称（多实例负载均衡）
     * @param requestType 请求类型
     * @param handler     业务处理函数
     * @param <T>         请求泛型
     * @param <R>         响应泛型
     */
    public <T, R> void registerHandler(String subject, String queueGroup,
                                        Class<T> requestType, Function<T, R> handler) {
        Connection conn = connectionManager.getConnection();

        // 构建 RPC 管道 Handler：反序列化 → 执行 → 序列化 → 回复
        NatsMessageHandler rawHandler = msg -> {
            String replyTo = msg.getReplyTo();
            if (replyTo == null || replyTo.isBlank()) {
                log.warn("NATS RPC: no replyTo for subject [{}], skipping", msg.getSubject());
                return;
            }

            try {
                T request = serializer.deserialize(msg.getData(), requestType);
                R response = handler.apply(request);

                // 序列化响应 + 注入追踪 header
                byte[] responseData = serializer.serialize(response);
                Headers responseHeaders = new Headers();
                headerInjector.inject(responseHeaders);
                responseHeaders.put("nats-message-id", UUID.randomUUID().toString());

                conn.publish(replyTo, responseHeaders, responseData);
            } catch (Exception e) {
                // 发送错误响应
                byte[] errorResponse = serializer.serialize(new ErrorResponse(e.getMessage()));
                Headers errorHeaders = new Headers();
                errorHeaders.put("nats-error", "true");
                conn.publish(replyTo, errorHeaders, errorResponse);
                throw e;
            }
        };

        NatsMessageHandler pipelinedHandler = subscriberFactory.buildRpcPipeline(rawHandler);

        Dispatcher dispatcher = conn.createDispatcher(msg -> {
            try {
                pipelinedHandler.handle(msg);
            } catch (Exception e) {
                errorStrategy.onConsumeError(msg.getSubject(), msg, e);
            }
        });

        if (queueGroup != null && !queueGroup.isBlank()) {
            dispatcher.subscribe(subject, queueGroup);
        } else {
            dispatcher.subscribe(subject);
        }

        log.info("NATS RPC endpoint registered: [{}]{}", subject,
                queueGroup != null ? " (queue: " + queueGroup + ")" : "");
    }

    /**
     * RPC 错误响应 DTO
     */
    public record ErrorResponse(String error) {
    }
}
