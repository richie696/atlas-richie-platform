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
package com.richie.component.http.core;

import tools.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 统一的 HTTP 客户端门面接口。
 * <p>
 * 实现类通过 Spring Boot 自动配置按 {@code platform.component.http.provider} 加载，
 * 支持 OkHttp、HttpClient5、Spring RestClient 三种底层实现，调用方代码无需感知。
 *
 * <h2>典型用法</h2>
 * <pre>{@code
 *   @Autowired
 *   private HttpClient http;
 *
 *   User user = http.get("https://api/users/123").execute(User.class);
 *   String json = http.post("https://api/users", newUser).execute();
 *
 *   // Future 方式
 *   CompletableFuture<User> future = http.get("https://api/users").future(User.class);
 *   future.thenAccept(user -> log.info("Got: {}", user));
 *
 *   // SSE 长连接
 *   SseConnection conn = http.sse("https://api/events", new SseListener() {
 *       @Override public void onEvent(SseConnection c, SseEvent e) {
 *           log.info("event: {}", e.data());
 *       }
 *   });
 * }</pre>
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public interface HttpClient {

    HttpRequest get(String url);

    HttpRequest post(String url, Object body);

    HttpRequest post(String url);

    HttpRequest put(String url, Object body);

    HttpRequest delete(String url, Object body);

    HttpRequest delete(String url);

    // ====== SSE ======

    /**
     * 打开一个 SSE（Server-Sent Events）长连接。
     * <p>
     * 连接建立后由 {@link SseListener} 异步接收事件，业务侧可通过返回的
     * {@link SseConnection} 获取响应元信息或主动关闭流。
     *
     * @param url      SSE 服务端点（{@code text/event-stream}）
     * @param listener 事件回调，不允许为 {@code null}
     * @return 连接句柄；调用 {@link SseConnection#close()} 可随时中断
     */
    SseConnection sse(String url, SseListener listener);

    /**
     * 打开一个带自定义请求头的 SSE 长连接。
     *
     * @param url      SSE 服务端点
     * @param headers  额外的请求头（鉴权、租户等），允许为 {@code null}
     * @param listener 事件回调，不允许为 {@code null}
     * @return 连接句柄
     */
    SseConnection sse(String url, Map<String, String> headers, SseListener listener);

    // ====== for HttpRequest delegation ======

    HttpResponse execute(HttpRequest request);

    <T> T execute(HttpRequest request, Class<T> type);

    <T> T execute(HttpRequest request, TypeReference<T> typeRef);

    <T> void async(HttpRequest request, AsyncCallback<T> callback, Class<T> type);

    <T> void async(HttpRequest request, AsyncCallback<T> callback, TypeReference<T> typeRef);

    <T> CompletableFuture<T> future(HttpRequest request, Class<T> type);

    <T> CompletableFuture<T> future(HttpRequest request, TypeReference<T> typeRef);

}
