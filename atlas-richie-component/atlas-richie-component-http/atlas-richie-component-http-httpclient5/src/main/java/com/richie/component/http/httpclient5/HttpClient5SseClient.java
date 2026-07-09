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
package com.richie.component.http.httpclient5;

import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseEvent;
import com.richie.component.http.core.SseLineParser;
import com.richie.component.http.core.SseListener;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Apache HttpClient5 的 SSE 客户端实现。
 * <p>
 * 通过 {@link #connect(String, Map, SseListener)} 建立 SSE 长连接，
 * 在后台线程中读取流式响应并通过 {@link SseLineParser} 解析协议帧，
 * 将完整事件分发给 {@link SseListener} 回调。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public class HttpClient5SseClient {

    private final CloseableHttpClient httpClient;

    /**
     * 创建 SSE 客户端。
     *
     * @param httpClient Apache HttpClient5 实例
     */
    public HttpClient5SseClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 建立 SSE 连接并开始接收事件。
     *
     * @param url      SSE 端点 URL
     * @param headers  额外请求头（可为 {@code null}）
     * @param listener 事件监听器
     * @return SSE 连接句柄
     */
    public SseConnection connect(String url, Map<String, String> headers, SseListener listener) {
        HttpGet get = new HttpGet(url);
        if (headers != null) {
            headers.forEach(get::setHeader);
        }

        final HttpClient5SseConnection connection = new HttpClient5SseConnection();
        try {
            CloseableHttpResponse raw = httpClient.execute(get);
            connection.setResponse(raw);
            listener.onOpen(connection);

            AtomicBoolean failed = new AtomicBoolean(false);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            executor.submit(() -> readLoop(connection, raw, listener, failed));
        } catch (IOException e) {
            listener.onFailure(connection, e);
        }
        return connection;
    }

    private void readLoop(HttpClient5SseConnection connection,
                          ClassicHttpResponse response,
                          SseListener listener,
                          AtomicBoolean failed) {
        SseLineParser parser = new SseLineParser();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
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
        } catch (Exception e) {
            if (failed.compareAndSet(false, true) && connection.isOpen()) {
                listener.onFailure(connection, e);
            }
        } finally {
            connection.close();
        }
    }

}
