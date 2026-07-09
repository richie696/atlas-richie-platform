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
package com.richie.component.http.jdk;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.http.jdk.support.LocalHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JdkHttpAdapterTest {

    private LocalHttpServer server;
    private JdkHttpAdapter client;

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalHttpServer();
        client = new JdkHttpAdapter(java.net.http.HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void getWithParamsAndHeaders() throws Exception {
        server.setHandler((exchange, body) -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getQuery()).contains("page=1");
            assertThat(exchange.getRequestHeaders().getFirst("X-Trace")).isEqualTo("abc");
            server.respondJson(exchange, 200, "{\"name\":\"jdk\"}");
        });

        EchoDto dto = client.get(server.url("/users"))
                .param("page", "1")
                .header("X-Trace", "abc")
                .execute(EchoDto.class);

        assertThat(dto.name()).isEqualTo("jdk");
    }

    @Test
    void postSerializesJsonBody() throws Exception {
        server.setHandler((exchange, body) -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(new String(body, StandardCharsets.UTF_8)).contains("hello");
            server.respondJson(exchange, 201, "{\"name\":\"created\"}");
        });

        HttpResponse response = client.post(server.url("/items"), Map.of("msg", "hello")).asJson().execute();

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.bodyAs(EchoDto.class).name()).isEqualTo("created");
    }

    @Test
    void putAndDeleteWithBody() throws Exception {
        server.setHandler((exchange, body) -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                assertThat(new String(body, StandardCharsets.UTF_8)).contains("update");
                server.respondJson(exchange, 200, "{\"name\":\"updated\"}");
            } else {
                assertThat(new String(body, StandardCharsets.UTF_8)).contains("remove");
                server.respondJson(exchange, 200, "{\"name\":\"deleted\"}");
            }
        });

        EchoDto updated = client.put(server.url("/items/1"), Map.of("msg", "update")).execute(EchoDto.class);
        EchoDto deleted = client.delete(server.url("/items/1"), Map.of("msg", "remove")).execute(EchoDto.class);

        assertThat(updated.name()).isEqualTo("updated");
        assertThat(deleted.name()).isEqualTo("deleted");
    }

    @Test
    void futureWithTypeReference() throws Exception {
        server.setHandler((exchange, body) -> server.respondJson(exchange, 200, "{\"name\":\"typed\"}"));

        Map<String, String> map = client.get(server.url("/typed")).future(new TypeReference<Map<String, String>>() {
        }).get(3, TimeUnit.SECONDS);

        assertThat(map).containsEntry("name", "typed");
    }

    @Test
    void futureReturnsBody() throws Exception {
        server.setHandler((exchange, body) -> server.respondJson(exchange, 200, "{\"name\":\"future\"}"));

        EchoDto dto = client.get(server.url("/future")).future(EchoDto.class)
                .get(3, TimeUnit.SECONDS);

        assertThat(dto.name()).isEqualTo("future");
    }

    @Test
    void asyncCallbackReturnsBody() throws Exception {
        server.setHandler((exchange, body) -> server.respondJson(exchange, 200, "{\"name\":\"async\"}"));

        AtomicReference<String> asyncValue = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        client.get(server.url("/async")).async(new AsyncCallback<>() {
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
    void putDeleteAndTypeReference() throws Exception {
        server.setHandler((exchange, body) -> {
            switch (exchange.getRequestMethod()) {
                case "PUT" -> server.respondJson(exchange, 200, "{\"name\":\"put\"}");
                case "DELETE" -> server.respondJson(exchange, 200, "{\"name\":\"delete\"}");
                default -> server.respondJson(exchange, 200, "{\"name\":\"default\"}");
            }
        });

        assertThat(client.put(server.url("/put"), Map.of("a", 1)).execute(EchoDto.class).name()).isEqualTo("put");
        assertThat(client.delete(server.url("/delete"), "body").execute(EchoDto.class).name()).isEqualTo("delete");
        assertThat(client.delete(server.url("/delete-empty")).execute(EchoDto.class).name()).isEqualTo("delete");
        assertThat(client.get(server.url("/typed")).execute(new tools.jackson.core.type.TypeReference<Map<String, String>>() {
        })).containsKey("name");
    }

    @Test
    void asyncWithTypeReference() throws Exception {
        server.setHandler((exchange, body) -> server.respondJson(exchange, 200, "{\"name\":\"async-ref\"}"));

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
        assertThat(holder[0]).containsEntry("name", "async-ref");
    }

    record EchoDto(String name) {
    }
}
