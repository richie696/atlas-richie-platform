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
