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
package com.richie.component.nats.dlq;

import com.richie.component.nats.config.NatsProperties;
import com.richie.context.utils.data.JsonUtils;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.Subscription;
import io.nats.client.api.MessageInfo;
import io.nats.client.impl.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * DLQ advisory 监听器 — 订阅 NATS JetStream 的 MAX_DELIVERIES advisory,编排"取原消息 → 入 DLQ →
 * 擦除原消息"全流程,业务侧 0 改动。
 *
 * <h2>核心范式</h2>
 * NATS 在 consumer 重试耗尽时,自动发布 advisory 事件到内部 stream;
 * 本组件订阅该 advisory,触发 DLQ 入库。
 *
 * <h2>订阅过滤(R3 自反防护)</h2>
 * 每个业务 stream <strong>单独</strong>订阅对应 advisory subject,如:
 * <pre>{@code
 * $JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.ORDERS.>
 * }</pre>
 * <strong>禁止</strong>使用 catch-all {@code >}(否则 DLQ stream 自身的消息若触发 advisory 会进入自反循环);
 * 启动时主动排除名字以 {@code dlq.streamNameSuffix} 结尾的 stream。
 *
 * <h2>启动顺序</h2>
 * {@link #getPhase()} 返回 {@code Integer.MAX_VALUE - 50}:在 {@code NatsComponent}
 * ({@code Integer.MAX_VALUE - 100},负责建立连接 + provision stream) 之后启动,确保 advisory
 * subscribe 在业务 consumer 之前就绪(R8)。业务 consumer 自身通过 {@code ApplicationRunner} 启动,
 * 在所有 {@code SmartLifecycle.start()} 返回后才会触发 advisory 事件。
 *
 * <h2>失败模式</h2>
 * <ul>
 *   <li>{@code getMessage} 返回 10060 (Message Not Found):原消息已被 purge / retention 删除,
 *       ack advisory 不重投(R3 防止自反)</li>
 *   <li>{@code publisher.publish} 失败:nak advisory,broker 端按 max-deliver 重投</li>
 *   <li>{@code deleteMessage} 失败:log.error 但仍 ack advisory,不阻塞后续(原消息最多保留到
 *       retention 触发删除,业务可通过 DLQ stream 看到完整信息)</li>
 *   <li>其他异常:log.error + nak advisory</li>
 * </ul>
 *
 * <h2>Core NATS fallback</h2>
 * 当 {@code js.subscribe()} 抛异常(如 advisory stream 内部异常、权限不足),降级为
 * {@code nc.subscribe()} Core NATS 模式:fire-and-forget,仅记录日志,无 ack(R8 由启动顺序保证)。
 *
 * <h2>idempotent</h2>
 * 同一 advisory 多次重投(进程崩溃、broker 重投)对 {@code getMessage}:第二次会得 10060 → ack;
 * 对 {@code deleteMessage}:第二次会得 10060 → 视为成功;对 {@code publisher.publish}:DLQ 会出现
 * 重复消息,由 Todo 8 引入的 DLQ consumer 做去重(本期不实现)。
 *
 * @author richie696
 * @since 1.0.0
 */
public class NatsDeadLetterAdvisoryConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(NatsDeadLetterAdvisoryConsumer.class);

    /**
     * NATS JetStream 错误码:Message Not Found。{@code getMessage} / {@code deleteMessage} 找不到
     * 目标消息时返回。DLQ advisory 场景下,出现此错误代表原消息已被 purge / retention / 手动删除,
     * 应 ack advisory 不重投(R3 防止自反)。
     */
    private static final int ERR_CODE_MESSAGE_NOT_FOUND = 10060;

    /**
     * NATS advisory subject 前缀。完整格式:
     * {@code $JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.<stream>.<consumer>}
     * 本组件订阅 {@code <prefix>.<stream>.>},advisory 投递时 NATS server 自动匹配。
     */
    private static final String ADVISORY_SUBJECT_PREFIX = "$JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.";

    /** advisory 订阅后 flush 超时。 */
    private static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(5);

    /** dispatcher drain 内部超时(单条消息处理预算)。 */
    private static final Duration DISPATCHER_DRAIN_TIMEOUT = Duration.ofSeconds(2);

    /** dispatcher drain 整体等待超时。 */
    private static final Duration DISPATCHER_DRAIN_AWAIT = Duration.ofSeconds(5);

    private final Connection connection;
    private final JetStream jetStream;
    private final JetStreamManagement jetStreamManagement;
    private final NatsDeadLetterPublisher publisher;
    private final NatsProperties properties;

    private volatile boolean running = false;
    private Dispatcher dispatcher;
    private Dispatcher fallbackDispatcher;
    private final List<Subscription> fallbackSubscriptions = new CopyOnWriteArrayList<>();

    public NatsDeadLetterAdvisoryConsumer(Connection connection,
                                          JetStream jetStream,
                                          JetStreamManagement jetStreamManagement,
                                          NatsDeadLetterPublisher publisher,
                                          NatsProperties properties) {
        this.connection = connection;
        this.jetStream = jetStream;
        this.jetStreamManagement = jetStreamManagement;
        this.publisher = publisher;
        this.properties = properties;
    }

    // ===== SmartLifecycle =====

    @Override
    public int getPhase() {
        // Integer.MAX_VALUE - 50:晚于 NatsComponent(-100,负责连接 + stream provision),
        // 早于业务 consumer(由 ApplicationRunner 启动,SmartLifecycle.start() 返回后才执行),
        // 保证 advisory subscription 在任何业务消息可能触发 advisory 之前就绪
        return Integer.MAX_VALUE - 50;
    }

    @Override
    public boolean isAutoStartup() {
        return properties.isEnabled()
                && properties.getJetstream().isEnabled()
                && properties.getDlq().isEnabled();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        if (!properties.getDlq().isEnabled()) {
            log.info("DLQ disabled (platform.nats.jetstream.dlq.enabled=false), advisory consumer not started");
            return;
        }
        if (!properties.getJetstream().isEnabled()) {
            log.info("JetStream disabled, DLQ advisory consumer requires JetStream, skipping");
            return;
        }

        // 1. 过滤掉 DLQ stream 自身(R3 自反防护:DLQ stream 触发的 advisory 不应再次入 DLQ)
        List<NatsProperties.StreamDefinition> businessStreams = properties.getJetstream().getStreams().stream()
                .filter(s -> s.getName() != null
                        && !s.getName().endsWith(properties.getDlq().getStreamNameSuffix()))
                .toList();

        if (businessStreams.isEmpty()) {
            log.info("No business streams configured, DLQ advisory consumer idle");
            running = true;
            return;
        }

        // 2. 共享一个 dispatcher,所有 advisory 订阅复用同一组工作线程
        dispatcher = connection.createDispatcher();

        // 3. per-business-stream 订阅 advisory(禁止 catch-all `>`,防 R3 自反)
        for (NatsProperties.StreamDefinition stream : businessStreams) {
            String advisorySubject = ADVISORY_SUBJECT_PREFIX + stream.getName() + ".>";
            subscribeAdvisoryForStream(stream.getName(), advisorySubject);
        }

        running = true;
        log.info("DLQ advisory consumer started, subscribed to {} business stream(s)",
                businessStreams.size());
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        log.info("DLQ advisory consumer stopping...");

        // 1. drain 共享 dispatcher(关闭 dispatcher 线程 + 取消所有 advisory 订阅)
        if (dispatcher != null) {
            try {
                CompletableFuture<Boolean> drainFuture = dispatcher.drain(DISPATCHER_DRAIN_TIMEOUT);
                drainFuture.get(DISPATCHER_DRAIN_AWAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("DLQ advisory dispatcher drain timeout, proceeding with stop");
            } catch (Exception e) {
                log.warn("DLQ advisory dispatcher drain failed", e);
            }
        }

        // 2. drain fallback dispatcher(关闭 Core NATS fallback 线程 + 取消所有 fallback 订阅)
        if (fallbackDispatcher != null) {
            try {
                CompletableFuture<Boolean> drainFuture = fallbackDispatcher.drain(DISPATCHER_DRAIN_TIMEOUT);
                drainFuture.get(DISPATCHER_DRAIN_AWAIT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                log.warn("DLQ advisory fallback dispatcher drain timeout, proceeding with stop");
            } catch (Exception e) {
                log.warn("DLQ advisory fallback dispatcher drain failed", e);
            }
            fallbackSubscriptions.clear();
        }

        log.info("DLQ advisory consumer stopped");
    }

    // ===== 内部:订阅 =====

    /**
     * 订阅单个业务 stream 的 advisory 事件。优先 {@link JetStream#subscribe} (NATS 自动管理内部
     * advisory stream + 支持 msg.ack/nak),失败降级 {@link Connection#subscribe} (Core NATS,
     * fire-and-forget)。
     */
    private void subscribeAdvisoryForStream(String streamName, String subject) {
        try {
            // deliverGroup = queue group,实现 HA 多 pod 去重(同组内只有一例消费 advisory)
            // autoAck=false:handler 内部手动 ack/nak,精细控制 broker 重投行为
            // jnats 2.25.3 不支持 5 参数 (subject, queue, dispatcher, handler, autoAck) 重载;
            // 必须用 6 参数 (含 PushSubscribeOptions),用 builder 设 deliverGroup
            PushSubscribeOptions pso = PushSubscribeOptions.builder()
                    .deliverGroup(properties.getDlq().getQueueGroup())
                    .build();
            jetStream.subscribe(subject,
                    properties.getDlq().getQueueGroup(),
                    dispatcher,
                    this::handleAdvisory,
                    false,
                    pso);
            try {
                connection.flush(FLUSH_TIMEOUT);
            } catch (Exception flushEx) {
                // flush 失败不致命,订阅实际已发出
                log.debug("DLQ advisory flush failed (non-fatal): subject={}", subject, flushEx);
            }
            log.info("DLQ advisory subscription (JetStream) registered: stream={} subject={} queueGroup={}",
                    streamName, subject, properties.getDlq().getQueueGroup());
        } catch (Exception e) {
            log.warn("DLQ advisory JetStream subscribe failed, fallback to Core NATS: stream={} subject={}",
                    streamName, subject, e);
            try {
                // 共享一个 fallback dispatcher(避免每个 stream 单独建线程)
                if (fallbackDispatcher == null) {
                    fallbackDispatcher = connection.createDispatcher();
                }
                Subscription sub = fallbackDispatcher.subscribe(subject, this::handleCoreAdvisory);
                fallbackSubscriptions.add(sub);
                log.info("DLQ advisory subscription (Core NATS fallback) registered: stream={} subject={}",
                        streamName, subject);
            } catch (Exception coreEx) {
                log.error("DLQ advisory Core NATS fallback subscribe ALSO failed: stream={} subject={}",
                        streamName, subject, coreEx);
            }
        }
    }

    // ===== 内部:JetStream advisory 处理器 =====

    /**
     * JetStream advisory handler。完整流程:
     * <ol>
     *   <li>解析 advisory JSON payload,提取 {@code stream} / {@code stream_seq} / {@code deliveries}</li>
     *   <li>{@code jsm.getMessage(stream, seq)} 取原 {@link MessageInfo}
     *       <ul>
     *         <li>10060 → 原消息已 purge / retention,ack advisory 不重投(R3)</li>
     *         <li>其他错误 → nak advisory 让 broker 重投</li>
     *       </ul>
     *   </li>
     *   <li>计算 DLQ subject = {@code info.subject + dlq.subjectSuffix},构造
     *       {@link NatsDeadLetterMessage}(含 traceparent / retryCount / originalStreamSeq / advisoryType)</li>
     *   <li>{@code publisher.publish(...)} 入 DLQ;失败 → nak advisory</li>
     *   <li>{@code jsm.deleteMessage(stream, seq, erase=true)} 擦除原消息;失败 → log.error 但仍
     *       ack advisory(不阻塞后续;原消息最多保留到 retention 触发删除,DLQ 已有完整副本)</li>
     *   <li>ack advisory</li>
     * </ol>
     */
    private void handleAdvisory(Message msg) {
        // 1. 解析 advisory JSON payload
        String sourceStream;
        long streamSeq;
        long deliveries;
        try {
            if (msg.getData() == null) {
                log.warn("DLQ advisory received with null data, skipping: subject={}", msg.getSubject());
                safeAck(msg);
                return;
            }
            String json = new String(msg.getData(), StandardCharsets.UTF_8);
            JsonNode payload = JsonUtils.getInstance().convertJsonNode(json);
            if (payload == null) {
                log.error("DLQ advisory JSON parse failed, dropping: subject={}", msg.getSubject());
                safeAck(msg);
                return;
            }
            sourceStream = payload.path("stream").asText("");
            streamSeq = payload.path("stream_seq").asLong(0L);
            deliveries = payload.path("deliveries").asLong(0L);
            if (sourceStream.isEmpty() || streamSeq <= 0L) {
                log.error("DLQ advisory missing stream/stream_seq, dropping: subject={} stream={} seq={}",
                        msg.getSubject(), sourceStream, streamSeq);
                safeAck(msg);
                return;
            }
        } catch (Exception e) {
            log.error("DLQ advisory payload parse unexpected error, dropping: subject={}",
                    msg.getSubject(), e);
            safeAck(msg);
            return;
        }

        // 2. 取原消息
        MessageInfo info;
        try {
            info = jetStreamManagement.getMessage(sourceStream, streamSeq);
        } catch (JetStreamApiException e) {
            // nats 2.10 'Message Not Found': main code=404 + api code=10037; 早期 jnats 用 10060
            // 三个错误码都代表 "message not found":原消息已被 purge / retention / 手动删除
            if (e.getErrorCode() == 404 || e.getErrorCode() == 10060 || e.getErrorCode() == 10037) {
                log.warn("DLQ advisory: source message already gone, ack and skip: stream={} seq={}",
                        sourceStream, streamSeq);
                safeAck(msg);
                return;
            }
            log.error("DLQ advisory getMessage failed (not message-not-found), nak for retry: stream={} seq={}",
                    sourceStream, streamSeq, e);
            safeNak(msg);
            return;
        } catch (Exception e) {
            log.error("DLQ advisory getMessage unexpected error, nak for retry: stream={} seq={}",
                    sourceStream, streamSeq, e);
            safeNak(msg);
            return;
        }

        // 3. 计算 DLQ subject + 构造 DLQ message meta
        String dlqSubject = info.getSubject() + properties.getDlq().getSubjectSuffix();
        Headers originalHeaders = info.getHeaders();
        NatsDeadLetterMessage dlqMeta = new NatsDeadLetterMessage(
                deliveries,
                extractTraceparentIgnoreCase(originalHeaders),
                info.getSeq(),
                AdvisoryType.MAX_DELIVERIES
        );

        // 4. publish DLQ
        try {
            publisher.publish(sourceStream, dlqSubject, info.getData(), originalHeaders, dlqMeta);
        } catch (Exception pubEx) {
            log.error("DLQ publish failed, nak advisory for broker redelivery: stream={} seq={} dlqSubject={}",
                    sourceStream, streamSeq, dlqSubject, pubEx);
            safeNak(msg);
            return;
        }

        // 5. erase 原消息(失败 log.error 但仍 ack advisory,不阻塞;原消息最多保留到 retention)
        try {
            jetStreamManagement.deleteMessage(sourceStream, streamSeq, true);
            log.info("DLQ original message erased: stream={} seq={}", sourceStream, streamSeq);
        } catch (JetStreamApiException delEx) {
            if (delEx.getErrorCode() == ERR_CODE_MESSAGE_NOT_FOUND) {
                // 重投场景:advisory 处理过但 ack 失败 → broker 重投 → 第二次 delete 命中 10060
                // 视为成功(消息已不在原 stream)
                log.info("DLQ original message already gone on delete (idempotent): stream={} seq={}",
                        sourceStream, streamSeq);
            } else {
                log.error("DLQ deleteMessage failed (non-fatal, ack advisory anyway): stream={} seq={}",
                        sourceStream, streamSeq, delEx);
            }
        } catch (Exception delEx) {
            log.error("DLQ deleteMessage unexpected error (non-fatal, ack advisory anyway): stream={} seq={}",
                    sourceStream, streamSeq, delEx);
        }

        // 6. ack advisory
        safeAck(msg);
    }

    /**
     * Core NATS fallback handler — advisory 收到但无法 ack/nak(Core NATS 协议无 ack),
     * fire-and-forget 仅记录日志。R8 由启动顺序保证:during normal operation, advisory 走
     * JetStream 路径,只有 JetStream subscribe 失败时才会落到此 handler。
     */
    private void handleCoreAdvisory(Message msg) {
        int dataLen = msg.getData() == null ? 0 : msg.getData().length;
        log.warn("DLQ advisory (Core NATS fallback, no ack): subject={} dataLen={}",
                msg.getSubject(), dataLen);
    }

    // ===== 内部:helpers =====

    /**
     * 大小写无关查找 traceparent header(与 {@link NatsDeadLetterMessage} 内同类实现同源)。
     * NATS server 2.11+ 不再改写 header 大小写,但 OTel SDK 不同版本注入的 header name
     * 大小写不一致,必须兜底。
     */
    private static String extractTraceparentIgnoreCase(Headers headers) {
        if (headers == null) {
            return null;
        }
        for (String key : headers.keySet()) {
            if ("traceparent".equalsIgnoreCase(key)) {
                var values = headers.get(key);
                if (values != null && !values.isEmpty()) {
                    return values.get(0);
                }
            }
        }
        return null;
    }

    private void safeAck(Message msg) {
        try {
            msg.ack();
        } catch (Exception e) {
            log.warn("DLQ advisory ack failed (non-fatal): subject={}", msg.getSubject(), e);
        }
    }

    private void safeNak(Message msg) {
        try {
            msg.nak();
        } catch (Exception e) {
            log.warn("DLQ advisory nak failed (non-fatal): subject={}", msg.getSubject(), e);
        }
    }
}
