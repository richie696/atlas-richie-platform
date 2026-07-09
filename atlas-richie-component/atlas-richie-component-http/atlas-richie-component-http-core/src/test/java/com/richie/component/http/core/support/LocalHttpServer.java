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
package com.richie.component.http.core.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 轻量本地 HTTP 服务，供各 Provider 单元测试复用。
 */
public final class LocalHttpServer implements AutoCloseable {

    @FunctionalInterface
    public interface ExchangeHandler {
        void handle(HttpExchange exchange, byte[] body) throws IOException;
    }

    private final HttpServer server;
    private volatile ExchangeHandler handler = LocalHttpServer::okEmpty;

    public LocalHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try {
                byte[] body = exchange.getRequestBody().readAllBytes();
                handler.handle(exchange, body);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url(String path) {
        return "http://localhost:" + port() + path;
    }

    public void setHandler(ExchangeHandler handler) {
        this.handler = handler;
    }

    public void respondJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void okEmpty(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
}
