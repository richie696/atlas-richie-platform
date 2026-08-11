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
 * 将框架无关的 {@link McpServerHttpEndpoint} 绑定到 Spring MVC（Servlet）容器。
 *
 * <p>仅在 Servlet 环境下生效（{@code @ConditionalOnWebApplication(SERVLET)}）；响应式环境
 * 请使用 {@link McpServerWebFluxController}。本控制器同时支持 JSON 单次响应和
 * SSE 流式响应（modern subscriptions / listen）：当底层返回
 * {@link java.util.concurrent.Flow.Publisher} 时，会被适配为 {@link SseEmitter}。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@RestController
@RequestMapping("${platform.component.mcp.server.path:/mcp}")
public final class McpServerHttpController {
    private final McpServerHttpEndpoint endpoint;

    /**
     * 构造 Servlet 控制器。
     *
     * @param endpoint 框架无关的 HTTP 端点，不可为 {@code null}
     */
    public McpServerHttpController(McpServerHttpEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 处理 MCP over HTTP 请求，并按响应类型自动选择 JSON 或 SSE 输出。
     *
     * <p>当 {@link McpHttpResponse#body()} 是 {@link java.util.concurrent.Flow.Publisher}
     * 时，按 SSE 流式输出（{@code Content-Type: text/event-stream}）；否则按 JSON 单次输出。
     * 框架侧携带的 {@code notifications} 会在 SSE 流的开始一次性发出，避免订阅启动前
     * 的事件被吞掉。</p>
     *
     * @param body 原始 JSON 请求体
     * @param headers HTTP 请求头集合
     * @return Spring MVC 响应实体；SSE 模式下 body 为 {@link SseEmitter}
     */
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
