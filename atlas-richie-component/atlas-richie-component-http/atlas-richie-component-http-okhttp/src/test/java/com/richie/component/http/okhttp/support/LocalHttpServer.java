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
package com.richie.component.http.okhttp.support;

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

    public void respondText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
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

    /**
     * 发送 SSE 响应的辅助方法。
     * <p>
     * 设置 {@code Content-Type: text/event-stream; charset=utf-8}、
     * {@code Cache-Control: no-cache}、{@code Connection: keep-alive}，
     * 并发送 200 状态码（0 长度表示 chunked transfer encoding）。
     *
     * @param exchange HTTP 交换对象
     * @return 响应体输出流，调用方写入 SSE 事件后自行关闭
     * @throws IOException 如果发送响应头失败
     */
    public OutputStream respondSse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        return exchange.getResponseBody();
    }

    /**
     * 发送单个 SSE 事件并关闭连接的辅助方法。
     * <p>
     * 适用于只需要发送一个事件的简单测试场景。
     *
     * @param exchange    HTTP 交换对象
     * @param sseContent  SSE 事件帧内容（通常由 {@link #formatEvent(String)} 生成）
     * @throws IOException 如果发送响应失败
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
     * 格式化 SSE 事件帧（仅 data 字段）。
     * <p>
     * 生成标准 SSE 格式：{@code data: <content>\n\n}
     *
     * @param data 事件数据
     * @return 格式化后的事件帧字符串
     */
    public static String formatEvent(String data) {
        return "data: " + data + "\n\n";
    }

    /**
     * 格式化 SSE 事件帧（完整字段）。
     * <p>
     * 生成标准 SSE 格式，各字段按协议规范排列。
     *
     * @param id    事件 ID（可为 null）
     * @param event 事件类型（可为 null，默认 message）
     * @param data  事件数据
     * @return 格式化后的事件帧字符串
     */
    public static String formatEvent(String id, String event, String data) {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("id: ").append(id).append("\n");
        }
        if (event != null) {
            sb.append("event: ").append(event).append("\n");
        }
        sb.append("data: ").append(data).append("\n");
        sb.append("\n");
        return sb.toString();
    }
}
