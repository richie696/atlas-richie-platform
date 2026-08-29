/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.common;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class HttpResponseRetryExecutorTest {

    @Test
    void retriesRateLimitsAndServerFailuresWithinTheConfiguredBudget() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/retry", exchange -> {
            int current = calls.incrementAndGet();
            byte[] body = (current < 3 ? "discard-me" : "ok").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Retry-After", "0");
            exchange.sendResponseHeaders(current < 3 ? (current == 1 ? 429 : 503) : 200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(server, "/retry"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            var response = new HttpResponseRetryExecutor(3).send(HttpClient.newHttpClient(), request);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).asString(StandardCharsets.UTF_8).isEqualTo("ok");
            assertThat(calls).hasValue(3);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void neverRetriesAuthenticationOrAuthorizationFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forbidden", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        server.start();
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(server, "/forbidden")).GET().build();

            var response = new HttpResponseRetryExecutor(3).send(HttpClient.newHttpClient(), request);

            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(calls).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    private URI uri(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
