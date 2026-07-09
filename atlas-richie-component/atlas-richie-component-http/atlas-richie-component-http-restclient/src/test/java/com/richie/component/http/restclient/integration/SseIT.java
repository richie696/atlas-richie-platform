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
package com.richie.component.http.restclient.integration;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseEvent;
import com.richie.component.http.core.SseListener;
import com.richie.component.http.restclient.support.LocalHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@org.springframework.boot.test.context.SpringBootTest(
        classes = com.richie.component.http.restclient.support.HttpIntegrationTestConfiguration.class,
        properties = "platform.component.http.provider=rest_client"
)
class SseIT {

    @Autowired
    private HttpClient httpClient;

    private LocalHttpServer server;
    private final CopyOnWriteArrayList<SseConnection> trackedConnections = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalHttpServer();
        server.setHandler((exchange, body) -> {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream;charset=utf-8");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, 0);
            try {
                var out = exchange.getResponseBody();
                out.write(formatEvent("1", "message", "hello").getBytes());
                out.flush();
                Thread.sleep(50);
                out.write(formatEvent("2", "update", "{\"value\":42}").getBytes());
                out.flush();
                Thread.sleep(50);
                out.write(formatEvent("3", "message", "world").getBytes());
                out.flush();
                out.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @AfterEach
    void tearDown() {
        trackedConnections.forEach(SseIT::safeClose);
        server.close();
    }

    private static void safeClose(SseConnection conn) {
        try {
            conn.close();
        } catch (Exception ignored) {
        }
    }

    private static String formatEvent(String id, String event, String data) {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("id:").append(id).append("\n");
        }
        if (event != null) {
            sb.append("event:").append(event).append("\n");
        }
        if (data != null) {
            sb.append("data:").append(data).append("\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    @Test
    void shouldReceiveAllEvents() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        List<SseEvent> events = new CopyOnWriteArrayList<>();
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

        SseConnection connection = httpClient.sse(server.url("/sse"), listener);
        trackedConnections.add(connection);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(events).hasSize(3);
        assertThat(events.getFirst().id()).isEqualTo("1");
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
        AtomicReference<SseConnection> connectionRef = new AtomicReference<>();

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection connection) {
                connectionRef.set(connection);
                openLatch.countDown();
            }
        };

        SseConnection connection = httpClient.sse(server.url("/sse"), listener);
        trackedConnections.add(connection);

        assertThat(openLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(connectionRef.get().isOpen()).isTrue();

        Thread.sleep(100);
        connectionRef.get().close();
        assertThat(connectionRef.get().isOpen()).isFalse();

        connectionRef.get().close();
    }

    @Test
    void shouldSendCustomHeaders() throws Exception {
        server.setHandler((exchange, body) -> {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            assertThat(authHeader).isEqualTo("Bearer test-token-123");
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream;charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try {
                var out = exchange.getResponseBody();
                out.write("data:ok\n\n".getBytes());
                out.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CountDownLatch latch = new CountDownLatch(1);
        SseListener listener = new SseListener() {
            @Override
            public void onEvent(SseConnection connection, SseEvent event) {
                latch.countDown();
            }
        };

        SseConnection connection = httpClient.sse(server.url("/sse"), Map.of("Authorization", "Bearer test-token-123"), listener);
        trackedConnections.add(connection);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
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

        SseConnection connection = httpClient.sse(server.url("/sse"), listener);
        trackedConnections.add(connection);

        assertThat(openLatch.await(5, TimeUnit.SECONDS)).isTrue();
        SseConnection conn = connectionRef.get();
        assertThat(conn.statusCode()).isEqualTo(200);
        assertThat(conn.headers()).isNotEmpty();
        // HTTP header names are case-insensitive (RFC 7230 §3.2);
        // Spring's HttpHeaders normalises names to lowercase, so look up case-insensitively.
        String contentType = conn.headers().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("Content-Type"))
                .flatMap(e -> e.getValue().stream())
                .findFirst()
                .orElse("");
        assertThat(contentType).contains("text/event-stream");
    }
}
