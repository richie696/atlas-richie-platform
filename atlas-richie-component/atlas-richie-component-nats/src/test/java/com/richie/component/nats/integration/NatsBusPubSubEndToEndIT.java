package com.richie.component.nats.integration;

import com.richie.component.nats.support.NatsIntegrationTestSupport;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Core NATS 发布/订阅端到集成测试 — 使用 Testcontainers 启动真实 NATS 容器。
 * <p>
 * 验证真实 NATS 服务器上的发布者-订阅者通信，包括：
 * <ul>
 *     <li>单消息发布/订阅</li>
 *     <li>多消息批量发布</li>
 *     <li>Request-Reply 模式</li>
 *     <li>Header 透传</li>
 *     <li>Queue Group 负载均衡</li>
 * </ul>
 *
 * @author richie696
 */
@EnabledIf("com.richie.component.nats.support.NatsIntegrationTestSupport#isEnabled")
class NatsBusPubSubEndToEndIT {

    private Connection publisherConn;
    private Connection subscriberConn;

    @BeforeEach
    void setUp() throws Exception {
        NatsIntegrationTestSupport support = NatsIntegrationTestSupport.getInstance();
        publisherConn = Nats.connect(support.connectionUrl());
        subscriberConn = Nats.connect(support.connectionUrl());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (publisherConn != null && publisherConn.getStatus() == Connection.Status.CONNECTED) {
            publisherConn.close();
        }
        if (subscriberConn != null && subscriberConn.getStatus() == Connection.Status.CONNECTED) {
            subscriberConn.close();
        }
    }

    @Test
    void subscriberReceivesPublishedMessage() throws Exception {
        String subject = "it.pubsub." + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> received = new CopyOnWriteArrayList<>();

        Dispatcher dispatcher = subscriberConn.createDispatcher();
        dispatcher.subscribe(subject, msg -> {
            received.add(msg);
            latch.countDown();
        });

        // Publish
        publisherConn.publish(subject, "hello-nats".getBytes(StandardCharsets.UTF_8));
        subscriberConn.flush(Duration.ofSeconds(2));

        boolean delivered = latch.await(5, TimeUnit.SECONDS);
        assertThat(delivered).isTrue();
        assertThat(received).hasSize(1);
        assertThat(new String(received.get(0).getData(), StandardCharsets.UTF_8)).isEqualTo("hello-nats");
    }

    @Test
    void subscriberReceivesMultipleMessages() throws Exception {
        String subject = "it.multi." + UUID.randomUUID();
        int messageCount = 10;
        CountDownLatch latch = new CountDownLatch(messageCount);
        List<String> payloads = new CopyOnWriteArrayList<>();

        Dispatcher dispatcher = subscriberConn.createDispatcher();
        dispatcher.subscribe(subject, msg -> {
            payloads.add(new String(msg.getData(), StandardCharsets.UTF_8));
            latch.countDown();
        });

        for (int i = 0; i < messageCount; i++) {
            publisherConn.publish(subject, ("msg-" + i).getBytes(StandardCharsets.UTF_8));
        }
        subscriberConn.flush(Duration.ofSeconds(2));

        boolean delivered = latch.await(5, TimeUnit.SECONDS);
        assertThat(delivered).isTrue();
        assertThat(payloads).hasSize(messageCount);
        for (int i = 0; i < messageCount; i++) {
            assertThat(payloads).contains("msg-" + i);
        }
    }

    @Test
    void subscriberReceivesHeadersAlongWithPayload() throws Exception {
        String subject = "it.headers." + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> received = new CopyOnWriteArrayList<>();

        Dispatcher dispatcher = subscriberConn.createDispatcher();
        dispatcher.subscribe(subject, msg -> {
            received.add(msg);
            latch.countDown();
        });

        Headers headers = new Headers();
        headers.put("X-Trace-Id", "trace-12345");
        headers.put("X-Tenant-Id", "tenant-42");

        publisherConn.publish(subject, headers, "with-headers".getBytes(StandardCharsets.UTF_8));
        subscriberConn.flush(Duration.ofSeconds(2));

        boolean delivered = latch.await(5, TimeUnit.SECONDS);
        assertThat(delivered).isTrue();
        assertThat(received).hasSize(1);

        Message msg = received.get(0);
        assertThat(msg.getHeaders()).isNotNull();
        assertThat(msg.getHeaders().get("X-Trace-Id")).contains("trace-12345");
        assertThat(msg.getHeaders().get("X-Tenant-Id")).contains("tenant-42");
    }

