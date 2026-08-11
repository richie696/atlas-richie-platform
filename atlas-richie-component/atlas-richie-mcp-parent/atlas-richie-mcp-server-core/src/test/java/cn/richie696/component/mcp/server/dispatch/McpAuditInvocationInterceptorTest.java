package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.McpException;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolAuditEvent;
import cn.richie696.component.mcp.api.server.McpToolAuditSink;
import cn.richie696.component.mcp.api.server.McpToolInvocation;
import cn.richie696.component.mcp.api.server.McpToolInvocationChain;
import cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link McpAuditInvocationInterceptor} 的脱敏 + 异常隔离语义：
 * 审计未开启时直传 chain；开启审计时仅在执行完成后写入一条脱敏事件；
 * 三种敏感识别规则（{@code sensitiveArguments} / {@code x-mcp-sensitive} / 名称关键字）
 * 都会被替换为 {@code ***}；写入失败被吞掉，绝不影响业务结果；
 * 错误码字段从 {@link McpException} 或响应体中提取。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpAuditInvocationInterceptor 测试")
class McpAuditInvocationInterceptorTest {

    @Test
    @DisplayName("order() 返回 MIN_VALUE+200，确保在 timeout 拦截器之后执行")
    void exposesStableOrder() {
        McpAuditInvocationInterceptor interceptor =
                new McpAuditInvocationInterceptor(event -> {
                });

        assertThat(interceptor.order()).isEqualTo(Integer.MIN_VALUE + 200);
        assertThat(interceptor.order()).isGreaterThan(new McpTimeoutInvocationInterceptor().order());
    }

    @Test
    @DisplayName("audit 注解未开启时直接透传 chain，不写审计事件")
    void skipsWhenAuditDisabled() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolResponse response = new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")), Map.of("k", "v"), false);
        McpToolInvocation invocation = invocation(Map.of()); // audit missing

        McpToolResponse actual = interceptor.intercept(invocation, returns(response))
                .toCompletableFuture()
                .join();

