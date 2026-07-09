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
package com.richie.component.http.okhttp;

import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseEvent;
import com.richie.component.http.core.SseListener;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.util.Map;

/**
 * OkHttp SSE 客户端。
 * <p>
 * 基于 OkHttp SSE SDK 实现的 SSE 连接管理器，用于建立和管理 SSE 长连接。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
class OkHttpSseClient {

    private final OkHttpClient okHttpClient;
    private final EventSource.Factory eventSourceFactory;

    OkHttpSseClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
        this.eventSourceFactory = EventSources.createFactory(okHttpClient);
    }

    SseConnection connect(String url, Map<String, String> headers, SseListener listener) {
        Request.Builder builder = new Request.Builder().url(url);
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }
        Request request = builder.build();

        OkHttpSseConnection connection = new OkHttpSseConnection();

        EventSourceListener listenerWrapper = new EventSourceListener() {
            @Override
            public void onOpen(EventSource es, Response response) {
                connection.setResponse(response);
                connection.setEventSource(es);
                connection.markOpened();
                listener.onOpen(connection);
            }

            @Override
            public void onEvent(EventSource es, String id, String type, String data) {
                SseEvent event = new SseEvent(id, type, data, null);
                listener.onEvent(connection, event);
            }

            @Override
            public void onClosed(EventSource es) {
                connection.markClosed();
                listener.onClosed(connection);
            }

            @Override
            public void onFailure(EventSource es, Throwable t, Response response) {
                if (response != null) {
                    connection.setResponse(response);
                }
                connection.markClosed();
                connection.setFailure(t);
                listener.onFailure(connection, t);
            }
        };

        EventSource source = eventSourceFactory.newEventSource(request, listenerWrapper);
        connection.setEventSource(source);

        return connection;
    }

}
