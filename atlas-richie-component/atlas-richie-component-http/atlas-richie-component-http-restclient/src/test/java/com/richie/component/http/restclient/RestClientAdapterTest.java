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
package com.richie.component.http.restclient;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.http.restclient.support.LocalHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientAdapterTest {

    private LocalHttpServer server;
    private RestClientAdapter client;

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalHttpServer();
        client = new RestClientAdapter(RestClient.builder().build());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void executeReturnsBytesAndTypedBody() throws Exception {
        server.setHandler((exchange, body) -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                assertThat(new String(body, StandardCharsets.UTF_8)).contains("demo");
            }
            server.respondJson(exchange, 200, "{\"name\":\"rest\"}");
        });

        HttpResponse raw = client.post(server.url("/users"), Map.of("name", "demo")).execute();
        EchoDto dto = client.post(server.url("/users"), Map.of("name", "demo")).execute(EchoDto.class);
        Map<String, String> map = client.get(server.url("/users"))
                .execute(new TypeReference<Map<String, String>>() {
                });

        assertThat(raw.statusCode()).isEqualTo(200);
        assertThat(new String(raw.body(), StandardCharsets.UTF_8)).contains("rest");
        assertThat(dto.name()).isEqualTo("rest");
        assertThat(map).containsEntry("name", "rest");
    }

    @Test
    void asyncAndFutureRespectTimeout() throws Exception {
        server.setHandler((exchange, body) -> server.respondJson(exchange, 200, "{\"name\":\"async\"}"));

        AtomicReference<String> asyncValue = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        client.get(server.url("/async")).async(new AsyncCallback<EchoDto>() {
            @Override
            public void onResponse(HttpResponse response, EchoDto data) {
                asyncValue.set(data.name());
                latch.countDown();
            }

            @Override
            public void onFailure(java.io.IOException exception) {
                throw new RuntimeException(exception);
            }
        }, EchoDto.class);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(asyncValue.get()).isEqualTo("async");
    }

    @Test
    void futureRespectsTimeout() throws Exception {
        server.setHandler((exchange, body) -> server.respondJson(exchange, 200, "{\"name\":\"async\"}"));

        EchoDto dto = client.get(server.url("/future"))
                .timeout(Duration.ofSeconds(2))
                .future(EchoDto.class)
                .get(3, TimeUnit.SECONDS);

        assertThat(dto.name()).isEqualTo("async");
    }

    @Test
    void factoryMethodsCoverAllVerbs() {
        server.setHandler((exchange, body) -> {
            try {
                server.respondJson(exchange, 200, "{\"name\":\"rest\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(client.get(server.url("/get")).execute().statusCode()).isEqualTo(200);
        assertThat(client.post(server.url("/post"), Map.of("a", 1)).execute().statusCode()).isEqualTo(200);
        assertThat(client.put(server.url("/put"), "x").execute().statusCode()).isEqualTo(200);
        assertThat(client.delete(server.url("/delete")).execute().statusCode()).isEqualTo(200);
        assertThat(client.delete(server.url("/delete-body"), "x").execute().statusCode()).isEqualTo(200);
    }

    @Test
    void executeWithTypeReferenceAndAsyncVariant() throws Exception {
        server.setHandler((exchange, body) -> {
            try {
                server.respondJson(exchange, 200, "{\"name\":\"typed\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        Map<String, String> typed = client.get(server.url("/typed"))
                .execute(new TypeReference<Map<String, String>>() {
                });
        assertThat(typed).containsEntry("name", "typed");

        CountDownLatch latch = new CountDownLatch(1);
        EchoDto[] holder = new EchoDto[1];
        client.get(server.url("/async-typed")).async(new AsyncCallback<EchoDto>() {
            @Override
            public void onResponse(HttpResponse response, EchoDto data) {
                holder[0] = data;
                latch.countDown();
            }

            @Override
            public void onFailure(java.io.IOException exception) {
                throw new RuntimeException(exception);
            }
        }, new TypeReference<EchoDto>() {
        });
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(holder[0].name()).isEqualTo("typed");

        EchoDto futureDto = client.get(server.url("/future-typed"))
                .future(new TypeReference<EchoDto>() {
                })
                .get(3, TimeUnit.SECONDS);
        assertThat(futureDto.name()).isEqualTo("typed");
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
    void futureCompletesExceptionallyOnNetworkFailure() {
        assertThatThrownBy(() -> client.get("http://127.0.0.1:1/unreachable")
                .future(EchoDto.class)
                .get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    record EchoDto(String name) {
    }
}
