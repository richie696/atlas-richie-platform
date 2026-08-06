package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.transport.http.McpHttpResponse;
import cn.richie696.component.mcp.transport.http.McpServerHttpEndpoint;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 将框架无关的 MCP HTTP Endpoint 绑定到 Spring MVC。
 */
@RestController
@RequestMapping("${platform.component.mcp.server.path:/mcp}")
public final class McpServerHttpController {
    private final McpServerHttpEndpoint endpoint;

    public McpServerHttpController(McpServerHttpEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<Object> handle(
            @RequestBody String body,
            @RequestHeader Map<String, String> headers) {
        Map<String, List<String>> normalizedHeaders = headers.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.of(entry.getValue())));
        McpHttpResponse response = endpoint.handle(body, normalizedHeaders);
        if (response.body() instanceof java.util.concurrent.Flow.Publisher<?> publisher) {
            SseEmitter emitter = new SseEmitter(0L);
            response.notifications().forEach(notification -> send(emitter, notification));
            publisher.subscribe(new java.util.concurrent.Flow.Subscriber<Object>() {
                @Override
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(Object item) {
                    send(emitter, item);
                }

                @Override
                public void onError(Throwable throwable) {
                    emitter.completeWithError(throwable);
                }

                @Override
                public void onComplete() {
                    emitter.complete();
                }
            });
            return ResponseEntity.status(response.status())
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(emitter);
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status());
        if (response.contentType() != null) {
            builder.contentType(MediaType.parseMediaType(response.contentType()));
        }
        return response.hasBody() ? builder.body(response.body()) : builder.build();
    }

    private void send(SseEmitter emitter, Object value) {
        try {
            emitter.send(SseEmitter.event().name("message").data(value));
        } catch (java.io.IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
