package com.richie.component.http.jdk;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpMethod;
import com.richie.component.http.core.HttpRequest;
import com.richie.component.http.core.HttpRequestSupport;
import com.richie.component.http.core.HttpResponse;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
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

    public JdkHttpAdapter(java.net.http.HttpClient httpClient) {
        this.httpClient = httpClient;
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
        var mime = request.contentTypeMime();
        if (mime != null) builder.header("Content-Type", mime);

        var timeout = request.timeout();
        if (timeout != null) builder.timeout(timeout);

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

}
