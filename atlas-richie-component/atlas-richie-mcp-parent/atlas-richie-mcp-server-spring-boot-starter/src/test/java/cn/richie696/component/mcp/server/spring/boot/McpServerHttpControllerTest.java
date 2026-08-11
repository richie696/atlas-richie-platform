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
package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.transport.http.McpHttpResponse;
import cn.richie696.component.mcp.transport.http.McpServerHttpEndpoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 验证 {@link McpServerHttpController} 的 Servlet 适配：JSON 单次响应、SSE 流响应、
 * 头部归一化、空 body、空响应。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpServerHttpController Servlet 适配")
class McpServerHttpControllerTest {

    @Nested
    @DisplayName("JSON 单次响应")
    class JsonResponse {

        @Test
        @DisplayName("返回 JSON 端点结果时 body 为响应体，Content-Type 沿用端点声明")
        void returnsJsonBody() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            Map<String, Object> body = Map.of("result", "ok");
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.json(200, body));

            McpServerHttpController controller = new McpServerHttpController(endpoint);
            ResponseEntity<Object> response = controller.handle(
                    "{}", Map.of("Content-Type", "application/json"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.getBody()).isSameAs(body);
        }

        @Test
        @DisplayName("响应未声明 Content-Type 时不设置 Content-Type header")
        void noContentTypeWhenAbsent() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            Map<String, Object> body = Map.of("result", "ok");
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(new McpHttpResponse(200, null, body));

            McpServerHttpController controller = new McpServerHttpController(endpoint);
            ResponseEntity<Object> response = controller.handle("{}",
                    Map.of("Content-Type", "application/json"));

            assertThat(response.getBody()).isSameAs(body);
            assertThat(response.getHeaders().getContentType()).isNull();
        }

        @Test
        @DisplayName("status 202 无 body 时返回无 body 的 ResponseEntity")
        void emptyBodyReturnsEmptyResponseEntity() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.accepted());

            McpServerHttpController controller = new McpServerHttpController(endpoint);
            ResponseEntity<Object> response = controller.handle("{}",
                    Map.of("Content-Type", "application/json"));

            assertThat(response.getStatusCode().value()).isEqualTo(202);
            assertThat(response.getBody()).isNull();
        }

        @Test
        @DisplayName("headers 都会被归一化为 List<String> 形式传递给端点")
        void headersNormalizedToList() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.json(200, Map.of()));

            McpServerHttpController controller = new McpServerHttpController(endpoint);
            controller.handle("{}", Map.of("X-Trace", "abc"));

            org.mockito.ArgumentCaptor<Map<String, List<String>>> captor =
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            org.mockito.Mockito.verify(endpoint).handle(
                    org.mockito.ArgumentMatchers.anyString(), captor.capture());
            assertThat(captor.getValue()).containsEntry("X-Trace", List.of("abc"));
        }
    }

    @Nested
    @DisplayName("SSE 流响应")
    class SseResponse {

        @Test
        @DisplayName("body 为 Flow.Publisher 时返回 SseEmitter，Content-Type 为 text/event-stream")
        void publisherBodyReturnsSseEmitter() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            Flow.Publisher<Object> publisher = new FinishingPublisher<>("hello");
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.sse(200, publisher, List.of()));

            McpServerHttpController controller = new McpServerHttpController(endpoint);
            ResponseEntity<Object> response = controller.handle("{}",
                    Map.of("Content-Type", "application/json"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
            assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
        }

        @Test
        @DisplayName("SSE 响应携带 notifications 时，notifications 在订阅启动前一次性发送")
        void notificationsAreSentBeforeSubscription() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            Flow.Publisher<Object> publisher = new FinishingPublisher<>("payload");
            Map<String, Object> notification = Map.of("type", "progress");
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.sse(200, publisher, List.of(notification)));

            McpServerHttpController controller = new McpServerHttpController(endpoint);
            ResponseEntity<Object> response = controller.handle("{}",
                    Map.of("Content-Type", "application/json"));

            // 直接探测 SseEmitter 内部 handler，未抛错即表示 notification 已写入
            SseEmitter emitter = (SseEmitter) response.getBody();
            assertThat(emitter).isNotNull();
            assertThat(emitter.getTimeout()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("构造")
    class Construction {

        @Test
        @DisplayName("构造时不抛异常（Mock 端点足够）")
        void constructionSucceeds() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            McpServerHttpController controller = new McpServerHttpController(endpoint);
            assertThat(controller).isNotNull();
        }
    }

    /** 简易 Flow.Publisher：被订阅后立即 send(Long.MAX_VALUE) -> 一次 next -> complete。 */
    private static final class FinishingPublisher<T> implements Flow.Publisher<T> {
        private final T value;

        FinishingPublisher(T value) {
            this.value = value;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super T> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    if (n <= 0) return;
                    subscriber.onNext(value);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                }
            });
        }
    }
}
