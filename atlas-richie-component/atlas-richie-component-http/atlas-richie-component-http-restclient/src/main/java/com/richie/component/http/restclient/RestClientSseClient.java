package com.richie.component.http.restclient;

import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseEvent;
import com.richie.component.http.core.SseLineParser;
import com.richie.component.http.core.SseListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RestClient SSE 客户端实现。
 * <p>
 * 使用 Spring RestClient 的 {@code execute(ExchangeFunction)} 方式获取流式响应，
 * 将协议读取放在 exchange lambda 内同步执行（Spring 会在 lambda 返回后关闭响应体，
 * 因此不能异步读取）。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
class RestClientSseClient {

    private final RestClient restClient;

    RestClientSseClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 建立 SSE 连接。
     *
     * @param url      SSE 端点 URL
     * @param headers  自定义请求头（可为 null）
     * @param listener SSE 事件监听器
     * @return SSE 连接句柄
     */
    SseConnection connect(String url, Map<String, String> headers, SseListener listener) {
        RestClientSseConnection connection = new RestClientSseConnection();
        AtomicBoolean started = new AtomicBoolean(false);

        // RestClient.exchange(...) 默认在调用线程同步执行；为避免阻塞调用方，
        // 这里把整个 exchange 调用放到独立守护线程里执行，close() 通过中断该线程来打断读取。
        Thread worker = new Thread(() -> runExchange(url, headers, listener, connection, started),
                "restclient-sse-" + System.nanoTime());
        worker.setDaemon(true);
        connection.workerThread(worker);
        worker.start();

        // 等待 exchange lambda 至少执行到 setStatusCode 阶段，避免极短请求竞态
        long deadline = System.currentTimeMillis() + 5000;
        while (!started.get() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return connection;
    }

    private void runExchange(String url, Map<String, String> headers, SseListener listener,
                             RestClientSseConnection connection, AtomicBoolean started) {
        try {
            var spec = restClient.method(HttpMethod.GET).uri(url).header(HttpHeaders.ACCEPT, "text/event-stream");
            if (headers != null) {
                headers.forEach(spec::header);
            }

            spec.exchange((request, response) -> {
                int code = response.getStatusCode().value();
                HttpHeaders respHeaders = response.getHeaders();
                Map<String, java.util.List<String>> headerMap = new HashMap<>();
                respHeaders.forEach(headerMap::put);
                connection.setStatusCode(code);
                connection.setHeaders(headerMap);
                started.set(true);
                listener.onOpen(connection);

                // 关键点：必须在 exchange lambda 内同步读完流，lambda 返回后 Spring 会自动关闭响应体。
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
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
                } catch (IOException e) {
                    if (connection.isOpen()) {
                        connection.markClosed();
                        listener.onFailure(connection, e);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            if (connection.isOpen()) {
                connection.markClosed();
                listener.onFailure(connection, e);
            }
        }
    }

}