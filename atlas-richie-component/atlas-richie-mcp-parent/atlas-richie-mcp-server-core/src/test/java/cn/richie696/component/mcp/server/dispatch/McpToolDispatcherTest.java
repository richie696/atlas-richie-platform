package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.McpCallCancelledException;
import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolExecutionException;
import cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolDispatcher} 的端到端语义：输入校验失败时跳过 handler 并返回
 * {@code MCP_TOOL_INPUT_VALIDATION_FAILED}；业务异常按结构化错误回传；handler
 * 内部抛出的同步 {@link McpProtocolException} 原样保留；cancellation 在进入
 * handler 之前终止；拦截器按 {@code order} 升序调用；工具超时产生 {@code MCP_TOOL_TIMEOUT}；
 * 审计拦截器对 {@code sensitiveArguments} 名单字段打码；输出违反 schema 时升级为
 * {@code MCP_INVALID_TOOL_OUTPUT}。
 *
 * @author richie696
 * @since 2026-08-11
 */
class McpToolDispatcherTest {
    @Test
    void validatesInputExecutesHandlerAndValidatesStructuredOutput() {
        McpToolRegistry registry = registry((arguments, context) ->
                CompletableFuture.completedFuture(success(Map.of(
                        "customerId", arguments.get("customerId"),
                        "active", true))));

        McpToolResponse response = dispatch(registry, Map.of("customerId", "C-1"));

        assertThat(response.error()).isFalse();
        assertThat(response.structuredContent()).isEqualTo(
                Map.of("customerId", "C-1", "active", true));
    }

