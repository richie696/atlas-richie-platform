package com.richie.component.nats.integration;

import com.richie.component.nats.bus.JetStreamBus;
import com.richie.component.nats.config.NatsAutoConfiguration;
import com.richie.component.nats.exception.NatsException;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import com.richie.component.nats.support.NatsIntegrationTestSupport;
import com.richie.component.nats.support.TestEvent;
import io.nats.client.Connection;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.PublishAck;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JetStreamBus 门面端到端集成测试 — 基于 {@code @Autowired JetStreamBus} 验证发布/拉取/持续消费全链路。
 *
 * <p>使用 Testcontainers 启动带 JetStream 的 NATS 容器，验证场景：</p>
 * <ul>
 *   <li>publish 返回 PublishAck 包含序列号</li>
 *   <li>publish + fetch 批量拉取</li>
 *   <li>publish + next 单条拉取</li>
 *   <li>publish + consume 持续消费(自动 ack)</li>
 *   <li>Header 透传</li>
 *   <li>不存在的 subject 抛异常</li>
 * </ul>
 *
 * <p>注意:本 IT 启用 {@code platform.nats.jetstream.enabled=true} 触发 JetStreamBus Bean 装配;
 * NatsComponent 启动时不会预置 stream(默认 properties.getJetstream().isEnabled() = false),
 * 各测试自行通过独立连接管理 stream/consumer 生命周期。</p>
 *
 * @author richie696
 */
