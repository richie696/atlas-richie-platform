package com.richie.component.nats.integration;

import com.richie.component.nats.bus.NatsBus;
import com.richie.component.nats.config.NatsAutoConfiguration;
import com.richie.component.nats.config.NatsProperties;
import com.richie.component.nats.connection.NatsConnectionManager;
import com.richie.component.nats.exception.NatsException;
import com.richie.component.nats.pipeline.NatsSubscriberFactory;
import com.richie.component.nats.strategy.DefaultNatsErrorStrategy;
import com.richie.component.nats.strategy.DefaultNatsHeaderInjector;
import com.richie.component.nats.strategy.NatsMessageSerializer;
import com.richie.component.nats.strategy.OpenTelemetryNatsTracingSupport;
import com.richie.component.nats.support.NatsIntegrationTestSupport;
import com.richie.component.nats.support.TestEvent;
import io.nats.client.Subscription;
import io.nats.client.impl.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NatsBus 门面端到端集成测试 — 基于 {@code @Autowired NatsBus} 验证发布/订阅全链路。
 *
 * <p>使用 Testcontainers 启动真实 NATS 容器，验证核心场景：</p>
 * <ul>
 *   <li>单消息发布/订阅 + POJO 反序列化</li>
 *   <li>多消息批量发布</li>
 *   <li>Header 注入（nats-message-id / nats-send-time）</li>
 *   <li>Queue Group 负载均衡</li>
 *   <li>通配符订阅（{@code >}）</li>
 *   <li>发布失败时抛出 {@link NatsException}</li>
 *   <li>Request-Reply RPC 端到端</li>
 * </ul>
 *
 * @author richie696
 */
