/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mqtt.integration;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.richie.component.mqtt.support.MqttIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test that verifies the MQTT client library's publish/subscribe
 * contract against a real HiveMQ CE testcontainer broker. Each test stands up a small
 * fleet of raw {@link Mqtt5AsyncClient} instances and exercises the broker path directly
 * (subscribe → publish → message arrival) without involving the component's own
 * {@code HiveMqMessageHandler} or {@code MqttEventBus} so the wiring of those layers
 * stays covered by the unit tests.
 */
@EnabledIf("com.richie.component.mqtt.support.MqttIntegrationTestSupport#isEnabled")
class MqttBrokerPubSubEndToEndIT {

    private final List<Mqtt5AsyncClient> clients = new ArrayList<>();

    @BeforeEach
    void noteBroker() {
        MqttIntegrationTestSupport support = MqttIntegrationTestSupport.getInstance();
        System.out.printf("[mqtt-e2e] broker %s:%d%n", support.brokerHost(), support.brokerPort());
    }

    @AfterEach
    void shutdownClients() {
        for (Mqtt5AsyncClient client : clients) {
            try {
                if (client.getState().isConnected()) {
                    client.disconnect();
                }
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
        clients.clear();
    }

    @Test
    void subscriberReceivesPublishedMessage_qos1() throws Exception {
        String topic = "e2e/pub-sub/" + UUID.randomUUID();
        Mqtt5Publish received = subscribeAndAwaitOne(topic, MqttQos.AT_LEAST_ONCE, () -> {
            Mqtt5AsyncClient publisher = newClient("e2e-pub");
            publisher.connect().get(5, TimeUnit.SECONDS);
            try {
                publisher.publish(Mqtt5Publish.builder()
                        .topic(topic)
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .payload("hello-e2e".getBytes(StandardCharsets.UTF_8))
                        .build()).get(5, TimeUnit.SECONDS);
            } finally {
                publisher.disconnect();
            }
        });
        assertThat(received).isNotNull();
        assertThat(new String(received.getPayloadAsBytes(), StandardCharsets.UTF_8)).isEqualTo("hello-e2e");
    }

    @Test
    void subscriberReceivesMultiplePublishedMessages() throws Exception {
        String topic = "e2e/multi/" + UUID.randomUUID();
        Mqtt5AsyncClient subscriber = newClient("e2e-sub-multi");
        subscriber.connect().get(5, TimeUnit.SECONDS);
        subscriber.subscribe(Mqtt5Subscribe.builder().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).build())
                .get(5, TimeUnit.SECONDS);

        List<Mqtt5Publish> received = new ArrayList<>();
        subscriber.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            if (publish.getTopic().toString().equals(topic)) {
                synchronized (received) {
                    received.add(publish);
                }
            }
        });

