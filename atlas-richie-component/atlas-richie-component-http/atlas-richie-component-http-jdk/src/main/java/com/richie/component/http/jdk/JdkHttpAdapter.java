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
package com.richie.component.http.jdk;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpMethod;
import com.richie.component.http.core.HttpRequest;
import com.richie.component.http.core.HttpRequestSupport;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseListener;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 JDK 原生 {@code java.net.http.HttpClient} 的 {@link HttpClient} 实现。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public class JdkHttpAdapter implements HttpClient {

    private final java.net.http.HttpClient httpClient;
    private final JdkSseClient sseClient;

    public JdkHttpAdapter(java.net.http.HttpClient httpClient) {
        this.httpClient = httpClient;
        this.sseClient = new JdkSseClient(httpClient);
    }

    @Override
    public SseConnection sse(String url, SseListener listener) {
        return sseClient.connect(url, null, listener);
    }

    @Override
    public SseConnection sse(String url, Map<String, String> headers, SseListener listener) {
        return sseClient.connect(url, headers, listener);
    }

    @Override public HttpRequest get(String url) { return new HttpRequest(url, HttpMethod.GET, null).client(this); }
    @Override public HttpRequest post(String url, Object body) { return new HttpRequest(url, HttpMethod.POST, body).client(this); }
    @Override public HttpRequest post(String url) { return new HttpRequest(url, HttpMethod.POST, null).client(this); }
    @Override public HttpRequest put(String url, Object body) { return new HttpRequest(url, HttpMethod.PUT, body).client(this); }
    @Override public HttpRequest delete(String url, Object body) { return new HttpRequest(url, HttpMethod.DELETE, body).client(this); }
    @Override public HttpRequest delete(String url) { return new HttpRequest(url, HttpMethod.DELETE, null).client(this); }

    @Override
    public HttpResponse execute(HttpRequest request) {
        try {
            // 同步请求统一收敛为 byte[]，方便后续反序列化复用。
            var raw = httpClient.send(buildJdkRequest(request), BodyHandlers.ofByteArray());
            return toHttpResponse(raw);
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed", e);
        }
    }

    @Override
    public <T> T execute(HttpRequest request, Class<T> type) { return execute(request).bodyAs(type); }
    @Override
    public <T> T execute(HttpRequest request, TypeReference<T> typeRef) { return execute(request).bodyAs(typeRef); }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, Class<T> type) {
        httpClient.sendAsync(buildJdkRequest(request), BodyHandlers.ofByteArray())
                .thenAccept(raw -> {
                    try {
                        var resp = toHttpResponse(raw);
                        callback.onResponse(resp, resp.bodyAs(type));
                    } catch (Exception e) {
                        callback.onFailure(new IOException(e));
                    }
                })
                .exceptionally(e -> { callback.onFailure(new IOException(e)); return null; });
    }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, TypeReference<T> typeRef) {
        httpClient.sendAsync(buildJdkRequest(request), BodyHandlers.ofByteArray())
                .thenAccept(raw -> {
                    try {
                        var resp = toHttpResponse(raw);
                        callback.onResponse(resp, resp.bodyAs(typeRef));
                    } catch (Exception e) {
                        callback.onFailure(new IOException(e));
                    }
                })
                .exceptionally(e -> { callback.onFailure(new IOException(e)); return null; });
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, Class<T> type) {
        return httpClient.sendAsync(buildJdkRequest(request), BodyHandlers.ofByteArray())
                .thenApply(raw -> toHttpResponse(raw).bodyAs(type));
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, TypeReference<T> typeRef) {
        return httpClient.sendAsync(buildJdkRequest(request), BodyHandlers.ofByteArray())
                .thenApply(raw -> toHttpResponse(raw).bodyAs(typeRef));
    }

    private java.net.http.HttpRequest buildJdkRequest(HttpRequest request) {
        var builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(HttpRequestSupport.buildUrlWithParams(request.url(), request.params())));

        var headers = request.headers();
        if (headers != null) headers.forEach(builder::header);

        var timeout = request.timeout();
        if (timeout != null) builder.timeout(timeout);

        var multipartData = request.multipartData();
        if (multipartData != null) {
            String boundary = "----JdkHttpAdapter" + System.nanoTime();
            builder.header("Content-Type", "multipart/form-data; boundary=" + boundary);
            byte[] multipartBody = buildMultipartBody(boundary,
                    request.multipartFieldName(), request.multipartFileName(), multipartData);
            switch (request.method()) {
                case POST:   builder.POST(BodyPublishers.ofByteArray(multipartBody)); break;
                case PUT:    builder.PUT(BodyPublishers.ofByteArray(multipartBody)); break;
                case DELETE: builder.method("DELETE", BodyPublishers.ofByteArray(multipartBody)); break;
                default:     builder.method(request.method().name(), BodyPublishers.ofByteArray(multipartBody)); break;
            }
            return builder.build();
        }

        var mime = request.contentTypeMime();
        if (mime != null) builder.header("Content-Type", mime);

        byte[] bodyBytes = bodyBytes(request);
        // DELETE 保留 body 能力，与其它 Provider 保持一致行为。
        switch (request.method()) {
            case GET:    builder.GET(); break;
            case DELETE: builder.method("DELETE", bodyPublisher(bodyBytes)); break;
            case PUT:    builder.PUT(bodyPublisher(bodyBytes)); break;
            case POST:   builder.POST(bodyPublisher(bodyBytes)); break;
        }
        return builder.build();
    }

    private static java.net.http.HttpRequest.BodyPublisher bodyPublisher(byte[] bytes) {
        return bytes != null && bytes.length > 0
                ? BodyPublishers.ofByteArray(bytes)
                : BodyPublishers.noBody();
    }

    private byte[] bodyBytes(HttpRequest request) {
        return HttpRequestSupport.serializeBody(request.body());
    }

    private HttpResponse toHttpResponse(java.net.http.HttpResponse<byte[]> raw) {
        return HttpResponse.of(raw.statusCode(), raw.headers().map(), raw.body());
    }

    /**
     * 构建 multipart/form-data 请求体字节数组。
     * <p>按 RFC 7578 组装单文件 multipart body，Content-Type 头已在调用方设置。
     */
    private static byte[] buildMultipartBody(String boundary, String fieldName, String fileName, InputStream data) {
        try {
            byte[] fileBytes = data.readAllBytes();
            String header = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + fieldName
                    + "\"; filename=\"" + fileName + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n";
            String footer = "\r\n--" + boundary + "--\r\n";
            byte[] pre = header.getBytes(StandardCharsets.UTF_8);
            byte[] post = footer.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[pre.length + fileBytes.length + post.length];
            System.arraycopy(pre, 0, result, 0, pre.length);
            System.arraycopy(fileBytes, 0, result, pre.length, fileBytes.length);
            System.arraycopy(post, 0, result, pre.length + fileBytes.length, post.length);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Failed to build multipart body", e);
        }
    }

}
