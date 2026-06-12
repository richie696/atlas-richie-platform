package com.richie.component.http.okhttp;

import com.richie.component.http.core.AsyncCallback;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpMethod;
import com.richie.component.http.core.HttpRequest;
import com.richie.component.http.core.HttpRequestSupport;
import com.richie.component.http.core.HttpResponse;
import com.richie.component.http.core.SseConnection;
import com.richie.component.http.core.SseListener;
import jakarta.annotation.Nonnull;
import tools.jackson.core.type.TypeReference;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 OkHttp 的 {@link HttpClient} 实现。
 *
 * @author richie696
 * @since 1.0.0
 * @version 1.0
 */
public class OkHttpAdapter implements HttpClient {

    private static final MediaType JSON_MEDIA = Objects.requireNonNull(MediaType.parse("application/json; charset=utf-8"));
    private static final MediaType XML_MEDIA = Objects.requireNonNull(MediaType.parse("application/xml; charset=utf-8"));
    private static final MediaType SOAP_MEDIA = Objects.requireNonNull(MediaType.parse("application/soap+xml"));

    private final OkHttpClient okHttpClient;
    private final OkHttpSseClient sseClient;

    public OkHttpAdapter(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
        this.sseClient = new OkHttpSseClient(okHttpClient);
    }

    @Override
    public HttpRequest get(String url) {
        return new HttpRequest(url, HttpMethod.GET, null).client(this);
    }

    @Override
    public HttpRequest post(String url, Object body) {
        return new HttpRequest(url, HttpMethod.POST, body).client(this);
    }

    @Override
    public HttpRequest post(String url) {
        return new HttpRequest(url, HttpMethod.POST, null).client(this);
    }

    @Override
    public HttpRequest put(String url, Object body) {
        return new HttpRequest(url, HttpMethod.PUT, body).client(this);
    }

    @Override
    public HttpRequest delete(String url, Object body) {
        return new HttpRequest(url, HttpMethod.DELETE, body).client(this);
    }

    @Override
    public HttpRequest delete(String url) {
        return new HttpRequest(url, HttpMethod.DELETE, null).client(this);
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        try (okhttp3.Response raw = doExecute(request)) {
            // 响应体一次性读取为字节数组，避免响应流被关闭后不可再次消费。
            return toHttpResponse(raw);
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed", e);
        }
    }

    @Override
    public <T> T execute(HttpRequest request, Class<T> type) {
        return execute(request).bodyAs(type);
    }

    @Override
    public <T> T execute(HttpRequest request, TypeReference<T> typeRef) {
        return execute(request).bodyAs(typeRef);
    }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, Class<T> type) {
        enqueue(request, callback, (raw) -> raw.bodyAs(type));
    }

    @Override
    public <T> void async(HttpRequest request, AsyncCallback<T> callback, TypeReference<T> typeRef) {
        enqueue(request, callback, (raw) -> raw.bodyAs(typeRef));
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, Class<T> type) {
        CompletableFuture<T> f = new CompletableFuture<>();
        okHttpClient.newCall(buildRequest(request)).enqueue(new Callback() {
            @Override
            public void onResponse(@Nonnull Call call, @Nonnull Response raw) {
                try (raw) {
                    f.complete(toHttpResponse(raw).bodyAs(type));
                } catch (Exception e) {
                    f.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                f.completeExceptionally(e);
            }
        });
        return f;
    }

    @Override
    public <T> CompletableFuture<T> future(HttpRequest request, TypeReference<T> typeRef) {
        CompletableFuture<T> f = new CompletableFuture<>();
        okHttpClient.newCall(buildRequest(request)).enqueue(new Callback() {
            @Override
            public void onResponse(@Nonnull Call call, @Nonnull Response raw) {
                try (raw) {
                    f.complete(toHttpResponse(raw).bodyAs(typeRef));
                } catch (Exception e) {
                    f.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                f.completeExceptionally(e);
            }
        });
        return f;
    }

    @Override
    public SseConnection sse(String url, SseListener listener) {
        return sseClient.connect(url, null, listener);
    }

    @Override
    public SseConnection sse(String url, Map<String, String> headers, SseListener listener) {
        return sseClient.connect(url, headers, listener);
    }

    private <T> void enqueue(HttpRequest request, AsyncCallback<T> callback, BodyParser<T> parser) {
        okHttpClient.newCall(buildRequest(request)).enqueue(new Callback() {
            @Override
            public void onResponse(@Nonnull Call call, @Nonnull Response raw) {
                try (raw) {
                    HttpResponse resp = toHttpResponse(raw);
                    callback.onResponse(resp, parser.parse(resp));
                } catch (Exception e) {
                    callback.onFailure(new IOException(e));
                }
            }

            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                callback.onFailure(e);
            }
        });
    }

    private @Nonnull Response doExecute(HttpRequest request) throws IOException {
        return clientFor(request).newCall(buildRequest(request)).execute();
    }

    private OkHttpClient clientFor(HttpRequest request) {
        Duration t = request.timeout();
        if (t == null) return okHttpClient;
        // 按请求覆盖超时配置，避免影响全局客户端实例。
        return okHttpClient.newBuilder().readTimeout(t).callTimeout(t).build();
    }

    private Request buildRequest(HttpRequest request) {
        Request.Builder b = new Request.Builder().url(HttpRequestSupport.buildUrlWithParams(request.url(), request.params()));
        var h = request.headers();
        if (h != null) h.forEach(b::addHeader);
        switch (request.method()) {
            case GET:
                b.get();
                break;
            case DELETE:
                b.delete(body(request));
                break;
            case PUT:
                b.put(body(request));
                break;
            case POST:
                b.post(body(request));
                break;
        }
        return b.build();
    }

    private RequestBody body(HttpRequest req) {
        Object b = req.body();
        if (b == null) return RequestBody.create(new byte[0], null);
        byte[] bytes;
        try {
            bytes = HttpRequestSupport.serializeBody(b);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
        if (bytes == null) bytes = new byte[0];
        return RequestBody.create(bytes, mime(req.contentTypeMime()));
    }

    private MediaType mime(String m) {
        return switch (m) {
            case "application/xml; charset=utf-8" -> XML_MEDIA;
            case "application/soap+xml" -> SOAP_MEDIA;
            default -> JSON_MEDIA;
        };
    }

    private HttpResponse toHttpResponse(@Nonnull Response raw) throws IOException {
        var body = raw.body().bytes();
        var headers = new HashMap<String, java.util.List<String>>();
        raw.headers().forEach(p -> headers.put(p.getFirst(), raw.headers().values(p.getFirst())));
        return HttpResponse.of(raw.code(), headers, body);
    }

    @FunctionalInterface
    private interface BodyParser<T> {
        T parse(HttpResponse resp) throws Exception;
    }

}