        Mqtt5AsyncClient publisher = newClient("e2e-pub-multi");
        publisher.connect().get(5, TimeUnit.SECONDS);
        try {
            for (int i = 0; i < 5; i++) {
                publisher.publish(Mqtt5Publish.builder()
                        .topic(topic)
                        .qos(MqttQos.AT_LEAST_ONCE)
                        .payload(("msg-" + i).getBytes(StandardCharsets.UTF_8))
                        .build()).get(5, TimeUnit.SECONDS);
            }
        } finally {
            publisher.disconnect();
        }

        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            synchronized (received) {
                if (received.size() >= 5) {
                    break;
                }
            }
            Thread.sleep(50L);
        }
        subscriber.disconnect();

        synchronized (received) {
            assertThat(received).hasSize(5);
            assertThat(received).extracting(p -> new String(p.getPayloadAsBytes(), StandardCharsets.UTF_8))
                    .containsExactlyInAnyOrder("msg-0", "msg-1", "msg-2", "msg-3", "msg-4");
        }
    }

    @Test
    void subscriberReceivesQos2Message() throws Exception {
        String topic = "e2e/qos2/" + UUID.randomUUID();
        Mqtt5Publish received = subscribeAndAwaitOne(topic, MqttQos.EXACTLY_ONCE, () -> {
            Mqtt5AsyncClient publisher = newClient("e2e-pub-qos2");
            publisher.connect().get(5, TimeUnit.SECONDS);
            try {
                publisher.publish(Mqtt5Publish.builder()
                        .topic(topic)
                        .qos(MqttQos.EXACTLY_ONCE)
                        .payload("exactly-once".getBytes(StandardCharsets.UTF_8))
                        .build()).get(5, TimeUnit.SECONDS);
            } finally {
                publisher.disconnect();
            }
        });
        assertThat(received).isNotNull();
        assertThat(received.getQos()).isEqualTo(MqttQos.EXACTLY_ONCE);
        assertThat(new String(received.getPayloadAsBytes(), StandardCharsets.UTF_8)).isEqualTo("exactly-once");
    }

    @Test
    void wildcardSubscriptionReceivesMessagesFromMatchingTopics() throws Exception {
        String prefix = "e2e/wild/" + UUID.randomUUID();
        String sensorTopic = prefix + "/sensor/42";
        String actuatorTopic = prefix + "/actuator/7";
        String unrelatedTopic = "e2e/other/" + UUID.randomUUID();

        Mqtt5AsyncClient subscriber = newClient("e2e-sub-wild");
        subscriber.connect().get(5, TimeUnit.SECONDS);
        subscriber.subscribe(Mqtt5Subscribe.builder()
                .topicFilter(prefix + "/+/+")
                .qos(MqttQos.AT_LEAST_ONCE)
                .build()).get(5, TimeUnit.SECONDS);

        List<String> receivedTopics = new ArrayList<>();
        subscriber.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            if (publish.getTopic().toString().startsWith(prefix)) {
                synchronized (receivedTopics) {
                    receivedTopics.add(publish.getTopic().toString());
                }
            }
        });

        Mqtt5AsyncClient publisher = newClient("e2e-pub-wild");
        publisher.connect().get(5, TimeUnit.SECONDS);
        try {
            publisher.publish(Mqtt5Publish.builder().topic(sensorTopic).qos(MqttQos.AT_LEAST_ONCE)
                    .payload("s".getBytes(StandardCharsets.UTF_8)).build()).get(5, TimeUnit.SECONDS);
            publisher.publish(Mqtt5Publish.builder().topic(actuatorTopic).qos(MqttQos.AT_LEAST_ONCE)
                    .payload("a".getBytes(StandardCharsets.UTF_8)).build()).get(5, TimeUnit.SECONDS);
            publisher.publish(Mqtt5Publish.builder().topic(unrelatedTopic).qos(MqttQos.AT_LEAST_ONCE)
                    .payload("u".getBytes(StandardCharsets.UTF_8)).build()).get(5, TimeUnit.SECONDS);
        } finally {
            publisher.disconnect();
        }

        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            synchronized (receivedTopics) {
                if (receivedTopics.size() >= 2) {
                    break;
                }
            }
            Thread.sleep(50L);
        }
        subscriber.disconnect();

        synchronized (receivedTopics) {
            assertThat(receivedTopics).containsExactlyInAnyOrder(sensorTopic, actuatorTopic);
        }
    }

    @Test
    void subscribersOnSameTopicBothReceiveMessage() throws Exception {
        String topic = "e2e/fanout/" + UUID.randomUUID();

        Mqtt5AsyncClient first = newClient("e2e-sub-fanout-1");
        first.connect().get(5, TimeUnit.SECONDS);
        first.subscribe(Mqtt5Subscribe.builder().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).build())
                .get(5, TimeUnit.SECONDS);

        Mqtt5AsyncClient second = newClient("e2e-sub-fanout-2");
        second.connect().get(5, TimeUnit.SECONDS);
        second.subscribe(Mqtt5Subscribe.builder().topicFilter(topic).qos(MqttQos.AT_LEAST_ONCE).build())
                .get(5, TimeUnit.SECONDS);

        Mqtt5Publish[] firstHolder = new Mqtt5Publish[1];
        first.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            if (publish.getTopic().toString().equals(topic) && firstHolder[0] == null) {
                firstHolder[0] = publish;
            }
        });
        Mqtt5Publish[] secondHolder = new Mqtt5Publish[1];
        second.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            if (publish.getTopic().toString().equals(topic) && secondHolder[0] == null) {
                secondHolder[0] = publish;
            }
        });

        Mqtt5AsyncClient publisher = newClient("e2e-pub-fanout");
        publisher.connect().get(5, TimeUnit.SECONDS);
        try {
            publisher.publish(Mqtt5Publish.builder()
                    .topic(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .payload("broadcast".getBytes(StandardCharsets.UTF_8))
                    .build()).get(5, TimeUnit.SECONDS);
        } finally {
            publisher.disconnect();
        }

        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline
                && (firstHolder[0] == null || secondHolder[0] == null)) {
            Thread.sleep(50L);
        }
        first.disconnect();
        second.disconnect();

        assertThat(firstHolder[0]).isNotNull();
        assertThat(secondHolder[0]).isNotNull();
        assertThat(new String(firstHolder[0].getPayloadAsBytes(), StandardCharsets.UTF_8)).isEqualTo("broadcast");
        assertThat(new String(secondHolder[0].getPayloadAsBytes(), StandardCharsets.UTF_8)).isEqualTo("broadcast");
    }

    private Mqtt5Publish subscribeAndAwaitOne(String topic, MqttQos qos, ThrowingRunnable publishAction) throws Exception {
        Mqtt5AsyncClient subscriber = newClient("e2e-sub-" + UUID.randomUUID());
        subscriber.connect().get(5, TimeUnit.SECONDS);
        subscriber.subscribe(Mqtt5Subscribe.builder().topicFilter(topic).qos(qos).build()).get(5, TimeUnit.SECONDS);

        Mqtt5Publish[] holder = new Mqtt5Publish[1];
        subscriber.publishes(MqttGlobalPublishFilter.ALL, publish -> {
            if (publish.getTopic().toString().equals(topic) && holder[0] == null) {
                holder[0] = publish;
            }
        });

        try {
            if (publishAction != null) {
                publishAction.run();
            }
            long deadline = System.currentTimeMillis() + 5_000L;
            while (System.currentTimeMillis() < deadline && holder[0] == null) {
                Thread.sleep(50L);
            }
            return holder[0];
        } finally {
            subscriber.disconnect();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private Mqtt5AsyncClient newClient(String role) {
        MqttIntegrationTestSupport support = MqttIntegrationTestSupport.getInstance();
        Mqtt5AsyncClient client = MqttClient.builder()
                .identifier(role + "-" + UUID.randomUUID())
                .serverHost(support.brokerHost())
                .serverPort(support.brokerPort())
                .useMqttVersion5()
                .buildAsync();
        clients.add(client);
        return client;
    }
}
