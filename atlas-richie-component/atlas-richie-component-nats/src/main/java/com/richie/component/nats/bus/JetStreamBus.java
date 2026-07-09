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
import com.richie.component.nats.exception.NatsException;
import com.richie.component.nats.pipeline.NatsMessageHandler;
import com.richie.component.nats.pipeline.NatsSubscriberFactory;
import com.richie.component.nats.strategy.NatsErrorStrategy;
import com.richie.component.nats.strategy.NatsHeaderInjector;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import com.richie.component.nats.strategy.NatsTracingSupport;
import io.nats.client.ConsumerContext;
import io.nats.client.FetchConsumer;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.MessageConsumer;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * JetStream 门面
 *
 * <p>所有操作基于 JetStream 协议：持久化存储，at-least-once 投递保证。
 * 每次操作自动完成序列化、上下文注入、链路追踪等横切关注点。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class JetStreamBus {

    private final NatsConnectionManager connectionManager;
    private final NatsMessageSerializer serializer;
    private final NatsHeaderInjector headerInjector;
    private final NatsTracingSupport tracingSupport;
    private final NatsSubscriberFactory subscriberFactory;
    private final NatsErrorStrategy errorStrategy;

    public JetStreamBus(NatsConnectionManager connectionManager,
                        NatsMessageSerializer serializer,
                        NatsHeaderInjector headerInjector,
                        NatsTracingSupport tracingSupport,
                        NatsSubscriberFactory subscriberFactory,
                        NatsErrorStrategy errorStrategy) {
        this.connectionManager = connectionManager;
        this.serializer = serializer;
        this.headerInjector = headerInjector;
        this.tracingSupport = tracingSupport;
        this.subscriberFactory = subscriberFactory;
        this.errorStrategy = errorStrategy;
    }

    // ===== 发布（服务端确认写入）=====

    /**
     * 发布消息到 JetStream，等待服务端确认写入
     *
     * @param streamName Stream 名称
     * @param subject    NATS subject（必须属于该 Stream）
     * @param message    消息对象（自动序列化）
     * @return PublishAck 包含服务端分配的序列号
     */
    public PublishAck publish(String streamName, String subject, Object message) {
        byte[] data = serializer.serialize(message);
        Headers headers = new Headers();
        headerInjector.inject(headers);
        headers.put("nats-message-id", UUID.randomUUID().toString());

        Span span = tracingSupport.startProducerSpan(subject, headers);
        try (Scope ignored = io.opentelemetry.context.Context.current().with(span).makeCurrent()) {
            JetStream js = connectionManager.getConnection().jetStream();
            PublishAck ack = js.publish(subject, headers, data);
            tracingSupport.finishSpan(span, true, null);
            return ack;
        } catch (Exception e) {
            tracingSupport.finishSpan(span, false, e.getMessage());
            errorStrategy.onPublishError(subject, data, e);
            throw new NatsException("Failed to publish JetStream message to subject: " + subject, e);
        }
    }

    // ===== 持续消费（自动 ack/nak 管理）=====

    /**
     * 持续消费 JetStream 消息，成功自动 ack，失败自动 nak（触发服务端重投递）
     *
     * @param streamName   Stream 名称
     * @param consumerName Consumer 名称
     * @param handler      业务处理 Handler
     * @return MessageConsumer（可用于 stop）
     */
    public MessageConsumer consume(String streamName, String consumerName,
                                    NatsMessageHandler handler) {
        ConsumerContext consumerCtx = connectionManager.getConsumerContext(streamName, consumerName);
        NatsMessageHandler pipelinedHandler = subscriberFactory.buildAsyncPipeline(handler);

        try {
            MessageConsumer mc = consumerCtx.consume(msg -> {
                try {
                    pipelinedHandler.handle(msg);
                    msg.ack();
                } catch (Exception e) {
                    errorStrategy.onConsumeError(msg.getSubject(), msg, e);
                    msg.nak();
                }
            });
            log.info("JetStream consumer [{}] on stream [{}] started", consumerName, streamName);
            return mc;
        } catch (Exception e) {
            throw new NatsException("Failed to start JetStream consumer: "
                    + streamName + "/" + consumerName, e);
        }
    }

    // ===== 批量拉取 =====

    /**
     * 批量拉取 JetStream 消息
     *
     * <p>返回 MessageConsumer 用于迭代消息，每条消息需手动 ack。
     * 使用 {@code nextMessage()} 获取下一条消息，返回 null 表示批次结束。</p>
     *
     * @param streamName   Stream 名称
     * @param consumerName Consumer 名称
     * @param batchSize    本批次最大消息数
     * @return FetchConsumer 迭代器
     */
    public FetchConsumer fetch(String streamName, String consumerName, int batchSize) {
        ConsumerContext consumerCtx = connectionManager.getConsumerContext(streamName, consumerName);
        try {
            return consumerCtx.fetchMessages(batchSize);
        } catch (Exception e) {
            throw new NatsException("Failed to fetch from JetStream: "
                    + streamName + "/" + consumerName, e);
        }
    }

    // ===== 单条拉取 =====

    /**
     * 拉取单条 JetStream 消息
     *
     * @param streamName   Stream 名称
     * @param consumerName Consumer 名称
     * @param timeout      等待超时
     * @return Message 或 null（无消息时）
     */
    public Message next(String streamName, String consumerName, Duration timeout) {
        ConsumerContext consumerCtx = connectionManager.getConsumerContext(streamName, consumerName);
        try {
            return consumerCtx.next(timeout);
        } catch (Exception e) {
            throw new NatsException("Failed to get next message from JetStream: "
                    + streamName + "/" + consumerName, e);
        }
    }


}
