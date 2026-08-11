package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.transport.http.McpHttpResponse;
import cn.richie696.component.mcp.transport.http.McpServerHttpEndpoint;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

/**
 * 将框架无关的 {@link McpServerHttpEndpoint} 绑定到 Spring WebFlux（响应式）容器。
 *
 * <p>仅在 Reactive Web 环境下生效（{@code @ConditionalOnWebApplication(REACTIVE)}）；Servlet
 * 环境请使用 {@link McpServerHttpController}。普通请求返回单个 JSON/SSE 响应；modern
 * subscriptions / listen 通过 {@link java.util.concurrent.Flow.Publisher} 保持响应流直到
 * 订阅关闭。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@RestController
@RequestMapping("${platform.component.mcp.server.path:/mcp}")
public final class McpServerWebFluxController {
    private final McpServerHttpEndpoint endpoint;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * 构造 WebFlux 控制器。
     *
     * @param endpoint 框架无关的 HTTP 端点，不可为 {@code null}
     */
    public McpServerWebFluxController(McpServerHttpEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 处理 JSON 模式的 MCP 请求（单次响应）。
     *
     * @param body 原始 JSON 请求体的响应式包装
     * @param headers HTTP 请求头集合
     * @return 单次 JSON 响应实体；响应体可为空（仅状态码/Content-Type 有效）
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> handleJson(
            @RequestBody Mono<String> body,
            @RequestHeader Map<String, String> headers) {
        return body.map(json -> response(endpoint.handle(json, normalize(headers))));
    }

    /**
     * 处理 SSE 模式的 MCP 请求（流式响应）。
     *
     * <p>当底层响应体为 {@link java.util.concurrent.Flow.Publisher} 时，会被桥接到 Reactor
     * 的 {@link Flux}，保持响应流直到订阅关闭；框架侧 {@code notifications} 会在 SSE 流的
     * 起始处一次性发出。</p>
     *
     * @param body 原始 JSON 请求体的响应式包装
     * @param headers HTTP 请求头集合
     * @return SSE 事件流；事件类型统一为 {@code "message"}
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> handleSse(
            @RequestBody Mono<String> body,
            @RequestHeader Map<String, String> headers) {
        return body.flatMapMany(json -> events(endpoint.handle(json, normalize(headers))));
    }

    private ResponseEntity<Object> response(McpHttpResponse response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status());
        if (response.contentType() != null) {
            builder.contentType(MediaType.parseMediaType(response.contentType()));
        }
        return response.hasBody() ? builder.body(response.body()) : builder.build();
    }

    private ServerSentEvent<String> event(McpHttpResponse response) {
        String data;
        try {
            data = response.hasBody() ? jsonMapper.writeValueAsString(response.body()) : "{}";
        } catch (JacksonException exception) {
            data = "{}";
        }
        return ServerSentEvent.<String>builder(data)
                .event("message")
                .build();
    }

    private Flux<ServerSentEvent<String>> events(McpHttpResponse response) {
        java.util.List<ServerSentEvent<String>> events = new java.util.ArrayList<>();
        response.notifications().forEach(notification -> events.add(eventData(notification)));
        if (response.body() instanceof Flow.Publisher<?> publisher) {
            return fromFlowPublisher(publisher)
                    .map(this::eventData)
                    .startWith(Flux.fromIterable(events));
        }
        if (response.hasBody()) {
            events.add(event(response));
        }
        return Flux.fromIterable(events);
    }

    private Flux<Object> fromFlowPublisher(Flow.Publisher<?> publisher) {
        return Flux.create(sink -> publisher.subscribe(new Flow.Subscriber<Object>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Object item) {
                sink.next(item);
            }

            @Override
            public void onError(Throwable throwable) {
                sink.error(throwable);
            }

            @Override
            public void onComplete() {
                sink.complete();
            }
        }));
    }

    private ServerSentEvent<String> eventData(Object value) {
        try {
            return ServerSentEvent.<String>builder(jsonMapper.writeValueAsString(value))
                    .event("message")
                    .build();
        } catch (JacksonException exception) {
            return ServerSentEvent.<String>builder("{}").event("message").build();
        }
    }

    private Map<String, List<String>> normalize(Map<String, String> headers) {
        return headers.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.of(entry.getValue())));
    }
}
