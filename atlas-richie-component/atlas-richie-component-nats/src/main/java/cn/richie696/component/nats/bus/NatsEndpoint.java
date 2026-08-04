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

import cn.richie696.component.nats.connection.NatsConnectionManager;
import cn.richie696.component.nats.pipeline.NatsMessageHandler;
import cn.richie696.component.nats.pipeline.NatsSubscriberFactory;
import cn.richie696.component.nats.strategy.NatsErrorStrategy;
import cn.richie696.component.nats.strategy.NatsHeaderInjector;
import cn.richie696.component.nats.strategy.NatsMessageSerializer;
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
 * <p><b>线程安全</b>：实例由 Spring 单例持有；业务 Handler 必须自行保证线程安全，
 * 因为同一个 endpoint 可能被多个分发线程并发调用。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class NatsEndpoint {

    /** NATS 连接持有者，用于创建 dispatcher 与发布响应。 */
    private final NatsConnectionManager connectionManager;
    /** 请求/响应序列化器。 */
    private final NatsMessageSerializer serializer;
    /** 响应 Header 注入器，负责把追踪上下文透传给调用方。 */
    private final NatsHeaderInjector headerInjector;
    /** 订阅管道工厂，用于在 Handler 前织入追踪/上下文/幂等装饰器。 */
    private final NatsSubscriberFactory subscriberFactory;
    /** 消费错误处理策略，抛出未捕获异常时回调。 */
    private final NatsErrorStrategy errorStrategy;

    /**
     * 构造 RPC 端点注册门面，所有依赖由 Spring 注入。
     *
     * @param connectionManager  NATS 连接持有者
     * @param serializer         请求/响应序列化器
     * @param headerInjector     响应 Header 注入器
     * @param subscriberFactory  订阅管道工厂
     * @param errorStrategy      消费错误处理策略
     */
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
     * 注册 RPC Handler（不使用 queue group）。
     * <p>底层会创建一个独立 dispatcher 与订阅；返回值持有 dispatcher 句柄，
     * 业务方不再需要时必须调用 {@link Registration#close()} 释放订阅线程。</p>
     *
     * @param subject     NATS subject（支持通配符）
     * @param requestType 请求类型
     * @param handler     业务处理函数（接收请求，返回响应）
     * @param <T>         请求泛型
     * @param <R>         响应泛型
     * @return 注册句柄；关闭后取消订阅并释放 dispatcher
     */
    public <T, R> Registration registerHandler(String subject, Class<T> requestType,
                                       Function<T, R> handler) {
        return registerHandler(subject, null, requestType, handler);
    }

    /**
     * 注册 RPC Handler（Queue Group 负载均衡）。
     * <p>传入相同 {@code queueGroup} 的多个实例会共同消费同一 subject，实现服务端负载均衡；
     * 每一实例仍持有独立 dispatcher，调用方仍需关闭 {@link Registration}。</p>
     *
     * @param subject     NATS subject（支持通配符）
     * @param queueGroup  Queue Group 名称（多实例负载均衡），为 {@code null} 时退化为单订阅
     * @param requestType 请求类型
     * @param handler     业务处理函数
     * @param <T>         请求泛型
     * @param <R>         响应泛型
     * @return 注册句柄；关闭后取消订阅并释放 dispatcher
     */
    public <T, R> Registration registerHandler(String subject, String queueGroup,
                                       Class<T> requestType, Function<T, R> handler) {
        Connection conn = connectionManager.getConnection();

        // 构建 RPC 管道 Handler：反序列化 → 执行 → 序列化 → 回复
        NatsMessageHandler rawHandler = msg -> {
            String replyTo = msg.getReplyTo();
            if (replyTo == null || replyTo.isBlank()) {
                // 没有 replyTo 就无法回复；忽略而非抛错，避免 NATS 流量被打成错误重投
                log.warn("NATS RPC: no replyTo for subject [{}], skipping", msg.getSubject());
                return;
            }

            try {
                T request = serializer.deserialize(msg.getData(), requestType);
                R response = handler.apply(request);

                // 序列化响应 + 注入追踪 header，使调用方能延续同一条链路
                byte[] responseData = serializer.serialize(response);
                Headers responseHeaders = new Headers();
                headerInjector.inject(responseHeaders);
                responseHeaders.put("nats-message-id", UUID.randomUUID().toString());

                conn.publish(replyTo, responseHeaders, responseData);
            } catch (Exception e) {
                // 把异常以 ErrorResponse 形式回写调用方，并重新抛出以触发错误策略
                byte[] errorResponse = serializer.serialize(new ErrorResponse(e.getMessage()));
                Headers errorHeaders = new Headers();
                errorHeaders.put("nats-error", "true");
                conn.publish(replyTo, errorHeaders, errorResponse);
                throw e;
            }
        };

        NatsMessageHandler pipelinedHandler = subscriberFactory.buildRpcPipeline(rawHandler);

        // dispatcher 内部多线程派发；handler 异常被吞掉后转交 errorStrategy
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
        return new Registration(conn, dispatcher);
    }

    /**
     * RPC 错误响应 DTO。
     * <p>当服务端 Handler 抛出异常时通过 {@code replyTo} 回写此结构；
     * {@code nats-error} Header 标记位由调用端检查决定是否走错误分支。</p>
     *
     * @param error 错误消息文本
     */
    public record ErrorResponse(String error) {
    }

    /**
     * RPC endpoint 的生命周期句柄。
     * <p>关闭时取消订阅并释放 dispatcher 线程；多次关闭是幂等的。</p>
     */
    public static final class Registration implements AutoCloseable {
        /** 持有 dispatcher 的连接，用于关闭 dispatcher。 */
        private final Connection connection;
        /** 订阅所在的 dispatcher，关闭时一并释放。 */
        private final Dispatcher dispatcher;
        /** 关闭标志位，避免多次关闭导致 {@code closeDispatcher} 重复调用抛错。 */
        private boolean closed;

        private Registration(Connection connection, Dispatcher dispatcher) {
            this.connection = connection;
            this.dispatcher = dispatcher;
        }

        /**
         * 关闭当前端点：取消 NATS 订阅并释放 dispatcher 工作线程。
         * <p>方法为 {@code synchronized} 以保证与并发收到的最后一批消息可见性，
         * 且天然幂等；业务通常在 Spring Bean 销毁或动态注册生命周期结束时调用。</p>
         */
        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            connection.closeDispatcher(dispatcher);
        }
    }
}
