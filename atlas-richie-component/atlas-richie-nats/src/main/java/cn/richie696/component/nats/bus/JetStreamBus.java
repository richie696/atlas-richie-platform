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
import cn.richie696.component.nats.pipeline.NatsMessageHandler;
import cn.richie696.component.nats.pipeline.NatsSubscriberFactory;
import cn.richie696.component.nats.strategy.NatsErrorStrategy;
import cn.richie696.component.nats.strategy.NatsHeaderInjector;
import cn.richie696.component.nats.strategy.NatsMessageSerializer;
import cn.richie696.component.nats.strategy.NatsTracingSupport;
import io.nats.client.*;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

/**
 * JetStream 门面
 *
 * <p>所有操作基于 JetStream 协议：持久化存储，at-least-once 投递保证。
 * 每次操作自动完成序列化、上下文注入、链路追踪等横切关注点。</p>
 *
 * <p><b>线程安全</b>：实例由 Spring 单例持有；
 * {@link #heartbeatExecutor} 是独立守护线程，心跳任务以固定速率异步触发，
 * 与消费回调线程解耦以避免阻塞业务处理器。</p>
 *
 * <p><b>生命周期</b>：实现 {@link AutoCloseable}，由 Spring 容器在销毁阶段调用 {@link #close()}
 * 关闭心跳线程池；业务侧使用 {@link MessageConsumer} 时也应在不再需要时停止消费。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
public class JetStreamBus implements AutoCloseable {

    /**
     * 找不到显式 {@code nak-delay} 配置时的默认延迟，避免 LLM 限流等瞬时失败被立刻重试。
     */
    private static final Duration DEFAULT_NAK_DELAY = Duration.ofSeconds(5);
    /**
     * 心跳线程工厂计数器，用于为每个 JetStreamBus 实例生成唯一的守护线程名。
     */
    private static final AtomicInteger HEARTBEAT_THREAD_COUNTER = new AtomicInteger();

    /** NATS 连接持有者。 */
    private final NatsConnectionManager connectionManager;
    /** 消息体序列化器。 */
    private final NatsMessageSerializer serializer;
    /** 出站 Header 注入器。 */
    private final NatsHeaderInjector headerInjector;
    /** 链路追踪门面。 */
    private final NatsTracingSupport tracingSupport;
    /** 订阅管道工厂。 */
    private final NatsSubscriberFactory subscriberFactory;
    /** 错误处理策略。 */
    private final NatsErrorStrategy errorStrategy;
    /** 外部配置（用于解析 consumer 自定义 nak-delay 等）。 */
    private final NatsProperties properties;
    /**
     * 专用的单线程调度线程池，在 consume 期间周期性发送 JetStream {@code inProgress} 心跳，
     * 确保长任务不会因为 ack-wait 过期而被重投。
     */
    private final ScheduledExecutorService heartbeatExecutor;

    /**
     * 构造 JetStream 门面，所有依赖由 Spring 注入。
     * <p>构造阶段同时创建单线程守护的心跳调度器，线程名带递增序号便于多实例调试。</p>
     *
     * @param connectionManager NATS 连接持有者
     * @param serializer        消息体序列化器
     * @param headerInjector    出站 Header 注入器
     * @param tracingSupport    链路追踪门面
     * @param subscriberFactory 订阅管道工厂
     * @param errorStrategy     错误处理策略
     * @param properties        外部配置
     */
    public JetStreamBus(NatsConnectionManager connectionManager,
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
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(heartbeatThreadFactory());
    }

    // ===== 发布（服务端确认写入）=====

    /**
     * 发布消息到 JetStream，等待服务端确认写入。
     * <p>发布前会通过 {@link #validateSubjectBelongsToStream(String, String)} 校验 subject
     * 落点，避免误把消息发到未声明的 stream；同时启动 Producer Span 并在出错时立即关闭。</p>
     *
     * @param streamName Stream 名称
     * @param subject    NATS subject（必须属于该 Stream）
     * @param message    消息对象（自动序列化）
     * @return PublishAck 包含服务端分配的序列号
     * @throws IllegalArgumentException subject 不属于指定 stream 时抛出
     * @throws NatsException             底层发布失败时抛出
     */
    public PublishAck publish(String streamName, String subject, Object message) {
        byte[] data = serializer.serialize(message);
        Headers headers = new Headers();
        headerInjector.inject(headers);
        headers.put("nats-message-id", UUID.randomUUID().toString());

        Span span = tracingSupport.startProducerSpan(subject, headers);
        try (Scope ignored = io.opentelemetry.context.Context.current().with(span).makeCurrent()) {
            validateSubjectBelongsToStream(streamName, subject);
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

    /**
     * 校验 subject 是否落在目标 stream 的 subject 过滤集合内。
     * <p>通过拉取 streamInfo 并遍历配置中的 subjects 完成；任何底层异常冒泡由发布方法统一包装。</p>
     *
     * @param streamName Stream 名称
     * @param subject    待校验的 subject
     * @throws Exception            拉取 stream 配置失败时抛出
     * @throws IllegalArgumentException subject 不匹配任何 stream subject 时抛出
     */
    private void validateSubjectBelongsToStream(String streamName, String subject) throws Exception {
        var streamInfo = connectionManager.getStreamContext(streamName).getStreamInfo();
        boolean matches = streamInfo.getConfiguration().getSubjects().stream()
                .anyMatch(pattern -> subjectMatches(pattern, subject));
        if (!matches) {
            throw new IllegalArgumentException("Subject [" + subject + "] does not belong to JetStream stream ["
                    + streamName + "]");
        }
    }

    /**
     * 按 NATS subject 通配符语义（{@code *} 匹配单段、{@code >} 匹配剩余所有段）判断给定 subject
     * 是否命中过滤 pattern。逐段比较而不依赖 SDK 工具类，便于解释诊断输出与单元测试。
     *
     * @param pattern stream 配置的过滤 pattern
     * @param subject 待校验的 subject
     * @return 命中返回 {@code true}
     */
    private boolean subjectMatches(String pattern, String subject) {
        String[] patternTokens = pattern.split("\\.");
        String[] subjectTokens = subject.split("\\.");
        int index = 0;
        while (index < patternTokens.length && index < subjectTokens.length) {
            String token = patternTokens[index];
            if (">".equals(token)) {
                // ">" 仅在 pattern 末段语义有效；前置出现按不匹配处理
                return index == patternTokens.length - 1;
            }
            if (!"*".equals(token) && !token.equals(subjectTokens[index])) {
                return false;
            }
            index++;
        }
        return index == patternTokens.length && index == subjectTokens.length;
    }

    // ===== 持续消费（自动 ack/nak 管理）=====

    /**
     * 持续消费 JetStream 消息，执行期间自动发送 in-progress 心跳，成功自动 ack；失败后按
     * consumer 的 {@code nak-delay} 延迟重投，避免 LLM 限流等瞬时故障形成重试风暴。
     * <p>心跳频率取 {@code ack-wait / 3}：服务端超过 ack-wait 未收到 ack 会重投，
     * 三分之一间隔保证任意时刻都有一个 in-progress 事件被服务端收到，支撑长任务执行。
     * 业务回调返回/抛错后即取消心跳，避免不必要的 in-progress 请求。</p>
     *
     * @param streamName   Stream 名称
     * @param consumerName Consumer 名称
     * @param handler      业务处理 Handler
     * @return MessageConsumer（可用于 stop）
     * @throws NatsException 当底层创建消费失败时抛出
     */
    public MessageConsumer consume(String streamName, String consumerName,
                                   NatsMessageHandler handler) {
        ConsumerContext consumerCtx = connectionManager.getConsumerContext(streamName, consumerName);
        NatsMessageHandler pipelinedHandler = subscriberFactory.buildAsyncPipeline(handler);
        Duration heartbeatInterval = resolveHeartbeatInterval(consumerCtx);
        Duration nakDelay = resolveNakDelay(streamName, consumerName);

        try {
            MessageConsumer mc = consumerCtx.consume(msg -> {
                // 拉起心跳任务；任务会在 handler 返回或抛错时被取消，避免心跳丢失导致服务侧误判过期
                ScheduledFuture<?> heartbeat = scheduleHeartbeat(msg, heartbeatInterval);
                try {
                    pipelinedHandler.handle(msg);
                    msg.ack();
                } catch (Exception e) {
                    errorStrategy.onConsumeError(msg.getSubject(), msg, e);
                    msg.nakWithDelay(nakDelay);
                } finally {
                    if (heartbeat != null) {
                        heartbeat.cancel(false);
                    }
                }
            });
            log.info("JetStream consumer [{}] on stream [{}] started", consumerName, streamName);
            return mc;
        } catch (Exception e) {
            throw new NatsException("Failed to start JetStream consumer: "
                    + streamName + "/" + consumerName, e);
        }
    }

    /**
     * 计算 in-progress 心跳间隔：取消费者 ack-wait 的三分之一。
     * <p>读取失败或 ack-wait 为空/非正时回退为 {@link Duration#ZERO}，让上游关闭心跳任务以减少无效调度。</p>
     *
     * @param consumerCtx 消费者上下文
     * @return 心跳间隔，{@link Duration#ZERO} 表示禁用
     */
    private Duration resolveHeartbeatInterval(ConsumerContext consumerCtx) {
        try {
            Duration ackWait = consumerCtx.getConsumerInfo().getConsumerConfiguration().getAckWait();
            if (ackWait != null && !ackWait.isNegative() && !ackWait.isZero()) {
                return ackWait.dividedBy(3);
            }
        } catch (Exception e) {
            // 读取 ack-wait 不应阻塞消费启动；降级为不发送心跳，由业务的显式 nak 控制重试
            log.warn("Failed to resolve JetStream ack wait; in-progress heartbeat disabled", e);
        }
        return Duration.ZERO;
    }

    /**
     * 从外部配置中查找该 consumer 的 {@code nak-delay}。
     * <p>仅取第一个非负值；缺失或为负时回落到 {@link #DEFAULT_NAK_DELAY}，避免重投风暴。</p>
     *
     * @param streamName   Stream 名称
     * @param consumerName Consumer 名称
     * @return nak 延迟，非空非负
     */
    private Duration resolveNakDelay(String streamName, String consumerName) {
        return properties.getJetstream().getStreams().stream()
                .filter(stream -> streamName.equals(stream.getName()))
                .flatMap(stream -> stream.getConsumers().stream())
                .filter(consumer -> consumerName.equals(consumer.getName()))
                .map(NatsProperties.ConsumerDefinition::getNakDelay)
                .filter(delay -> delay != null && !delay.isNegative())
                .findFirst()
                .orElse(DEFAULT_NAK_DELAY);
    }

    /**
     * 调度周期性的 in-progress 心跳。间隔非正时返回 {@code null}，由调用方直接走 finally 跳过取消逻辑。
     * <p>底层用单线程调度器串行执行，in-progress 调用自身的异常被吞掉以避免打断后续心跳。</p>
     *
     * @param message  被处理的 JetStream 消息
     * @param interval 心跳间隔
     * @return 调度句柄，或 {@code null} 表示未启用
     */
    private ScheduledFuture<?> scheduleHeartbeat(Message message, Duration interval) {
        if (interval.isZero() || interval.isNegative()) {
            return null;
        }
        long delayMillis = Math.max(1, interval.toMillis());
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                message.inProgress();
            } catch (Exception e) {
                log.warn("JetStream in-progress heartbeat failed: subject={}", message.getSubject(), e);
            }
        }, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 心跳调度线程工厂：守护线程 + 递增编号，便于多实例场景下线程栈定位。
     *
     * @return 心跳线程工厂
     */
    private ThreadFactory heartbeatThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable,
                    "nats-jetstream-heartbeat-" + HEARTBEAT_THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * 关闭心跳调度线程池。
     * <p>由 Spring 容器在 Bean 销毁阶段调用，{@code shutdownNow} 会中断在途任务；
     * 调用后整个 JetStreamBus 实例不再可用。</p>
     */
    @Override
    public void close() {
        heartbeatExecutor.shutdownNow();
    }

    // ===== 批量拉取 =====

    /**
     * 批量拉取 JetStream 消息。
     * <p>返回 {@link FetchConsumer} 用于迭代消息，每条消息需手动 ack。
     * 使用 {@code nextMessage()} 获取下一条消息，返回 {@code null} 表示批次结束。</p>
     *
     * @param streamName   Stream 名称
     * @param consumerName Consumer 名称
     * @param batchSize    本批次最大消息数
     * @return FetchConsumer 迭代器
     * @throws NatsException 当底层拉取失败时抛出
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
     * 拉取单条 JetStream 消息。
     *
     * @param streamName   Stream 名称
     * @param consumerName Consumer 名称
     * @param timeout      等待超时
     * @return Message 或 {@code null}（无消息时）
     * @throws NatsException 当底层拉取失败时抛出
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
