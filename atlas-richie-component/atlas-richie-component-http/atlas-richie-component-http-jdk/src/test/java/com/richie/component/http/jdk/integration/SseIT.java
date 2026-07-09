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
package com.richie.component.http.jdk.integration;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseEvent;
import com.richie.component.http.core.SseListener;
import com.richie.component.http.jdk.support.HttpIntegrationTest;
import com.richie.component.http.jdk.support.LocalHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@HttpIntegrationTest
class SseIT {

    @Autowired
    private HttpClient httpClient;

    private LocalHttpServer server;
    private final CopyOnWriteArrayList<SseConnection> trackedConnections = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalHttpServer();
        server.setHandler((exchange, body) -> {
            try {
                OutputStream out = server.respondSse(exchange);
                out.write(LocalHttpServer.formatEvent("1", "message", "hello").getBytes());
                out.flush();
                Thread.sleep(50);
                out.write(LocalHttpServer.formatEvent("2", "update", "{\"value\":42}").getBytes());
                out.flush();
                Thread.sleep(100);
                out.write(LocalHttpServer.formatEvent("3", "message", "world").getBytes());
                out.flush();
                out.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @AfterEach
    void tearDown() {
        trackedConnections.forEach(conn -> {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        });
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldReceiveAllEvents() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        CopyOnWriteArrayList<SseEvent> events = new CopyOnWriteArrayList<>();
        AtomicInteger openCount = new AtomicInteger(0);
        AtomicInteger closedCount = new AtomicInteger(0);

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                openCount.incrementAndGet();
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
        };

        SseConnection conn = httpClient.sse(server.url("/"), listener);
        trackedConnections.add(conn);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
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
    }

    @Test
    void shouldCloseConnection() throws Exception {
        CountDownLatch openLatch = new CountDownLatch(1);
        AtomicBoolean opened = new AtomicBoolean(false);

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                opened.set(true);
                openLatch.countDown();
            }
        };

        SseConnection conn = httpClient.sse(server.url("/"), listener);
        trackedConnections.add(conn);

        assertThat(openLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(opened.get()).isTrue();

        Thread.sleep(200);
        assertThat(conn.isOpen()).isFalse();

        assertThatCode(conn::close).doesNotThrowAnyException();
        assertThatCode(conn::close).doesNotThrowAnyException();
    }

    @Test
    void shouldSendCustomHeaders() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        server.setHandler((exchange, body) -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            authHeader.set(auth);
            try {
                OutputStream out = server.respondSse(exchange);
                out.write(LocalHttpServer.formatEvent("data", "custom", "event").getBytes());
                out.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<SseEvent> events = new CopyOnWriteArrayList<>();

        SseListener listener = new SseListener() {
            @Override
            public void onEvent(SseConnection connection, SseEvent event) {
                events.add(event);
                latch.countDown();
            }
        };

        SseConnection conn = httpClient.sse(server.url("/"), Map.of("Authorization", "Bearer test-token-123"), listener);
        trackedConnections.add(conn);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(authHeader.get()).isEqualTo("Bearer test-token-123");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("event");
    }

    @Test
    void shouldExposeStatusAndHeaders() throws Exception {
        CountDownLatch openLatch = new CountDownLatch(1);
        AtomicReference<SseConnection> connectionRef = new AtomicReference<>();

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                connectionRef.set(connection);
                openLatch.countDown();
            }
        };

        SseConnection conn = httpClient.sse(server.url("/"), listener);
        trackedConnections.add(conn);

        assertThat(openLatch.await(5, TimeUnit.SECONDS)).isTrue();

        SseConnection connection = connectionRef.get();
        assertThat(connection.statusCode()).isEqualTo(200);
        Map<String, List<String>> headers = connection.headers();
        assertThat(headers).isNotEmpty();
        boolean hasContentType = headers.entrySet().stream()
                .anyMatch(e -> e.getKey().equalsIgnoreCase("Content-Type")
                        && e.getValue().stream().anyMatch(v -> v.contains("text/event-stream")));
        assertThat(hasContentType).isTrue();
    }
}