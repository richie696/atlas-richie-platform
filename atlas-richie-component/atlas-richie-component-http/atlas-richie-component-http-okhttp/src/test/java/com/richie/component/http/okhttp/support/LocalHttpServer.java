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
}