    @Test
    void inputValidationFailureIsActionableToolErrorAndSkipsHandler() {
        AtomicBoolean called = new AtomicBoolean();
        McpToolRegistry registry = registry((arguments, context) -> {
            called.set(true);
            return CompletableFuture.completedFuture(success(Map.of()));
        });

        McpToolResponse response = dispatch(registry, Map.of("customerId", 42));

        assertThat(called).isFalse();
        assertThat(response.error()).isTrue();
        assertThat(response.structuredContent()).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "MCP_TOOL_INPUT_VALIDATION_FAILED");
    }

    @Test
    void businessExecutionExceptionBecomesToolError() {
        McpToolRegistry registry = registry((arguments, context) -> {
            throw new McpToolExecutionException(
                    "Customer is inactive",
                    Map.of("code", "CUSTOMER_INACTIVE"),
                    null);
        });

        McpToolResponse response = dispatch(registry, Map.of("customerId", "C-1"));

        assertThat(response.error()).isTrue();
        assertThat(response.content().getFirst().get("text")).isEqualTo("Customer is inactive");
        assertThat(response.structuredContent()).isEqualTo(Map.of("code", "CUSTOMER_INACTIVE"));
    }

    @Test
    void unexpectedFailureIsSanitizedProtocolInternalError() {
        McpToolRegistry registry = registry((arguments, context) ->
                CompletableFuture.failedFuture(new IllegalStateException("database password leaked")));

        assertThatThrownBy(() -> dispatch(registry, Map.of("customerId", "C-1")))
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32603);
                    assertThat(exception.getMessage()).isEqualTo("Tool execution failed");
                    assertThat(exception.getMessage()).doesNotContain("password");
                    assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void synchronousProtocolFailureIsPreserved() {
        McpProtocolException expected = new McpProtocolException(
                "MCP_RATE_LIMITED",
                -32029,
                "Too many requests",
                Map.of("retryAfterMs", 1000));
        McpToolRegistry registry = registry((arguments, context) -> {
            throw expected;
        });

        assertThatThrownBy(() -> dispatch(registry, Map.of("customerId", "C-1")))
                .isInstanceOf(CompletionException.class)
                .cause()
                .isSameAs(expected);
    }

    @Test
    void invalidSuccessfulOutputIsServerProtocolError() {
        McpToolRegistry registry = registry((arguments, context) ->
                CompletableFuture.completedFuture(success(Map.of(
                        "customerId", 42,
                        "active", true))));

        assertThatThrownBy(() -> dispatch(registry, Map.of("customerId", "C-1")))
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("MCP_INVALID_TOOL_OUTPUT");
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32603);
                });
    }

    @Test
    void cancellationStopsBeforeBusinessExecution() {
        AtomicBoolean called = new AtomicBoolean();
        McpToolRegistry registry = registry((arguments, context) -> {
            called.set(true);
            return CompletableFuture.completedFuture(success(Map.of()));
        });
        McpToolDispatcher dispatcher = new McpToolDispatcher(registry);
        McpCallContext cancelled = new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                () -> true,
                null);

        assertThatThrownBy(() -> dispatcher.dispatch(
                "customer.lookup", Map.of("customerId", "C-1"), cancelled))
                .isInstanceOf(McpCallCancelledException.class);
        assertThat(called).isFalse();
    }

    @Test
    void invokesInterceptorsInDeclaredOrder() {
        McpToolRegistry registry = registry((arguments, context) ->
                CompletableFuture.completedFuture(success(Map.of(
                        "customerId", "C-1", "active", true))));
        List<Integer> calls = new CopyOnWriteArrayList<>();
        McpToolInvocationInterceptor late = interceptor(20, calls);
        McpToolInvocationInterceptor early = interceptor(10, calls);

        new McpToolDispatcher(registry, List.of(late, early))
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture().join();

        assertThat(calls).containsExactly(10, 20);
    }

    @Test
    void appliesConfiguredToolTimeout() {
        McpToolRegistry registry = registry(
                (arguments, context) -> new CompletableFuture<>(),
                Map.of("timeoutMs", 25L));

        assertThatThrownBy(() -> new McpToolDispatcher(
                registry, List.of(new McpTimeoutInvocationInterceptor()))
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("MCP_TOOL_TIMEOUT");
                    assertThat(exception.getMessage()).doesNotContain("customerId");
                });
    }

    @Test
    void auditInterceptorRedactsSchemaAndConventionSensitiveValues() {
        AtomicReference<cn.richie696.component.mcp.api.server.McpToolAuditEvent> event =
                new AtomicReference<>();
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(new McpToolRegistration(
                new McpToolDescriptor(
                        "secret.check", null, null,
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "customerId", Map.of("type", "string"),
                                        "pin", Map.of("type", "string"),
                                        "metadata", Map.of("type", "object")),
                                "required", List.of("customerId", "pin")),
                        Map.of(),
                        Map.of("audit", true, "sensitiveArguments", List.of("pin"))),
                (arguments, context) -> CompletableFuture.completedFuture(
                        new McpToolResponse(List.of(), Map.of("ok", true), false))));

        new McpToolDispatcher(registry, List.of(new McpAuditInvocationInterceptor(event::set)))
                .dispatch("secret.check", Map.of(
                        "customerId", "C-1",
                        "pin", "123456",
                        "metadata", Map.of("accessToken", "token-value")), context())
                .toCompletableFuture().join();

        assertThat(event.get().successful()).isTrue();
        assertThat(event.get().arguments()).containsEntry("pin", "***");
        assertThat(event.get().arguments().toString())
                .doesNotContain("123456", "token-value");
    }

    private McpToolInvocationInterceptor interceptor(int order, List<Integer> calls) {
        return new McpToolInvocationInterceptor() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public java.util.concurrent.CompletionStage<McpToolResponse> intercept(
                    cn.richie696.component.mcp.api.server.McpToolInvocation invocation,
                    cn.richie696.component.mcp.api.server.McpToolInvocationChain chain) {
                calls.add(order);
                return chain.proceed(invocation);
            }
        };
    }

    private McpToolRegistry registry(
            cn.richie696.component.mcp.api.server.McpToolHandler handler) {
        return registry(handler, Map.of());
    }

    private McpToolRegistry registry(
            cn.richie696.component.mcp.api.server.McpToolHandler handler,
            Map<String, Object> annotations) {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(new McpToolRegistration(
                new McpToolDescriptor(
                        "customer.lookup",
                        "Customer Lookup",
                        "Looks up a customer",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "customerId", Map.of("type", "string", "minLength", 1)),
                                "required", List.of("customerId"),
                                "additionalProperties", false),
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "customerId", Map.of("type", "string"),
                                        "active", Map.of("type", "boolean")),
                                "required", List.of("customerId", "active"),
                                "additionalProperties", false),
                        annotations),
                handler));
        return registry;
    }

    private McpToolResponse dispatch(McpToolRegistry registry, Map<String, Object> arguments) {
        return new McpToolDispatcher(registry)
                .dispatch("customer.lookup", arguments, context())
                .toCompletableFuture()
                .join();
    }

    private McpToolResponse success(Object structuredContent) {
        return new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")),
                structuredContent,
                false);
    }

    private McpCallContext context() {
        return new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                null,
                null);
    }
}
