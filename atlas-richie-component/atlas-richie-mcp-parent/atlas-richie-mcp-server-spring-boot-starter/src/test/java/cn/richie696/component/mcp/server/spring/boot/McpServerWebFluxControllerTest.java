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
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 验证 {@link McpServerWebFluxController} 的响应式适配：JSON 单次响应、SSE 流响应、
 * 头部归一化、空 body、空响应、Flow.Publisher 错误路径。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpServerWebFluxController WebFlux 适配")
class McpServerWebFluxControllerTest {

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

            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            ResponseEntity<Object> response = controller.handleJson(
                    Mono.just("{}"), Map.of("Content-Type", "application/json"))
                    .block();

            assertThat(response).isNotNull();
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

            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            ResponseEntity<Object> response = controller.handleJson(
                    Mono.just("{}"), Map.of("Content-Type", "application/json"))
                    .block();

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

            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            ResponseEntity<Object> response = controller.handleJson(
                    Mono.just("{}"), Map.of("Content-Type", "application/json"))
                    .block();

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

            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            controller.handleJson(Mono.just("{}"), Map.of("X-Trace", "abc"))
                    .block();

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
        @DisplayName("body 为 Flow.Publisher 时桥接到 Flux 并 startWith notifications")
        void publisherBodyBridgedToFlux() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            Flow.Publisher<Object> publisher = new FinishingPublisher<>("payload");
            Map<String, Object> notification = Map.of("type", "progress");
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.sse(200, publisher, List.of(notification)));

            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            Flux<ServerSentEvent<String>> events = controller.handleSse(
                    Mono.just("{}"), Map.of("Content-Type", "application/json"));

            List<ServerSentEvent<String>> collected = events.collectList().block();
            assertThat(collected).hasSize(2);
            assertThat(collected.get(0).event()).isEqualTo("message");
            assertThat(collected.get(0).data()).contains("progress");
            assertThat(collected.get(1).event()).isEqualTo("message");
            assertThat(collected.get(1).data()).contains("payload");
        }

        @Test
        @DisplayName("body 为普通对象但 contentType 为 SSE 时同样被序列化为单个 SSE 事件")
        void nonPublisherBodyBecomesSingleEvent() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            Map<String, Object> body = Map.of("result", "ok");
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.sse(200, body, List.of()));

            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            Flux<ServerSentEvent<String>> events = controller.handleSse(
                    Mono.just("{}"), Map.of("Content-Type", "application/json"));

            List<ServerSentEvent<String>> collected = events.collectList().block();
            assertThat(collected).hasSize(1);
            assertThat(collected.getFirst().event()).isEqualTo("message");
            assertThat(collected.getFirst().data()).contains("ok");
        }

        @Test
        @DisplayName("body 为 null 时 SSE 流仅包含 notification")
        void nullBodyReturnsNotificationsOnly() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            Map<String, Object> notification = Map.of("type", "ack");
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.sse(200, null, List.of(notification)));

            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            Flux<ServerSentEvent<String>> events = controller.handleSse(
                    Mono.just("{}"), Map.of("Content-Type", "application/json"));

            List<ServerSentEvent<String>> collected = events.collectList().block();
            assertThat(collected).hasSize(1);
            assertThat(collected.getFirst().data()).contains("ack");
        }

        @Test
        @DisplayName("Flow.Publisher 报告错误时错误透传到 Flux")
        void publisherErrorPropagatesToFlux() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            Flow.Publisher<Object> publisher = subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onError(new IllegalStateException("stream error"));
                }

                @Override
                public void cancel() {
                }
            });
            given(endpoint.handle(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyMap()))
                    .willReturn(McpHttpResponse.sse(200, publisher, List.of()));

            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            Flux<ServerSentEvent<String>> events = controller.handleSse(
                    Mono.just("{}"), Map.of("Content-Type", "application/json"));

            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable = () ->
                    events.collectList().block();
            assertThatThrownBy(callable)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("stream error");
        }

    }

    @Nested
    @DisplayName("构造")
    class Construction {

        @Test
        @DisplayName("构造时不抛异常（Mock 端点足够）")
        void constructionSucceeds() {
            McpServerHttpEndpoint endpoint = mock(McpServerHttpEndpoint.class);
            McpServerWebFluxController controller = new McpServerWebFluxController(endpoint);
            assertThat(controller).isNotNull();
        }
    }

    /** 简易 Flow.Publisher：被订阅后立即 send -> 一次 next -> complete。 */
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
