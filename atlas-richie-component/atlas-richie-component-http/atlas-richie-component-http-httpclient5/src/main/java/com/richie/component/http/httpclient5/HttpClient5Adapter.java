package com.richie.component.http.httpclient5;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpMethod;
import com.richie.component.http.core.HttpRequest;
import com.richie.component.http.core.HttpRequestSupport;
import com.richie.component.http.core.HttpResponse;
import tools.jackson.core.type.TypeReference;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 Apache HttpClient5 的 {@link HttpClient} 实现。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public class HttpClient5Adapter implements HttpClient {

    private final CloseableHttpClient httpClient;

    public HttpClient5Adapter(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
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
        try {
            // 使用 ResponseHandler 方式执行，可自动管理底层连接释放。
            return httpClient.execute(buildRequest(request), this::buildResponse);
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
        CompletableFuture.runAsync(() -> {
            try {
                var resp = execute(request);
                callback.onResponse(resp, resp.bodyAs(type));
            } catch (Exception e) {
                callback.onFailure(new IOException(e));
            }
        });
    }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, TypeReference<T> typeRef) {
        CompletableFuture.runAsync(() -> {
            try {
                var resp = execute(request);
                callback.onResponse(resp, resp.bodyAs(typeRef));
            } catch (Exception e) {
                callback.onFailure(new IOException(e));
            }
        });
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> execute(request).bodyAs(type));
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, TypeReference<T> typeRef) {
        return CompletableFuture.supplyAsync(() -> execute(request).bodyAs(typeRef));
    }

    private HttpUriRequestBase buildRequest(HttpRequest request) {
        URI uri = buildUri(request);
        var method = request.method().name();
        var req = new HttpUriRequestBase(method, uri);
        HttpEntity entity = bodyEntity(request);
        if (entity != null) {
            req.setEntity(entity);
        }
        var headers = request.headers();
        if (headers != null) headers.forEach(req::setHeader);
        if (request.timeout() != null) {
            var requestConfig = RequestConfig.custom()
                    .setConnectionRequestTimeout(Timeout.of(request.timeout()))
                    .setResponseTimeout(Timeout.of(request.timeout()))
                    .build();
            req.setConfig(requestConfig);
        }
        return req;
    }

    private URI buildUri(HttpRequest request) {
        try {
            return new URIBuilder(HttpRequestSupport.buildUrlWithParams(request.url(), request.params())).build();
        } catch (Exception e) {
            return URI.create(request.url());
        }
    }

    private HttpEntity bodyEntity(HttpRequest request) {
        if (request.multipartData() != null) {
            // multipart 场景优先按文件上传协议构建实体。
            return MultipartEntityBuilder.create()
                    .addBinaryBody(
                            request.multipartFieldName() != null ? request.multipartFieldName() : "file",
                            request.multipartData(),
                            ContentType.APPLICATION_OCTET_STREAM,
                            request.multipartFileName() != null ? request.multipartFileName() : "upload.bin")
                    .build();
        }

        byte[] bodyBytes = bodyBytes(request);
        if (bodyBytes == null || bodyBytes.length == 0) {
            // 空请求体时不设置 entity，避免 GET/DELETE 被强制带 body。
            return null;
        }
        return new ByteArrayEntity(bodyBytes, ContentType.parse(request.contentTypeMime()));
    }

    private byte[] bodyBytes(HttpRequest request) {
        return HttpRequestSupport.serializeBody(request.body());
    }

    private HttpResponse buildResponse(ClassicHttpResponse raw) throws IOException {
        int code = raw.getCode();
        byte[] bytes = raw.getEntity() != null ? EntityUtils.toByteArray(raw.getEntity()) : new byte[0];
        var headers = new HashMap<String, java.util.List<String>>();
        for (var h : raw.getHeaders()) {
            headers.computeIfAbsent(h.getName(), k -> new java.util.ArrayList<>()).add(h.getValue());
        }
        return HttpResponse.of(code, headers, bytes);
    }

}
