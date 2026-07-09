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
package com.richie.component.http.restclient;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpMethod;
import com.richie.component.http.core.HttpRequest;
import com.richie.component.http.core.HttpRequestSupport;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseListener;
import tools.jackson.core.type.TypeReference;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 Spring {@link RestClient} 的 {@link HttpClient} 实现。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public class RestClientAdapter implements HttpClient {

    private final RestClient restClient;
    private final RestClientSseClient sseClient;

    public RestClientAdapter(RestClient restClient) {
        this.restClient = restClient;
        this.sseClient = new RestClientSseClient(restClient);
    }

    @Override
    public HttpRequest get(String url) { return new HttpRequest(url, HttpMethod.GET, null).client(this); }
    @Override
    public HttpRequest post(String url, Object body) { return new HttpRequest(url, HttpMethod.POST, body).client(this); }
    @Override
    public HttpRequest post(String url) { return new HttpRequest(url, HttpMethod.POST, null).client(this); }
    @Override
    public HttpRequest put(String url, Object body) { return new HttpRequest(url, HttpMethod.PUT, body).client(this); }
    @Override
    public HttpRequest delete(String url, Object body) { return new HttpRequest(url, HttpMethod.DELETE, body).client(this); }
    @Override
    public HttpRequest delete(String url) { return new HttpRequest(url, HttpMethod.DELETE, null).client(this); }

    @Override
    public HttpResponse execute(HttpRequest request) {
        // RestClient 本身不直接提供单次请求超时，这里通过共享工具层统一兜底。
        var raw = HttpRequestSupport.executeWithTimeout(request.timeout(),
                () -> buildSpec(request).retrieve().toEntity(byte[].class));
        var headers = new HashMap<String, List<String>>();
        raw.getHeaders().forEach(headers::put);
        return HttpResponse.of(raw.getStatusCode().value(), headers, raw.getBody());
    }

    @Override
    public <T> T execute(HttpRequest request, Class<T> type) {
        return HttpRequestSupport.executeWithTimeout(request.timeout(),
                () -> buildSpec(request).retrieve().body(type));
    }

    @Override
    public <T> T execute(HttpRequest request, TypeReference<T> typeRef) {
        return HttpRequestSupport.executeWithTimeout(request.timeout(),
                () -> buildSpec(request).retrieve().body(toSpringType(typeRef)));
    }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, Class<T> type) {
        CompletableFuture.runAsync(() -> {
            try {
                ResponseEntity<T> entity = buildSpec(request).retrieve().toEntity(type);
                callback.onResponse(toHttpResponse(entity), entity.getBody());
            } catch (Exception e) {
                callback.onFailure(new IOException(e));
            }
        });
    }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, TypeReference<T> typeRef) {
        CompletableFuture.runAsync(() -> {
            try {
                ResponseEntity<T> entity = buildSpec(request).retrieve().toEntity(toSpringType(typeRef));
                callback.onResponse(toHttpResponse(entity), entity.getBody());
            } catch (Exception e) {
                callback.onFailure(new IOException(e));
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, Class<T> type) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> buildSpec(request).retrieve().body(type));
        if (request.timeout() != null && !request.timeout().isZero() && !request.timeout().isNegative()) {
            // future 模式也遵循 HttpRequest.timeout() 语义。
            future = future.orTimeout(request.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        return future;
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, TypeReference<T> typeRef) {
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> buildSpec(request).retrieve().body(toSpringType(typeRef)));
        if (request.timeout() != null && !request.timeout().isZero() && !request.timeout().isNegative()) {
            future = future.orTimeout(request.timeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        return future;
    }

    @Override
    public SseConnection sse(String url, SseListener listener) {
        return sseClient.connect(url, null, listener);
    }

    @Override
    public SseConnection sse(String url, Map<String, String> headers, SseListener listener) {
        return sseClient.connect(url, headers, listener);
    }

    private RestClient.RequestBodySpec buildSpec(HttpRequest request) {
        var req = restClient.method(org.springframework.http.HttpMethod.valueOf(request.method().name()))
                .uri(HttpRequestSupport.buildUrlWithParams(request.url(), request.params()));
        var headers = request.headers();
        if (headers != null) headers.forEach(req::header);
        if (request.contentTypeMime() != null) req.header("Content-Type", request.contentTypeMime());
        if (request.body() != null) req.body(request.body());
        return req;
    }

    private static <T> ParameterizedTypeReference<T> toSpringType(TypeReference<T> ref) {
        return ParameterizedTypeReference.forType(ref.getType());
    }

    private static HttpResponse toHttpResponse(ResponseEntity<?> entity) {
        var headers = new HashMap<String, List<String>>();
        entity.getHeaders().forEach(headers::put);
        var body = entity.getBody();
        return body instanceof byte[] b
                ? HttpResponse.of(entity.getStatusCode().value(), headers, b)
                : HttpResponse.of(entity.getStatusCode().value(), headers, new byte[0]);
    }

}
