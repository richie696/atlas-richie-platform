package com.richie.component.http.jdk;

import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseEvent;
import com.richie.component.http.core.SseListener;
import com.richie.component.http.core.SseLineParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * JDK HttpClient 的 SSE 客户端实现。
 * <p>
 * 通过 {@link #connect(String, Map, SseListener)} 建立 SSE 长连接，
 * 并将解析出的事件通过 {@link SseListener} 回调传递给调用方。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
class JdkSseClient {

    private final java.net.http.HttpClient httpClient;
    private final ExecutorService workerExecutor;

    /**
     * 使用指定的 HttpClient 创建 SSE 客户端。
     *
     * @param httpClient JDK HttpClient 实例
     */
    JdkSseClient(java.net.http.HttpClient httpClient) {
        this.httpClient = httpClient;
        this.workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 建立 SSE 长连接。
     *
     * @param url      SSE 端点 URL
     * @param headers  额外请求头（可为空）
     * @param listener SSE 事件监听器
     * @return SSE 连接句柄
     */
    SseConnection connect(String url, Map<String, String> headers, SseListener listener) {
        JdkSseConnection connection = new JdkSseConnection();

        java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();

        if (headers != null && !headers.isEmpty()) {
            headers.forEach(reqBuilder::header);
        }

        if (!headersContains(headers, "Accept")) {
            reqBuilder.header("Accept", "text/event-stream");
        }

        java.net.http.HttpRequest request = reqBuilder.build();

        httpClient.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofInputStream())
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        Throwable cause = ex instanceof java.util.concurrent.CompletionException
                                ? ex.getCause() : ex;
                        listener.onFailure(connection, cause);
                        return;
                    }

                    connection.setResponse(response);
                    listener.onOpen(connection);

                    Future<?> workerFuture = CompletableFuture.runAsync(
                            () -> readStream(connection, response, listener),
                            workerExecutor
                    );
                    connection.setWorkerFuture(workerFuture);
                });

        return connection;
    }

    private void readStream(JdkSseConnection connection,
                            java.net.http.HttpResponse<java.io.InputStream> response,
                            SseListener listener) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {

            SseLineParser parser = new SseLineParser();
            String line;

            while ((line = reader.readLine()) != null) {
                if (!connection.isOpen()) {
                    break;
                }
                SseEvent ev = parser.feed(line);
                if (ev != null) {
                    listener.onEvent(connection, ev);
                }
            }

            SseEvent trailing = parser.flush();
            if (trailing != null) {
                listener.onEvent(connection, trailing);
            }

            if (connection.isOpen()) {
                connection.markClosed();
                listener.onClosed(connection);
            }

        } catch (java.io.IOException | RuntimeException e) {
            if (connection.isOpen()) {
                listener.onFailure(connection, e);
            }
        } finally {
            connection.markClosed();
        }
    }

    private static boolean headersContains(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        return headers.keySet().stream()
                .anyMatch(k -> k.equalsIgnoreCase(name));
    }

}
