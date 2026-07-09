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
package com.richie.component.nats.integration;

import com.richie.component.nats.support.NatsIntegrationTestSupport;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.FetchConsumer;
import io.nats.client.JetStream;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.StreamContext;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JetStream 端到集成测试 — 使用 Testcontainers 启动带 JetStream 的 NATS 容器。
 * <p>
 * 验证 JetStream 持久化消息的发布和拉取（pull consumer fetch）：
 * <ul>
 *     <li>Stream 创建</li>
 *     <li>Consumer 创建</li>
 *     <li>消息发布（PublishAck 确认）</li>
 *     <li>Pull Consumer fetch 拉取消息</li>
 *     <li>消息 ACK 确认</li>
 * </ul>
 *
 * @author richie696
 */
@EnabledIf("com.richie.component.nats.support.NatsIntegrationTestSupport#isEnabled")
class JetStreamBusEndToEndIT {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        NatsIntegrationTestSupport support = NatsIntegrationTestSupport.getInstance();
        conn = Nats.connect(support.connectionUrl());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null && conn.getStatus() == Connection.Status.CONNECTED) {
            conn.close();
        }
    }

    @Test
    void shouldCreateStreamAndPublishMessage() throws Exception {
        String streamName = "IT_STREAM_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String subject = streamName + ".test";

        // Create Stream
        JetStreamManagement jsm = conn.jetStreamManagement();
        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subject)
                .retentionPolicy(RetentionPolicy.Limits)
                .storageType(StorageType.Memory)
                .maxMessages(1000)
                .build();
        jsm.addStream(streamConfig);

        try {
            // Publish with JetStream
            JetStream js = conn.jetStream();
            byte[] data = "jetstream-hello".getBytes(StandardCharsets.UTF_8);
            PublishAck ack = js.publish(subject, data);

            assertThat(ack).isNotNull();
            assertThat(ack.getStream()).isEqualTo(streamName);
            assertThat(ack.getSeqno()).isGreaterThan(0);
            assertThat(ack.hasError()).isFalse();
        } finally {
            jsm.deleteStream(streamName);
        }
    }

    @Test
    void shouldPublishAndFetchWithPullConsumer() throws Exception {
        String streamName = "IT_FETCH_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String subject = streamName + ".events";
        String consumerName = "it-consumer";

        // Create Stream
        JetStreamManagement jsm = conn.jetStreamManagement();
        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subject)
                .retentionPolicy(RetentionPolicy.Limits)
                .storageType(StorageType.Memory)
                .build();
        jsm.addStream(streamConfig);

        try {
            // Create Consumer
            ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                    .name(consumerName)
                    .ackPolicy(AckPolicy.Explicit)
                    .deliverPolicy(DeliverPolicy.All)
                    .maxPullWaiting(256)
                    .build();
            jsm.addOrUpdateConsumer(streamName, consumerConfig);

            // Publish multiple messages
            JetStream js = conn.jetStream();
            int messageCount = 5;
            for (int i = 0; i < messageCount; i++) {
                Headers headers = new Headers();
                headers.put("X-Seq", String.valueOf(i));
                PublishAck ack = js.publish(subject, headers,
                        ("event-" + i).getBytes(StandardCharsets.UTF_8));
                assertThat(ack.hasError()).isFalse();
            }

            // Fetch with Pull Consumer
            StreamContext streamCtx = conn.getStreamContext(streamName);
            ConsumerContext consumerCtx = streamCtx.getConsumerContext(consumerName);
            FetchConsumer fetchConsumer = consumerCtx.fetchMessages(messageCount);

            int received = 0;
            Message msg;
            while ((msg = fetchConsumer.nextMessage()) != null) {
                String payload = new String(msg.getData(), StandardCharsets.UTF_8);
                assertThat(payload).startsWith("event-");
                msg.ack();
                received++;
            }

            assertThat(received).isEqualTo(messageCount);
        } finally {
            jsm.deleteStream(streamName);
        }
    }

    @Test
    void shouldPublishWithHeadersAndFetchWithHeaders() throws Exception {
        String streamName = "IT_HDR_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        String subject = streamName + ".orders";
        String consumerName = "it-hdr-consumer";

        JetStreamManagement jsm = conn.jetStreamManagement();
        StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subject)
                .storageType(StorageType.Memory)
                .build();
        jsm.addStream(streamConfig);

        try {
            ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                    .name(consumerName)
                    .ackPolicy(AckPolicy.Explicit)
                    .build();
            jsm.addOrUpdateConsumer(streamName, consumerConfig);

            // Publish with headers
            JetStream js = conn.jetStream();
            Headers headers = new Headers();
            headers.put("X-Trace-Id", "trace-abc-123");
            headers.put("X-Tenant-Id", "tenant-42");
            headers.put("X-Order-Id", "order-001");

            PublishAck ack = js.publish(subject, headers,
                    "order-payload".getBytes(StandardCharsets.UTF_8));
            assertThat(ack.hasError()).isFalse();

            // Fetch and verify headers
            StreamContext streamCtx = conn.getStreamContext(streamName);
            ConsumerContext consumerCtx = streamCtx.getConsumerContext(consumerName);
            FetchConsumer fetchConsumer = consumerCtx.fetchMessages(1);

            Message msg;
            while ((msg = fetchConsumer.nextMessage()) != null) {
                assertThat(msg.getHeaders()).isNotNull();
                assertThat(msg.getHeaders().get("X-Trace-Id")).contains("trace-abc-123");
                assertThat(msg.getHeaders().get("X-Tenant-Id")).contains("tenant-42");
                assertThat(msg.getHeaders().get("X-Order-Id")).contains("order-001");
                assertThat(new String(msg.getData(), StandardCharsets.UTF_8)).isEqualTo("order-payload");
                msg.ack();
            }
        } finally {
            jsm.deleteStream(streamName);
        }
    }
}
