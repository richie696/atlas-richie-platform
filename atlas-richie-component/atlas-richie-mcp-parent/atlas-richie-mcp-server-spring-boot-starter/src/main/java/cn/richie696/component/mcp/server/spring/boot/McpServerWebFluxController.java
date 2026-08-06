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
 * 将框架无关的 MCP HTTP Endpoint 绑定到 Spring WebFlux。
 *
 * <p>普通请求返回单个 JSON/SSE 响应；modern subscriptions/listen 通过
 * {@link java.util.concurrent.Flow.Publisher} 保持响应流直到订阅关闭。</p>
 */
@RestController
@RequestMapping("${platform.component.mcp.server.path:/mcp}")
public final class McpServerWebFluxController {
    private final McpServerHttpEndpoint endpoint;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public McpServerWebFluxController(McpServerHttpEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Object>> handleJson(
            @RequestBody Mono<String> body,
            @RequestHeader Map<String, String> headers) {
        return body.map(json -> response(endpoint.handle(json, normalize(headers))));
    }

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