    @Test
    void requestReply_shouldWork() throws Exception {
        String subject = "it.rpc." + UUID.randomUUID();

        // Register RPC handler (reply side)
        Dispatcher dispatcher = subscriberConn.createDispatcher();
        dispatcher.subscribe(subject, msg -> {
            String request = new String(msg.getData(), StandardCharsets.UTF_8);
            String reply = "echo:" + request;
            subscriberConn.publish(msg.getReplyTo(), reply.getBytes(StandardCharsets.UTF_8));
        });

        // Send request and wait for reply
        Message response = publisherConn.request(subject,
                "ping".getBytes(StandardCharsets.UTF_8), Duration.ofSeconds(5));

        assertThat(response).isNotNull();
        assertThat(new String(response.getData(), StandardCharsets.UTF_8)).isEqualTo("echo:ping");
    }

    @Test
    void multipleSubscribers_shouldBothReceiveMessage() throws Exception {
        String subject = "it.fanout." + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(2);
        List<String> sub1Received = new CopyOnWriteArrayList<>();
        List<String> sub2Received = new CopyOnWriteArrayList<>();

        // Subscriber 1
        Dispatcher d1 = subscriberConn.createDispatcher();
        d1.subscribe(subject, msg -> {
            sub1Received.add(new String(msg.getData(), StandardCharsets.UTF_8));
            latch.countDown();
        });

        // Subscriber 2 (separate connection)
        Connection sub2Conn = Nats.connect(NatsIntegrationTestSupport.getInstance().connectionUrl());
        try {
            Dispatcher d2 = sub2Conn.createDispatcher();
            d2.subscribe(subject, msg -> {
                sub2Received.add(new String(msg.getData(), StandardCharsets.UTF_8));
                latch.countDown();
            });

            publisherConn.publish(subject, "broadcast".getBytes(StandardCharsets.UTF_8));
            publisherConn.flush(Duration.ofSeconds(2));

            boolean delivered = latch.await(5, TimeUnit.SECONDS);
            assertThat(delivered).isTrue();
            assertThat(sub1Received).containsExactly("broadcast");
            assertThat(sub2Received).containsExactly("broadcast");
        } finally {
            sub2Conn.close();
        }
    }

    @Test
    void wildcardSubscription_shouldReceiveFromMatchingSubjects() throws Exception {
        String prefix = "it.wild." + UUID.randomUUID();
        String topic1 = prefix + ".sensor.1";
        String topic2 = prefix + ".sensor.2";
        String unrelated = "other." + UUID.randomUUID();

        CountDownLatch latch = new CountDownLatch(2);
        List<String> subjects = new CopyOnWriteArrayList<>();

        Dispatcher dispatcher = subscriberConn.createDispatcher();
        dispatcher.subscribe(prefix + ".>", msg -> {
            subjects.add(msg.getSubject());
            latch.countDown();
        });

        publisherConn.publish(topic1, "s1".getBytes(StandardCharsets.UTF_8));
        publisherConn.publish(topic2, "s2".getBytes(StandardCharsets.UTF_8));
        publisherConn.publish(unrelated, "other".getBytes(StandardCharsets.UTF_8));
        publisherConn.flush(Duration.ofSeconds(2));

        boolean delivered = latch.await(5, TimeUnit.SECONDS);
        assertThat(delivered).isTrue();
        assertThat(subjects).containsExactlyInAnyOrder(topic1, topic2);
    }
}
