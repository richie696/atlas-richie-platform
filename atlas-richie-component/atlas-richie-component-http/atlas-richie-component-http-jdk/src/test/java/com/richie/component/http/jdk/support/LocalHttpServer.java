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
package com.richie.component.http.jdk.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * 本地 HTTP 测试服务器，支持 SSE（Server-Sent Events）响应。
 * <p>
 * 提供 SSE 格式的辅助方法，用于测试 SSE 客户端功能。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
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

    public String url(String path) {
        return "http://localhost:" + server.getAddress().getPort() + path;
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

    /**
     * 发起 SSE 响应，返回 OutputStream 用于写入 SSE 内容。
     * <p>
     * 自动设置 SSE 所需的响应头：
     * <ul>
     *   <li>Content-Type: text/event-stream; charset=utf-8</li>
     *   <li>Cache-Control: no-cache</li>
     *   <li>Connection: keep-alive</li>
     * </ul>
     *
     * @param exchange HTTP 交换对象
     * @return 响应体 OutputStream，调用方负责写入 SSE 数据后关闭
     * @throws IOException 如果发生 I/O 错误
     */
    public OutputStream respondSse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        return exchange.getResponseBody();
    }

    /**
     * 发送单个 SSE 事件并关闭连接。
     * <p>
     * 写入单个 SSE 事件块后立即关闭连接，适用于测试简单场景。
     *
     * @param exchange     HTTP 交换对象
     * @param sseContent   SSE 格式的事件内容
     * @throws IOException 如果发生 I/O 错误
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
     * 格式化仅包含 data 字段的 SSE 事件。
     *
     * @param data 事件数据
     * @return 格式化的 SSE 事件字符串（以空行结尾）
     */
    public static String formatEvent(String data) {
        return formatEvent(null, null, data);
    }

    /**
     * 格式化完整的 SSE 事件。
     *
     * @param id    事件 ID（可为 null）
     * @param event 事件类型（可为 null，默认 message）
     * @param data  事件数据
     * @return 格式化的 SSE 事件字符串（以空行结尾）
     */
    public static String formatEvent(String id, String event, String data) {
        StringBuilder sb = new StringBuilder();
        if (id != null) {
            sb.append("id: ").append(id).append("\n");
        }
        if (event != null) {
            sb.append("event: ").append(event).append("\n");
        }
        if (data != null) {
            sb.append("data: ").append(data).append("\n");
        }
        sb.append("\n");
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
