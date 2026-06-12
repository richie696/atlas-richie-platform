package com.richie.component.http.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestTest {

    @Test
    void builderCollectsRequestMetadata() {
        HttpRequest request = new HttpRequest("https://example.com/path", HttpMethod.POST, "{\"a\":1}")
                .param("q", "1")
                .params(Map.of("page", "2"))
                .header("X-Test", "yes")
                .headers(Map.of("Accept", "application/json"))
                .timeout(Duration.ofSeconds(5))
                .asXml();

        assertThat(request.url()).isEqualTo("https://example.com/path");
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.params()).containsEntry("q", "1").containsEntry("page", "2");
        assertThat(request.headers()).containsEntry("X-Test", "yes").containsEntry("Accept", "application/json");
        assertThat(request.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(request.contentTypeMime()).isEqualTo("application/xml; charset=utf-8");
    }

    @Test
    void multipartOverridesContentType() {
        HttpRequest request = new HttpRequest("https://example.com/upload", HttpMethod.POST, null)
                .asJson()
                .multipart("file", "a.txt", new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)));

        assertThat(request.contentTypeMime()).isEqualTo("multipart/form-data");
        assertThat(request.multipartFieldName()).isEqualTo("file");
        assertThat(request.multipartFileName()).isEqualTo("a.txt");
        assertThat(request.multipartData()).isNotNull();
    }

    @Test
    void contentTypeShortcuts() {
        HttpRequest json = new HttpRequest("https://example.com", HttpMethod.POST, null).asJson();
        HttpRequest soap = new HttpRequest("https://example.com", HttpMethod.POST, null).asSoap();
        HttpRequest form = new HttpRequest("https://example.com", HttpMethod.POST, null).asForm();

        assertThat(json.contentTypeMime()).contains("json");
        assertThat(soap.contentTypeMime()).contains("soap");
        assertThat(form.contentTypeMime()).contains("form-urlencoded");
    }

    @Test
    void executeWithoutClientThrows() {
        HttpRequest request = new HttpRequest("https://example.com", HttpMethod.GET, null);

        assertThatThrownBy(request::execute).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.execute(String.class)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.future(String.class)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stubClientDelegatesExecution() throws Exception {
        AtomicReference<HttpRequest> captured = new AtomicReference<>();
        HttpClient stub = new StubHttpClient(captured);
        HttpRequest request = stub.get("https://example.com/items").param("id", "1");

        HttpResponse response = request.execute();
        Demo body = request.execute(Demo.class);
        CompletableFuture<Demo> future = request.future(Demo.class);
        AtomicReference<Demo> asyncBody = new AtomicReference<>();
        request.async(new AsyncCallback<>() {
            @Override
            public void onResponse(HttpResponse response, Demo data) {
                asyncBody.set(data);
            }

            @Override
            public void onFailure(java.io.IOException exception) {
                throw new RuntimeException(exception);
            }
        }, Demo.class);

        assertThat(captured.get()).isSameAs(request);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.name()).isEqualTo("ok");
        assertThat(future.get(2, TimeUnit.SECONDS).name()).isEqualTo("ok");
        assertThat(asyncBody.get().name()).isEqualTo("ok");
    }

    private static final class StubHttpClient implements HttpClient {
        private final AtomicReference<HttpRequest> captured;

        private StubHttpClient(AtomicReference<HttpRequest> captured) {
            this.captured = captured;
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
        public SseConnection sse(String url, SseListener listener) {
            throw new UnsupportedOperationException("SSE not used in this test");
        }

        @Override
        public SseConnection sse(String url, Map<String, String> headers, SseListener listener) {
            throw new UnsupportedOperationException("SSE not used in this test");
        }

        @Override
        public HttpResponse execute(HttpRequest request) {
            captured.set(request);
            return HttpResponse.of(200, Map.of("X-Stub", List.of("1")),
                    "{\"name\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public <T> T execute(HttpRequest request, Class<T> type) {
            return execute(request).bodyAs(type);
        }

        @Override
        public <T> T execute(HttpRequest request, tools.jackson.core.type.TypeReference<T> typeRef) {
            return execute(request).bodyAs(typeRef);
        }

        @Override
        public <T> void async(HttpRequest request, AsyncCallback<T> callback, Class<T> type) {
            callback.onResponse(execute(request), execute(request, type));
        }

        @Override
        public <T> void async(HttpRequest request, AsyncCallback<T> callback, tools.jackson.core.type.TypeReference<T> typeRef) {
            callback.onResponse(execute(request), execute(request, typeRef));
        }

        @Override
        public <T> CompletableFuture<T> future(HttpRequest request, Class<T> type) {
            return CompletableFuture.completedFuture(execute(request, type));
        }

        @Override
        public <T> CompletableFuture<T> future(HttpRequest request, tools.jackson.core.type.TypeReference<T> typeRef) {
            return CompletableFuture.completedFuture(execute(request, typeRef));
        }
    }

    private record Demo(String name) {
    }
}
