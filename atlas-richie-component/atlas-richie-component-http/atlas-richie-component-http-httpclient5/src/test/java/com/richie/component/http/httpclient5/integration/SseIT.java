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
package com.richie.component.http.httpclient5.integration;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseEvent;
import com.richie.component.http.core.SseListener;
import com.richie.component.http.httpclient5.support.HttpIntegrationTest;
import com.richie.component.http.httpclient5.support.LocalHttpServer;
import com.sun.net.httpserver.HttpExchange;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@HttpIntegrationTest
class SseIT {

    @Autowired
    private HttpClient httpClient;

    private LocalHttpServer server;
    private final CopyOnWriteArrayList<SseConnection> trackedConnections = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalHttpServer();
    }

    @AfterEach
    void tearDown() {
        trackedConnections.forEach(SseConnection::close);
        server.close();
    }

    @Test
    void shouldReceiveAllEvents() throws Exception {
        server.setHandler((exchange, body) -> {
            try {
                OutputStream out = server.respondSse(exchange);
                writeSseEvent(out, "1", "message", "hello");
                Thread.sleep(50);
                writeSseEvent(out, "2", "update", "{\"value\":42}");
                Thread.sleep(50);
                writeSseEvent(out, "3", "message", "world");
                exchange.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CountDownLatch latch = new CountDownLatch(3);
        List<SseEvent> events = new CopyOnWriteArrayList<>();
        AtomicInteger openCount = new AtomicInteger(0);
        AtomicInteger closedCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection conn) {
                openCount.incrementAndGet();
                trackedConnections.add(conn);
            }

            @Override
            public void onEvent(SseConnection conn, SseEvent event) {
                events.add(event);
                latch.countDown();
            }

            @Override
            public void onClosed(SseConnection conn) {
                closedCount.incrementAndGet();
            }

            @Override
            public void onFailure(SseConnection conn, Throwable cause) {
                failureCount.incrementAndGet();
            }
        };

        httpClient.sse(server.url("/"), Map.of(), listener);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        await().untilAsserted(() -> assertThat(events).hasSize(3));

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
            try {
                OutputStream out = server.respondSse(exchange);
                writeSseEvent(out, "1", "message", "hello");
                Thread.sleep(500);
                writeSseEvent(out, "2", "message", "world");
                exchange.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SseConnection> connRef = new AtomicReference<>();

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection conn) {
                trackedConnections.add(conn);
                connRef.set(conn);
                latch.countDown();
            }

            @Override
            public void onEvent(SseConnection conn, SseEvent event) {
            }

            @Override
            public void onFailure(SseConnection conn, Throwable cause) {
            }
        };

        httpClient.sse(server.url("/"), Map.of(), listener);
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        SseConnection conn = connRef.get();
        Thread.sleep(100);
        assertThat(conn.isOpen()).isTrue();

        conn.close();
        assertThat(conn.isOpen()).isFalse();

        conn.close();
    }

    @Test
    void shouldSendCustomHeaders() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        server.setHandler((exchange, body) -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            authHeader.set(auth);
            OutputStream out = server.respondSse(exchange);
            writeSseEvent(out, "1", "message", "authorized");
            exchange.close();
            latch.countDown();
        });

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection conn) {
                trackedConnections.add(conn);
            }

            @Override
            public void onEvent(SseConnection conn, SseEvent event) {
            }
        };

        httpClient.sse(server.url("/"), Map.of("Authorization", "Bearer test-token"), listener);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(authHeader.get()).isEqualTo("Bearer test-token");
    }

    @Test
    void shouldExposeStatusAndHeaders() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger statusCode = new AtomicInteger(-1);

        server.setHandler((exchange, body) -> {
            OutputStream out = server.respondSse(exchange);
            writeSseEvent(out, "1", "message", "hello");
            exchange.close();
            latch.countDown();
        });

        SseListener listener = new SseListener() {
            @Override
            public void onOpen(SseConnection conn) {
                trackedConnections.add(conn);
                statusCode.set(conn.statusCode());
            }

            @Override
            public void onEvent(SseConnection conn, SseEvent event) {
            }
        };

        httpClient.sse(server.url("/"), Map.of(), listener);

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(statusCode.get()).isEqualTo(200);
    }

    private void writeSseEvent(OutputStream out, String id, String event, String data) throws IOException {
        out.write(("id:" + id + "\n").getBytes());
        out.write(("event:" + event + "\n").getBytes());
        out.write(("data:" + data + "\n").getBytes());
        out.write("\n".getBytes());
        out.flush();
    }
}