@SpringBootTest(classes = NatsAutoConfiguration.class,
        properties = "platform.nats.jetstream.enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIf("com.richie.component.nats.support.NatsIntegrationTestSupport#isEnabled")
class JetStreamBusFacadeIT {

    @DynamicPropertySource
    static void natsProperties(DynamicPropertyRegistry registry) {
        NatsIntegrationTestSupport.getInstance().registerNatsProperties(registry);
    }

    @Autowired
    private JetStreamBus jetStreamBus;

    @Autowired
    private NatsMessageSerializer serializer;

    private Connection adminConn;
    private JetStreamManagement jsm;
    private String streamName;
    private String subject;

    @BeforeEach
    void setUp() throws Exception {
        adminConn = io.nats.client.Nats.connect(
                NatsIntegrationTestSupport.getInstance().connectionUrl());
        jsm = adminConn.jetStreamManagement();

        streamName = "IT_JS_FACADE_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        subject = streamName + ".events";

        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subject)
                .retentionPolicy(RetentionPolicy.Limits)
                .storageType(StorageType.Memory)
                .maxMessages(1000)
                .build();
        jsm.addStream(streamConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (jsm != null) {
            try {
                jsm.deleteStream(streamName);
            } catch (Exception ignored) {
                // best effort
            }
        }
        if (adminConn != null && adminConn.getStatus() == Connection.Status.CONNECTED) {
            adminConn.close();
        }
    }

    @Test
    void publishReturnsPublishAck() {
        PublishAck ack = jetStreamBus.publish(streamName, subject, new TestEvent("first-event"));

        assertThat(ack).isNotNull();
        assertThat(ack.getStream()).isEqualTo(streamName);
        assertThat(ack.getSeqno()).isGreaterThan(0);
        assertThat(ack.hasError()).isFalse();
    }

    @Test
    void publishAndFetchBatch() throws Exception {
        String consumerName = "fetch-consumer";
        jsm.addOrUpdateConsumer(streamName, ConsumerConfiguration.builder()
                .name(consumerName)
                .ackPolicy(AckPolicy.Explicit)
                .deliverPolicy(DeliverPolicy.All)
                .build());

        int count = 5;
        for (int i = 0; i < count; i++) {
            jetStreamBus.publish(streamName, subject, new TestEvent("event-" + i));
        }

        Message msg;
        int received = 0;
        var fetchConsumer = jetStreamBus.fetch(streamName, consumerName, count);
        while ((msg = fetchConsumer.nextMessage()) != null) {
            TestEvent ev = serializer.deserialize(msg.getData(), TestEvent.class);
            assertThat(ev.content()).startsWith("event-");
            msg.ack();
            received++;
        }

        assertThat(received).isEqualTo(count);
    }

    @Test
    void publishAndNextSingleMessage() throws Exception {
        String consumerName = "next-consumer";
        jsm.addOrUpdateConsumer(streamName, ConsumerConfiguration.builder()
                .name(consumerName)
                .ackPolicy(AckPolicy.Explicit)
                .build());

        jetStreamBus.publish(streamName, subject, new TestEvent("single"));

        Message msg = jetStreamBus.next(streamName, consumerName, Duration.ofSeconds(5));
        assertThat(msg).isNotNull();
        TestEvent ev = serializer.deserialize(msg.getData(), TestEvent.class);
        assertThat(ev.content()).isEqualTo("single");
        msg.ack();

        // 超时等待 — 无更多消息(JetStream 要求 wait ≥ 1000ms)
        Message empty = jetStreamBus.next(streamName, consumerName, Duration.ofMillis(1100));
        assertThat(empty).isNull();
    }

    @Test
    void publishAndConsumeContinuously() throws Exception {
        String consumerName = "consume-consumer";
        jsm.addOrUpdateConsumer(streamName, ConsumerConfiguration.builder()
                .name(consumerName)
                .ackPolicy(AckPolicy.Explicit)
                .build());

        int count = 3;
        CountDownLatch latch = new CountDownLatch(count);
        CopyOnWriteArrayList<String> contents = new CopyOnWriteArrayList<>();

        var messageConsumer = jetStreamBus.consume(streamName, consumerName, m -> {
            TestEvent ev = serializer.deserialize(m.getData(), TestEvent.class);
            contents.add(ev.content());
            latch.countDown();
        });

        try {
            for (int i = 0; i < count; i++) {
                jetStreamBus.publish(streamName, subject, new TestEvent("consume-" + i));
            }

            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(contents).hasSize(count);
            for (int i = 0; i < count; i++) {
                assertThat(contents).contains("consume-" + i);
            }
        } finally {
            messageConsumer.stop();
        }
    }

    @Test
    void publishPropagatesInfrastructureHeaders() throws Exception {
        String consumerName = "header-consumer";
        jsm.addOrUpdateConsumer(streamName, ConsumerConfiguration.builder()
                .name(consumerName)
                .ackPolicy(AckPolicy.Explicit)
                .build());

        jetStreamBus.publish(streamName, subject, new TestEvent("with-headers"));

        Message msg = jetStreamBus.next(streamName, consumerName, Duration.ofSeconds(5));
        assertThat(msg).isNotNull();
        Headers actual = msg.getHeaders();
        assertThat(actual).isNotNull();
        // nats-message-id 由 JetStreamBus.publish 注入
        assertThat(actual.get("nats-message-id")).isNotNull().hasSize(1);
        msg.ack();
    }

    @Test
    void publishToUnknownStreamThrowsException() {
        String unknownStream = "UNKNOWN_" + UUID.randomUUID();
        String unknownSubject = unknownStream + ".x";

        assertThatThrownBy(() ->
                jetStreamBus.publish(unknownStream, unknownSubject, new TestEvent("boom")))
                .isInstanceOf(NatsException.class);
    }

    @Test
    void multipleConsumeHandlersEachReceiveCopy() throws Exception {
        String consumerA = "duplicate-consumer-A-" + UUID.randomUUID();
        String consumerB = "duplicate-consumer-B-" + UUID.randomUUID();
        jsm.addOrUpdateConsumer(streamName, ConsumerConfiguration.builder()
                .name(consumerA)
                .ackPolicy(AckPolicy.Explicit)
                .build());
        jsm.addOrUpdateConsumer(streamName, ConsumerConfiguration.builder()
                .name(consumerB)
                .ackPolicy(AckPolicy.Explicit)
                .build());

        CountDownLatch latch = new CountDownLatch(2);
        CopyOnWriteArrayList<String> a = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> b = new CopyOnWriteArrayList<>();

        // JetStream 与 NATS core 不同:同一消息只能被一个 consumer name 投递一次。
        // 用两个独立 consumer names 各自监听同一 stream,验证每个 consumer 拿到一份副本
        var c1 = jetStreamBus.consume(streamName, consumerA, m -> {
            TestEvent ev = serializer.deserialize(m.getData(), TestEvent.class);
            a.add(ev.content());
            latch.countDown();
        });
        var c2 = jetStreamBus.consume(streamName, consumerB, m -> {
            TestEvent ev = serializer.deserialize(m.getData(), TestEvent.class);
            b.add(ev.content());
            latch.countDown();
        });

        try {
            jetStreamBus.publish(streamName, subject, new TestEvent("broadcast"));

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(a).containsExactly("broadcast");
            assertThat(b).containsExactly("broadcast");
        } finally {
            c1.stop();
            c2.stop();
        }
    }
}
