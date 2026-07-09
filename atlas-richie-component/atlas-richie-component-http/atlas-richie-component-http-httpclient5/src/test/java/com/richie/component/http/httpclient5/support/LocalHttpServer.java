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
package com.richie.component.http.httpclient5.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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

    public String url(String path) {
        return "http://localhost:" + server.getAddress().getPort() + path;
    }

    public void setHandler(ExchangeHandler handler) {
        this.handler = handler;
    }

    public void respondText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * 发送 SSE 响应头（text/event-stream，chunked transfer encoding）。
     *
     * @param exchange HTTP 交换句柄
     * @return 响应体输出流，供 caller 写入 SSE 文本行
     * @throws IOException I/O 异常
     */
    public OutputStream respondSse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        return exchange.getResponseBody();
    }

    /**
     * 发送单个 SSE 事件块并立即关闭流。
     *
     * @param exchange    HTTP 交换句柄
     * @param sseContent  SSE 格式文本（通常由 {@link #formatEvent(String)} 等工具方法生成）
     * @throws IOException I/O 异常
     */
    public void respondSseAndClose(HttpExchange exchange, String sseContent) throws IOException {
        byte[] bytes = sseContent.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * 格式化仅含 data 字段的 SSE 事件文本（不含末尾空行）。
     *
     * @param data 事件数据
     * @return SSE 格式字符串，如 {@code "data:hello\n"}
     */
    public static String formatEvent(String data) {
        return "data:" + data + "\n";
    }

    /**
     * 格式化含 id / event / data 字段的 SSE 事件文本（不含末尾空行）。
     *
     * @param id    事件 ID（可为 {@code null}）
     * @param event 事件类型（可为 {@code null}，默认为 {@code message}）
     * @param data  事件数据
     * @return SSE 格式字符串，如 {@code "id:1\nevent:update\ndata:hello\n"}
     */
    public static String formatEvent(String id, String event, String data) {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("id:").append(id).append("\n");
        }
        if (event != null) {
            sb.append("event:").append(event).append("\n");
        }
        sb.append("data:").append(data).append("\n");
        return sb.toString();
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
