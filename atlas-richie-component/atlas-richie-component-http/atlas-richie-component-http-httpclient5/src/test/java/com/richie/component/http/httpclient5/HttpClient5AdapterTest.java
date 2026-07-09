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
package com.richie.component.http.httpclient5;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.http.httpclient5.support.LocalHttpServer;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpClient5AdapterTest {

    private LocalHttpServer server;
    private HttpClient5Adapter client;

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalHttpServer();
        client = new HttpClient5Adapter(HttpClients.createDefault());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void multipartUploadAndGet() throws Exception {
        server.setHandler((exchange, body) -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                assertThat(body.length).isPositive();
                server.respondText(exchange, 200, "uploaded");
                return;
            }
            server.respondText(exchange, 200, "plain");
        });

        HttpResponse upload = client.post(server.url("/upload"))
                .multipart("file", "demo.bin", new ByteArrayInputStream("bytes".getBytes(StandardCharsets.UTF_8)))
                .execute();

        assertThat(upload.statusCode()).isEqualTo(200);
        assertThat(upload.bodyAsString()).isEqualTo("uploaded");
        assertThat(client.get(server.url("/health")).execute().bodyAsString()).isEqualTo("plain");
    }

    @Test
    void asyncCallbackReceivesTypedBody() throws Exception {
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"done\"}"));

        AtomicReference<String> value = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        client.get(server.url("/async")).async(new AsyncCallback<EchoDto>() {
            @Override
            public void onResponse(HttpResponse response, EchoDto data) {
                value.set(data.name());
                latch.countDown();
            }

            @Override
            public void onFailure(java.io.IOException exception) {
                throw new RuntimeException(exception);
            }
        }, EchoDto.class);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(value.get()).isEqualTo("done");
    }

    @Test
    void requestTimeoutIsApplied() throws Exception {
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"ok\"}"));

        EchoDto dto = client.get(server.url("/timeout"))
                .timeout(Duration.ofSeconds(2))
                .future(EchoDto.class)
                .get(3, TimeUnit.SECONDS);

        assertThat(dto.name()).isEqualTo("ok");
    }

    @Test
    void factoryMethodsAndAsyncTypeReference() throws Exception {
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"hc5\"}"));

        assertThat(client.get(server.url("/get")).execute().statusCode()).isEqualTo(200);
        assertThat(client.post(server.url("/post"), Map.of("a", 1)).execute().statusCode()).isEqualTo(200);
        assertThat(client.put(server.url("/put"), "x").execute().statusCode()).isEqualTo(200);
        assertThat(client.delete(server.url("/delete")).execute().statusCode()).isEqualTo(200);
        assertThat(client.delete(server.url("/delete-body"), "gone").execute().statusCode()).isEqualTo(200);

        CountDownLatch latch = new CountDownLatch(1);
        Map<String, String>[] holder = new Map[1];
        client.get(server.url("/async-ref")).async(new AsyncCallback<Map<String, String>>() {
            @Override
            public void onResponse(HttpResponse response, Map<String, String> data) {
                holder[0] = data;
                latch.countDown();
            }

            @Override
            public void onFailure(java.io.IOException exception) {
                throw new RuntimeException(exception);
            }
        }, new TypeReference<Map<String, String>>() {
        });
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(holder[0]).containsEntry("name", "hc5");
    }

    @Test
    void asyncNotifiesFailureOnNetworkError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        IOException[] error = new IOException[1];
        client.get("http://127.0.0.1:1/unreachable").async(new AsyncCallback<EchoDto>() {
            @Override
            public void onResponse(HttpResponse response, EchoDto data) {
            }

            @Override
            public void onFailure(IOException exception) {
                error[0] = exception;
                latch.countDown();
            }
        }, EchoDto.class);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(error[0]).isNotNull();
    }

    @Test
    void executeWrapsNetworkFailure() {
        assertThatThrownBy(() -> client.get("http://127.0.0.1:1/unreachable").execute())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP request failed");
    }

    @Test
    void getWithQueryParamsAndHeadersBuildsUri() throws Exception {
        server.setHandler((exchange, body) -> {
            assertThat(exchange.getRequestURI().getQuery()).contains("page=2");
            assertThat(exchange.getRequestHeaders().getFirst("X-Trace")).isEqualTo("t1");
            server.respondText(exchange, 200, "{\"name\":\"paged\"}");
        });

        EchoDto dto = client.get(server.url("/items"))
                .param("page", "2")
                .header("X-Trace", "t1")
                .execute(EchoDto.class);

        assertThat(dto.name()).isEqualTo("paged");
    }

    @Test
    void postWithoutBodyUsesEmptyEntity() throws Exception {
        server.setHandler((exchange, body) -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            server.respondText(exchange, 200, "accepted");
        });

        assertThat(client.post(server.url("/noop")).execute().bodyAsString()).isEqualTo("accepted");
    }

    @Test
    void executeAndFutureWithTypeReference() throws Exception {
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"ref\"}"));

        Map<String, String> body = client.get(server.url("/ref"))
                .execute(new TypeReference<Map<String, String>>() {
                });
        Map<String, String> futureBody = client.get(server.url("/ref-future"))
                .future(new TypeReference<Map<String, String>>() {
                })
                .get(3, TimeUnit.SECONDS);

        assertThat(body).containsEntry("name", "ref");
        assertThat(futureBody).containsEntry("name", "ref");
    }

    record EchoDto(String name) {
    }
}
