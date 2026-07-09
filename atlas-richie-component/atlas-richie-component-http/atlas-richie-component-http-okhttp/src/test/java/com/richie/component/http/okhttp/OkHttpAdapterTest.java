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
package com.richie.component.http.okhttp;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.http.okhttp.support.LocalHttpServer;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OkHttpAdapterTest {

    private LocalHttpServer server;
    private OkHttpAdapter client;

    @BeforeEach
    void setUp() throws IOException {
        server = new LocalHttpServer();
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "ok", "text/plain"));
        client = new OkHttpAdapter(new OkHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void putAndDeleteWithBody() throws Exception {
        server.setHandler((exchange, body) -> {
            if ("PUT".equals(exchange.getRequestMethod())) {
                assertThat(new String(body, StandardCharsets.UTF_8)).contains("xml");
                server.respondText(exchange, 200, "<ok/>", "application/xml; charset=utf-8");
                return;
            }
            if ("DELETE".equals(exchange.getRequestMethod())) {
                assertThat(body).isNotEmpty();
                server.respondText(exchange, 204, "", "text/plain");
                return;
            }
            server.respondText(exchange, 404, "missing", "text/plain");
        });

        HttpResponse putResponse = client.put(server.url("/resource"), "<xml/>").asXml().execute();
        HttpResponse deleteResponse = client.delete(server.url("/resource"), "gone").execute();

        assertThat(putResponse.statusCode()).isEqualTo(200);
        assertThat(putResponse.bodyAsString()).contains("ok");
        assertThat(deleteResponse.statusCode()).isEqualTo(204);
    }

    @Test
    void futureUsesPerRequestTimeoutClient() throws Exception {
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"ok\"}", "application/json"));

        EchoDto dto = client.get(server.url("/future"))
                .timeout(Duration.ofSeconds(5))
                .future(EchoDto.class)
                .get(3, TimeUnit.SECONDS);

        assertThat(dto.name()).isEqualTo("ok");
    }

    @Test
    void postWithoutBodyUsesEmptyRequestBody() throws Exception {
        server.setHandler((exchange, body) -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            server.respondText(exchange, 200, "accepted", "text/plain");
        });

        assertThat(client.post(server.url("/noop")).execute().bodyAsString()).isEqualTo("accepted");
    }

    @Test
    void asyncAndFutureSupportTypeReference() throws Exception {
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"typed\"}", "application/json"));

        CountDownLatch latch = new CountDownLatch(1);
        Map<String, String>[] asyncHolder = new Map[1];
        client.get(server.url("/typed")).async(new com.richie.component.http.core.AsyncCallback<Map<String, String>>() {
            @Override
            public void onResponse(HttpResponse response, Map<String, String> data) {
                asyncHolder[0] = data;
                latch.countDown();
            }

            @Override
            public void onFailure(java.io.IOException exception) {
                throw new RuntimeException(exception);
            }
        }, new TypeReference<Map<String, String>>() {
        });

        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"typed\"}", "application/json"));
        Map<String, String> futureValue = client.get(server.url("/typed-future"))
                .future(new TypeReference<Map<String, String>>() {
                })
                .get(3, TimeUnit.SECONDS);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(asyncHolder[0]).containsEntry("name", "typed");
        assertThat(futureValue).containsEntry("name", "typed");
    }

    @Test
    void factoryMethodsBindAdapter() {
        assertThat(client.get(server.url("/get")).execute().statusCode()).isEqualTo(200);
        assertThat(client.post(server.url("/post"), Map.of("a", 1)).execute().statusCode()).isEqualTo(200);
        assertThat(client.post(server.url("/post-empty")).execute().statusCode()).isEqualTo(200);
        assertThat(client.put(server.url("/put"), "x").execute().statusCode()).isEqualTo(200);
        assertThat(client.delete(server.url("/delete")).execute().statusCode()).isEqualTo(200);
        assertThat(client.delete(server.url("/delete-body"), "x").execute().statusCode()).isEqualTo(200);
    }

    @Test
    void executeSupportsClassAndTypeReference() throws Exception {
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"exec\"}", "application/json"));

        EchoDto dto = client.get(server.url("/exec")).execute(EchoDto.class);
        Map<String, String> map = client.get(server.url("/exec"))
                .execute(new TypeReference<Map<String, String>>() {
                });

        assertThat(dto.name()).isEqualTo("exec");
        assertThat(map).containsEntry("name", "exec");
    }

    @Test
    void asyncWithClassTypeReturnsBody() throws Exception {
        server.setHandler((exchange, body) -> server.respondText(exchange, 200, "{\"name\":\"async-class\"}", "application/json"));

        CountDownLatch latch = new CountDownLatch(1);
        EchoDto[] holder = new EchoDto[1];
        client.get(server.url("/async-class")).async(new com.richie.component.http.core.AsyncCallback<EchoDto>() {
            @Override
            public void onResponse(HttpResponse response, EchoDto data) {
                holder[0] = data;
                latch.countDown();
            }

            @Override
            public void onFailure(java.io.IOException exception) {
                throw new RuntimeException(exception);
            }
        }, EchoDto.class);

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(holder[0].name()).isEqualTo("async-class");
    }

    @Test
    void asyncNotifiesNetworkFailure() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        java.io.IOException[] error = new java.io.IOException[1];
        client.get("http://127.0.0.1:1/unreachable").async(new AsyncCallback<>() {
            @Override
            public void onResponse(HttpResponse response, EchoDto data) {
            }

            @Override
            public void onFailure(java.io.IOException exception) {
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
                .hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    void futureCompletesExceptionallyOnNetworkFailure() {
        assertThatThrownBy(() -> client.get("http://127.0.0.1:1/unreachable")
                .future(EchoDto.class)
                .get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void postWithXmlContentTypeUsesXmlMedia() throws Exception {
        server.setHandler((exchange, body) -> {
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).contains("xml");
            server.respondText(exchange, 200, "xml-ok", "application/xml");
        });

        assertThat(client.post(server.url("/xml"), "<root/>").asXml().execute().bodyAsString())
                .isEqualTo("xml-ok");
    }

    @Test
    void postWithSoapContentTypeUsesSoapMedia() throws Exception {
        server.setHandler((exchange, body) -> {
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).contains("soap");
            server.respondText(exchange, 200, "soap-ok", "application/soap+xml");
        });

        assertThat(client.post(server.url("/soap"), "<Envelope/>").asSoap().execute().bodyAsString())
                .isEqualTo("soap-ok");
    }

    record EchoDto(String name) {
    }
}