@SpringBootTest(classes = NatsAutoConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIf("com.richie.component.nats.support.NatsIntegrationTestSupport#isEnabled")
class NatsBusFacadeIT {

    @DynamicPropertySource
    static void natsProperties(DynamicPropertyRegistry registry) {
        NatsIntegrationTestSupport.getInstance().registerNatsProperties(registry);
    }

    @Autowired
    private NatsBus natsBus;

    @Autowired
    private NatsMessageSerializer serializer;

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    @AfterEach
    void cleanup() {
        subscriptions.forEach(sub -> {
            try {
                sub.unsubscribe();
            } catch (Exception ignored) {
                // subscription 可能已被 dispatcher 释放,忽略即可
            }
        });
        subscriptions.clear();
    }

    @Test
    void subscriberReceivesPublishedMessage() throws Exception {
        String subject = "bus.simple." + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<TestEvent> received = new AtomicReference<>();
        AtomicReference<Headers> receivedHeaders = new AtomicReference<>();

        subscriptions.add(natsBus.subscribe(subject, msg -> {
            received.set(serializer.deserialize(msg.getData(), TestEvent.class));
            receivedHeaders.set(msg.getHeaders());
            latch.countDown();
        }));

        natsBus.publish(subject, new TestEvent("hello-bus"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get().content()).isEqualTo("hello-bus");
        // nats-message-id 由 NatsBus.publish 注入
        assertThat(receivedHeaders.get().get("nats-message-id")).isNotNull().hasSize(1);
    }

    @Test
    void subscriberReceivesMultipleMessages() throws Exception {
        String subject = "bus.multi." + UUID.randomUUID();
        int count = 10;
        CountDownLatch latch = new CountDownLatch(count);
        List<String> contents = new CopyOnWriteArrayList<>();

        subscriptions.add(natsBus.subscribe(subject, msg -> {
            TestEvent ev = serializer.deserialize(msg.getData(), TestEvent.class);
            contents.add(ev.content());
            latch.countDown();
        }));

        for (int i = 0; i < count; i++) {
            natsBus.publish(subject, new TestEvent("msg-" + i));
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(contents).hasSize(count);
        for (int i = 0; i < count; i++) {
            assertThat(contents).contains("msg-" + i);
        }
    }

    @Test
    void publishInjectsWhitelistedHeaders() throws Exception {
        String subject = "bus.headers." + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Headers> receivedHeaders = new AtomicReference<>();
        AtomicReference<String> receivedTenant = new AtomicReference<>();

        subscriptions.add(natsBus.subscribe(subject, msg -> {
            receivedHeaders.set(msg.getHeaders());
            receivedTenant.set(com.richie.context.common.api.HeaderContextHolder.getHeader("x-tenant-id"));
            latch.countDown();
        }));

        com.richie.context.common.api.HeaderContextHolder.setHeader("x-tenant-id", "tenant-42");
        try {
            natsBus.publish(subject, new TestEvent("with-headers"));

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            Headers headers = receivedHeaders.get();
            assertThat(headers).isNotNull();
            assertThat(headers.get("nats-message-id")).isNotNull().hasSize(1);
            assertThat(headers.get("x-tenant-id")).contains("tenant-42");
            assertThat(receivedTenant.get()).isEqualTo("tenant-42");
        } finally {
            com.richie.context.common.api.HeaderContextHolder.removeContext();
        }
    }

    @Test
    void queueGroupDistributesLoadAcrossSubscribers() throws Exception {
        String subject = "bus.queue." + UUID.randomUUID();
        String queueGroup = "workers";
        int messageCount = 6;

        CountDownLatch totalLatch = new CountDownLatch(messageCount);
        CopyOnWriteArrayList<String> sub1 = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<String> sub2 = new CopyOnWriteArrayList<>();

        subscriptions.add(natsBus.subscribe(subject, queueGroup, msg -> {
            TestEvent ev = serializer.deserialize(msg.getData(), TestEvent.class);
            sub1.add(ev.content());
            totalLatch.countDown();
        }));
        subscriptions.add(natsBus.subscribe(subject, queueGroup, msg -> {
            TestEvent ev = serializer.deserialize(msg.getData(), TestEvent.class);
            sub2.add(ev.content());
            totalLatch.countDown();
        }));

        for (int i = 0; i < messageCount; i++) {
            natsBus.publish(subject, new TestEvent("job-" + i));
        }

        assertThat(totalLatch.await(5, TimeUnit.SECONDS)).isTrue();
        // 同一消息不会同时投递给两个 Queue Group 成员
        assertThat(sub1).doesNotContainAnyElementsOf(sub2);
        // 合计收到 messageCount 条
        assertThat(sub1.size() + sub2.size()).isEqualTo(messageCount);
    }

    @Test
    void wildcardSubscriptionReceivesMatchingSubjects() throws Exception {
        String prefix = "bus.wild." + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(2);
        CopyOnWriteArrayList<String> receivedSubjects = new CopyOnWriteArrayList<>();

        subscriptions.add(natsBus.subscribe(prefix + ".>", msg -> {
            receivedSubjects.add(msg.getSubject());
            latch.countDown();
        }));

        natsBus.publish(prefix + ".sensor.1", new TestEvent("s1"));
        natsBus.publish(prefix + ".sensor.2", new TestEvent("s2"));
        natsBus.publish("unrelated." + UUID.randomUUID(), new TestEvent("other"));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedSubjects).containsExactlyInAnyOrder(
                prefix + ".sensor.1", prefix + ".sensor.2");
    }

    @Test
    void publishFailsWhenServerUnreachable() {
        // 用不可达端口构造坏 Bus,触发真实连接失败路径
        NatsProperties badProps = new NatsProperties();
        badProps.setServer("nats://127.0.0.1:1");
        badProps.setEnabled(true);
        NatsConnectionManager badManager = new NatsConnectionManager(badProps);

        NatsBus badBus = new NatsBus(
                badManager,
                serializer,
                new DefaultNatsHeaderInjector(Set.of()),
                new OpenTelemetryNatsTracingSupport(false),
                new NatsSubscriberFactory(
                        new OpenTelemetryNatsTracingSupport(false), null, null, false, 0L),
                new DefaultNatsErrorStrategy(),
                badProps);

        assertThatThrownBy(() -> badBus.publish("any.subject", new TestEvent("boom")))
                .isInstanceOf(NatsException.class)
                .hasMessageContaining("Failed to publish");
    }

    @Test
    void requestReplyEndToEnd() throws Exception {
        String subject = "bus.rpc." + UUID.randomUUID();

        subscriptions.add(natsBus.subscribe(subject, msg -> {
            TestEvent req = serializer.deserialize(msg.getData(), TestEvent.class);
            natsBus.publish(msg.getReplyTo(), new TestEvent("echo:" + req.content()));
        }));

        TestEvent response = natsBus.request(
                subject, new TestEvent("ping"), TestEvent.class, Duration.ofSeconds(5));

        assertThat(response.content()).isEqualTo("echo:ping");
    }
}
