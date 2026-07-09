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
package com.richie.component.http.okhttp.integration;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseEvent;
import com.richie.component.http.core.SseListener;
import com.richie.component.http.okhttp.support.HttpIntegrationTest;
import com.richie.component.http.okhttp.support.LocalHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@HttpIntegrationTest
class SseIT {

    @Autowired
    private HttpClient httpClient;

    private LocalHttpServer server;
    private final List<SseConnection> openConnections = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalHttpServer();
    }

    @AfterEach
    void tearDown() {
        openConnections.forEach(SseConnection::close);
        openConnections.clear();
        server.close();
    }

    @Test
    void shouldReceiveAllEvents() throws Exception {
        server.setHandler((exchange, body) -> {
            try (OutputStream out = server.respondSse(exchange)) {
                out.write(LocalHttpServer.formatEvent("1", "message", "hello").getBytes(StandardCharsets.UTF_8));
                out.flush();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                out.write(LocalHttpServer.formatEvent("2", "update", "{\"value\":42}").getBytes(StandardCharsets.UTF_8));
                out.flush();
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                out.write(LocalHttpServer.formatEvent("3", "message", "world").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        });

        CountDownLatch latch = new CountDownLatch(3);
        CopyOnWriteArrayList<SseEvent> events = new CopyOnWriteArrayList<>();
        AtomicInteger openCount = new AtomicInteger(0);
        AtomicInteger closedCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicReference<SseConnection> connectionRef = new AtomicReference<>();

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                openCount.incrementAndGet();
                connectionRef.set(connection);
                openConnections.add(connection);
            }

            @Override
            public void onEvent(SseConnection connection, SseEvent event) {
                events.add(event);
                latch.countDown();
            }

            @Override
            public void onClosed(SseConnection connection) {
                closedCount.incrementAndGet();
            }

            @Override
            public void onFailure(SseConnection connection, Throwable cause) {
                failureCount.incrementAndGet();
            }
        };

        httpClient.sse(server.url("/events"), listener);
        assertThat(latch.await(10, SECONDS)).isTrue();

        assertThat(events).hasSize(3);
        assertThat(events.get(0).id()).isEqualTo("1");
        assertThat(events.get(0).event()).isEqualTo("message");
        assertThat(events.get(0).data()).isEqualTo("hello");
        assertThat(events.get(1).id()).isEqualTo("2");
        assertThat(events.get(1).event()).isEqualTo("update");
        assertThat(events.get(1).data()).isEqualTo("{\"value\":42}");
        assertThat(events.get(2).id()).isEqualTo("3");
        assertThat(events.get(2).event()).isEqualTo("message");
        assertThat(events.get(2).data()).isEqualTo("world");

        assertThat(openCount.get()).isEqualTo(1);
        assertThat(closedCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(0);
    }

    @Test
    void shouldCloseConnection() throws Exception {
        server.setHandler((exchange, body) -> {
            try (OutputStream out = server.respondSse(exchange)) {
                out.write(LocalHttpServer.formatEvent("1", "message", "hello").getBytes(StandardCharsets.UTF_8));
                out.flush();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                out.write(LocalHttpServer.formatEvent("2", "message", "world").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        });

        CountDownLatch openLatch = new CountDownLatch(1);
        AtomicReference<SseConnection> connectionRef = new AtomicReference<>();

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                connectionRef.set(connection);
                openConnections.add(connection);
                openLatch.countDown();
            }
        };

        SseConnection conn = httpClient.sse(server.url("/events"), listener);
        assertThat(openLatch.await(5, SECONDS)).isTrue();

        Thread.sleep(100);
        assertThat(conn.isOpen()).isTrue();

        conn.close();
        assertThat(conn.isOpen()).isFalse();

        conn.close();
    }

    @Test
    void shouldSendCustomHeaders() throws Exception {
        AtomicReference<String> receivedAuthHeader = new AtomicReference<>();
        CountDownLatch eventLatch = new CountDownLatch(1);

        server.setHandler((exchange, body) -> {
            receivedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try (OutputStream out = server.respondSse(exchange)) {
                out.write(LocalHttpServer.formatEvent("1", "message", "ok").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        });

        SseListener listener = new SseListener() {
            @Override
            public void onEvent(SseConnection connection, SseEvent event) {
                eventLatch.countDown();
            }
        };

        SseConnection conn = httpClient.sse(server.url("/events"), Map.of("Authorization", "Bearer test-token"), listener);
        openConnections.add(conn);
        assertThat(eventLatch.await(5, SECONDS)).isTrue();
        assertThat(receivedAuthHeader.get()).isEqualTo("Bearer test-token");
    }

    @Test
    void shouldExposeStatusAndHeaders() throws Exception {
        server.setHandler((exchange, body) -> {
            try (OutputStream out = server.respondSse(exchange)) {
                out.write(LocalHttpServer.formatEvent("1", "message", "test").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        });

        CountDownLatch openLatch = new CountDownLatch(1);
        AtomicReference<SseConnection> connectionRef = new AtomicReference<>();

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                connectionRef.set(connection);
                openConnections.add(connection);
                openLatch.countDown();
            }
        };

        SseConnection conn = httpClient.sse(server.url("/events"), listener);
        assertThat(openLatch.await(5, SECONDS)).isTrue();

        assertThat(conn.statusCode()).isEqualTo(200);
        Map<String, List<String>> headers = conn.headers();
        boolean hasEventStream = headers.entrySet().stream()
                .anyMatch(e -> e.getKey().equalsIgnoreCase("Content-Type")
                        && e.getValue().stream().anyMatch(v -> v.startsWith("text/event-stream")));
        assertThat(hasEventStream).isTrue();
    }
}
