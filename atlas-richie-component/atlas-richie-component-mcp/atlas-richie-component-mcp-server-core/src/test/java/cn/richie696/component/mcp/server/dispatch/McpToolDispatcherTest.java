package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.McpCallCancelledException;
import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolExecutionException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private McpToolRegistry registry(
            cn.richie696.component.mcp.api.server.McpToolHandler handler) {
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
                        Map.of()),
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