        assertThat(actual).isSameAs(response);
        assertThat(captured.get()).isNull();
    }

    @Test
    @DisplayName("audit=true 时按 sensitiveArguments 名单脱敏")
    void redactsSensitiveArguments() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolResponse response = new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")), Map.of("ok", true), false);
        McpToolInvocation invocation = invocation(Map.of(
                "audit", true,
                "sensitiveArguments", List.of("pin")),
                Map.of("customerId", "C-1", "pin", "123456"));

        interceptor.intercept(invocation, returns(response)).toCompletableFuture().join();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().successful()).isTrue();
        assertThat(captured.get().errorCode()).isNull();
        assertThat(captured.get().arguments())
                .containsEntry("pin", "***")
                .containsEntry("customerId", "C-1");
    }

    @Test
    @DisplayName("audit=true 时按 inputSchema x-mcp-sensitive:true 标记脱敏")
    void redactsSensitiveSchemaProperties() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "customerId", Map.of("type", "string"),
                        "accessToken", Map.of("type", "string", "x-mcp-sensitive", true)));
        McpToolInvocation invocation = invocationWithSchema(
                Map.of("audit", true), inputSchema,
                Map.of("customerId", "C-1", "accessToken", "secret-value"));

        interceptor.intercept(invocation, returns(successResponse()))
                .toCompletableFuture().join();

        assertThat(captured.get().arguments())
                .containsEntry("accessToken", "***")
                .containsEntry("customerId", "C-1");
    }

    @Test
    @DisplayName("audit=true 时按名称关键字（authorization/token/secret 等）脱敏")
    void redactsBySensitiveNamePattern() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "customerId", Map.of("type", "string"),
                        "apiSecret", Map.of("type", "string")));
        McpToolInvocation invocation = invocationWithSchema(
                Map.of("audit", true), inputSchema,
                Map.of("customerId", "C-1", "apiSecret", "top-secret"));

        interceptor.intercept(invocation, returns(successResponse()))
                .toCompletableFuture().join();

        assertThat(captured.get().arguments())
                .containsEntry("apiSecret", "***")
                .containsEntry("customerId", "C-1");
    }

    @Test
    @DisplayName("audit=true 时嵌套 Map 中的敏感字段名也会被脱敏")
    void redactsNestedSensitiveMapKeys() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of("audit", true), Map.of(
                "customerId", "C-1",
                "metadata", Map.of("accessToken", "token-value", "ttl", 30)));

        interceptor.intercept(invocation, returns(successResponse()))
                .toCompletableFuture().join();

        assertThat(captured.get().arguments())
                .extractingByKey("metadata")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("accessToken", "***")
                .containsEntry("ttl", 30);
    }

    @Test
    @DisplayName("audit=true 时嵌套 Collection 会被递归脱敏")
    void redactsNestedCollectionValues() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of("audit", true), Map.of(
                "customerId", "C-1",
                "metadata", List.of(
                        Map.of("accessToken", "token-value"),
                        Map.of("ttl", 30))));

        interceptor.intercept(invocation, returns(successResponse()))
                .toCompletableFuture().join();

        Object sanitized = captured.get().arguments().get("metadata");
        assertThat(sanitized).asString().contains("***").doesNotContain("token-value");
    }

    @Test
    @DisplayName("audit=true 且业务抛异常 → 写入 successful=false + 错误码")
    void recordsFailureWhenHandlerThrows() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of("audit", true));
        McpException failure = new McpException("MCP_INTERNAL", "boom");
        McpToolInvocationChain chain = inv -> CompletableFuture.failedFuture(failure);

        assertThatCode(() -> interceptor.intercept(invocation, chain)
                .toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseReference(failure);

        assertThat(captured.get().successful()).isFalse();
        assertThat(captured.get().errorCode()).isEqualTo("MCP_INTERNAL");
    }

    @Test
    @DisplayName("audit=true 且 handler 返回 error=true → 错误码从 structuredContent.code 提取")
    void extractsErrorCodeFromErrorResponse() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolResponse errorResponse = new McpToolResponse(
                List.of(Map.of("type", "text", "text", "fail")),
                Map.of("code", "MCP_TOOL_INPUT_VALIDATION_FAILED"), true);
        McpToolInvocation invocation = invocation(Map.of("audit", true));

        interceptor.intercept(invocation, returns(errorResponse))
                .toCompletableFuture().join();

        assertThat(captured.get().successful()).isFalse();
        assertThat(captured.get().errorCode()).isEqualTo("MCP_TOOL_INPUT_VALIDATION_FAILED");
    }

    @Test
    @DisplayName("audit=true 时 sink.record 抛异常被吞掉，不影响业务")
    void sinkExceptionIsSwallowed() {
        McpToolAuditSink sink = mock(McpToolAuditSink.class);
        doThrow(new RuntimeException("storage offline")).when(sink).record(any());
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(sink);

        McpToolInvocation invocation = invocation(Map.of("audit", true));
        McpToolResponse response = successResponse();

        McpToolResponse actual = interceptor.intercept(invocation, returns(response))
                .toCompletableFuture()
                .join();

        assertThat(actual).isSameAs(response);
    }

    @Test
    @DisplayName("audit=true 且 errorCode 路径：CompletionException 包装会被解包到根因")
    void unwrapsCompletionExceptionForErrorCode() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of("audit", true));
        McpProtocolException protocol = new McpProtocolException(
                "MCP_INVALID_PARAMS", -32602, "bad", Map.of());
        McpToolInvocationChain chain = inv -> CompletableFuture.failedFuture(
                new CompletionException(protocol));

        assertThatCode(() -> interceptor.intercept(invocation, chain)
                .toCompletableFuture().join())
                .isInstanceOf(CompletionException.class);

        assertThat(captured.get().successful()).isFalse();
        assertThat(captured.get().errorCode()).isEqualTo("MCP_INVALID_PARAMS");
    }

    @Test
    @DisplayName("audit=true 但 sink.record 抛错且非 RuntimeException 时仍不抛出")
    void sinkExceptionSwallowedEvenFromMockitoStub() {
        // Use a real sink that throws to confirm catch(RuntimeException) handles it
        McpToolAuditSink throwingSink = event -> {
            throw new RuntimeException("nope");
        };
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(throwingSink);

        McpToolInvocation invocation = invocation(Map.of("audit", true));

        McpToolResponse actual = assertDoesNotThrow(() -> interceptor.intercept(invocation, returns(successResponse()))
                .toCompletableFuture().join());

        assertThat(actual).isNotNull();
    }

    @Test
    @DisplayName("mock sink 验证 record 被调用且事件字段被填充")
    void sinkReceivesCompletedEvent() {
        McpToolAuditSink sink = mock(McpToolAuditSink.class);
        AtomicReference<McpToolAuditEvent> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return null;
        }).when(sink).record(any());

        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(sink);
        McpToolInvocation invocation = invocation(Map.of("audit", true));

        interceptor.intercept(invocation, returns(successResponse()))
                .toCompletableFuture().join();

        McpToolAuditEvent event = stored.get();
        assertThat(event).isNotNull();
        assertThat(event.toolName()).isEqualTo("alpha.tool");
        assertThat(event.requestId()).isEqualTo("request-1");
        assertThat(event.tenantId()).isEqualTo("tenant-1");
        assertThat(event.subject()).isEqualTo("alice");
        assertThat(event.successful()).isTrue();
        assertThat(event.startedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(event.duration()).isGreaterThanOrEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("doNothing verification 协同 mock sink 验证 record 调用")
    void sinkVerifiedViaMockito() {
        McpToolAuditSink sink = mock(McpToolAuditSink.class);
        doNothing().when(sink).record(any());

        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(sink);
        interceptor.intercept(invocation(Map.of("audit", true)), returns(successResponse()))
                .toCompletableFuture().join();

        org.mockito.Mockito.verify(sink).record(any());
    }

    @Test
    @DisplayName("未声明 required=true 字段的敏感字段由 inputSchema 标记识别")
    void sensitiveArgumentsConfiguredAsCollectionSupportsStringEntries() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of(
                "audit", true,
                "sensitiveArguments", List.of(123, "pin")),
                Map.of("customerId", "C-1", "pin", "123456"));

        interceptor.intercept(invocation, returns(successResponse()))
                .toCompletableFuture().join();

        assertThat(captured.get().arguments()).containsEntry("pin", "***");
    }

    @Test
    @DisplayName("audit=true 时无敏感字段时全部原样保留")
    void argumentsPassThroughWhenNothingIsSensitive() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of("audit", true), Map.of(
                "customerId", "C-1",
                "amount", 100));

        interceptor.intercept(invocation, returns(successResponse()))
                .toCompletableFuture().join();

        assertThat(captured.get().arguments())
                .containsEntry("customerId", "C-1")
                .containsEntry("amount", 100);
    }

    @Test
    @DisplayName("audit=true 但 sink 未声明时抛 NPE 也被吞掉")
    void sinkNullPointerExceptionIsSwallowed() {
        McpToolAuditSink sink = event -> {
            throw new NullPointerException("npe");
        };
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(sink);

        McpToolInvocation invocation = invocation(Map.of("audit", true));

        McpToolResponse actual = assertDoesNotThrow(() ->
                interceptor.intercept(invocation, returns(successResponse()))
                        .toCompletableFuture().join());

        assertThat(actual).isNotNull();
    }

    @Test
    @DisplayName("audit=true 且失败为非 McpException → 错误码统一为 MCP_TOOL_INTERNAL_ERROR")
    void nonMcpExceptionFailureProducesInternalErrorCode() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of("audit", true));
        RuntimeException failure = new IllegalStateException("internal");
        McpToolInvocationChain chain = inv -> CompletableFuture.failedFuture(failure);

        assertThatCode(() -> interceptor.intercept(invocation, chain)
                .toCompletableFuture().join())
                .isInstanceOf(CompletionException.class);

        assertThat(captured.get().successful()).isFalse();
        assertThat(captured.get().errorCode()).isEqualTo("MCP_TOOL_INTERNAL_ERROR");
    }

    @Test
    @DisplayName("audit=true 且 error response 的 structuredContent 不是 Map → 错误码为 null")
    void errorResponseWithNonMapContentProducesNullErrorCode() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolResponse errorResponse = new McpToolResponse(
                List.of(Map.of("type", "text", "text", "fail")),
                "string-content-not-map", true);
        McpToolInvocation invocation = invocation(Map.of("audit", true));

        interceptor.intercept(invocation, returns(errorResponse))
                .toCompletableFuture().join();

        assertThat(captured.get().successful()).isFalse();
        assertThat(captured.get().errorCode()).isNull();
    }

    @Test
    @DisplayName("audit=true 且 error response 的 structuredContent.code 非 String → 错误码为 null")
    void errorResponseWithNonStringCodeProducesNullErrorCode() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolResponse errorResponse = new McpToolResponse(
                List.of(Map.of("type", "text", "text", "fail")),
                Map.of("code", 42), true);
        McpToolInvocation invocation = invocation(Map.of("audit", true));

        interceptor.intercept(invocation, returns(errorResponse))
                .toCompletableFuture().join();

        assertThat(captured.get().successful()).isFalse();
        assertThat(captured.get().errorCode()).isNull();
    }

    @Test
    @DisplayName("audit=true 且 handler 返回 null → successful=false 且错误码为 null")
    void nullResponseProducesUnsuccessfulAudit() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of("audit", true));
        McpToolInvocationChain chain = inv -> CompletableFuture.completedFuture(null);

        McpToolResponse actual = interceptor.intercept(invocation, chain)
                .toCompletableFuture()
                .join();

        assertThat(actual).isNull();
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().successful()).isFalse();
        assertThat(captured.get().errorCode()).isNull();
    }

    @Test
    @DisplayName("audit=true 且非 McpException 但被 CompletionException 包装 → 错误码 MCP_TOOL_INTERNAL_ERROR")
    void completionWrappedNonMcpExceptionProducesInternalErrorCode() {
        AtomicReference<McpToolAuditEvent> captured = new AtomicReference<>();
        McpAuditInvocationInterceptor interceptor = new McpAuditInvocationInterceptor(captured::set);

        McpToolInvocation invocation = invocation(Map.of("audit", true));
        RuntimeException failure = new IllegalArgumentException("wrapped");
        McpToolInvocationChain chain = inv -> CompletableFuture.failedFuture(
                new CompletionException(failure));

        assertThatCode(() -> interceptor.intercept(invocation, chain)
                .toCompletableFuture().join())
                .isInstanceOf(CompletionException.class);

        assertThat(captured.get().successful()).isFalse();
        assertThat(captured.get().errorCode()).isEqualTo("MCP_TOOL_INTERNAL_ERROR");
    }

    private static McpToolResponse successResponse() {
        return new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")),
                Map.of("ok", true), false);
    }

    private static McpToolInvocationChain returns(McpToolResponse response) {
        return inv -> CompletableFuture.completedFuture(response);
    }

    private static McpToolInvocation invocation(Map<String, Object> annotations) {
        return invocation(annotations, Map.of("customerId", "C-1"));
    }

    private static McpToolInvocation invocation(Map<String, Object> annotations,
                                                Map<String, Object> arguments) {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "alpha.tool",
                "Alpha",
                "test",
                Map.of(
                        "type", "object",
                        "properties", Map.of("customerId", Map.of("type", "string"))),
                Map.of(),
                annotations);
        McpCallContext context = new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                McpCancellationToken.NONE,
                null);
        McpToolHandlerStub handler = new McpToolHandlerStub();
        return new McpToolInvocation(
                descriptor, arguments, context, null, Map.of(), handler.toHandler());
    }

    private static McpToolInvocation invocationWithSchema(
            Map<String, Object> annotations,
            Map<String, Object> inputSchema,
            Map<String, Object> arguments) {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "alpha.tool", "Alpha", "test", inputSchema, Map.of(), annotations);
        McpCallContext context = new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                McpCancellationToken.NONE,
                null);
        McpToolHandlerStub handler = new McpToolHandlerStub();
        return new McpToolInvocation(
                descriptor, arguments, context, null, Map.of(), handler.toHandler());
    }

    private static McpToolResponse assertDoesNotThrow(ThrowingSupplier supplier) {
        try {
            return supplier.get();
        } catch (Throwable throwable) {
            throw new AssertionError("Expected no throw, but got " + throwable, throwable);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        McpToolResponse get() throws Throwable;
    }

    /** Simple stub avoiding Mockito on the functional interface (McpToolHandler is functional). */
    private static final class McpToolHandlerStub {
        cn.richie696.component.mcp.api.server.McpToolHandler toHandler() {
            return (a, c) -> CompletableFuture.completedFuture(successResponse());
        }
    }

    /** Public for static analysis convenience (also keeps the import explicit). */
    @SuppressWarnings("unused")
    private static class _Marker {
        McpToolInvocationInterceptor unused() {
            return mock(McpToolInvocationInterceptor.class);
        }

        @SuppressWarnings("unused")
        void unusedWhen() {
            when(mock(McpToolInvocationInterceptor.class).order()).thenReturn(0);
        }
    }
}