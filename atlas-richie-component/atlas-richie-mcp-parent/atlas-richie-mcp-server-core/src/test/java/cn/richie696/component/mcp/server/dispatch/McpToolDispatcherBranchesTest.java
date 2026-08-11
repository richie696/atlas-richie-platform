package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.McpCallCancelledException;
import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpArgumentBindingException;
import cn.richie696.component.mcp.api.server.McpToolExecutionException;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import cn.richie696.component.mcp.server.tool.McpToolRegistration;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 针对 {@link McpToolDispatcher} 异常分支的补充分支覆盖：
 * 同步抛出 {@link McpArgumentBindingException} /
 * {@link McpCallCancelledException} / {@link McpProtocolException} /
 * 通用异常的链路；异步失败路径；
 * {@code policies} 注解中含非 String key；
 * 错误响应被 outputSchema 校验跳过等场景。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpToolDispatcher 补充分支测试")
class McpToolDispatcherBranchesTest {

    @Test
    @DisplayName("handler 同步抛 McpArgumentBindingException → Tool error 响应")
    void synchronousBindingExceptionBecomesToolError() {
        McpToolRegistry registry = registry((a, c) -> {
            throw new McpArgumentBindingException("customerId", "must be string");
        });

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isTrue();
        assertThat(response.structuredContent())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "MCP_TOOL_ARGUMENT_BINDING_FAILED")
                .containsEntry("argument", "customerId");
    }

    @Test
    @DisplayName("handler 同步抛 McpCallCancelledException → 协议异常被透传")
    void synchronousCallCancelledPropagates() {
        McpToolRegistry registry = registry((a, c) -> {
            throw new McpCallCancelledException();
        });

        assertThatThrownBy(() -> new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(McpCallCancelledException.class);
    }

    @Test
    @DisplayName("handler 同步抛通用异常 → 包装为 MCP_TOOL_INTERNAL_ERROR 协议异常")
    void synchronousGenericExceptionBecomesProtocolError() {
        McpToolRegistry registry = registry((a, c) -> {
            throw new IllegalStateException("internal");
        });

        assertThatThrownBy(() -> new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("MCP_TOOL_INTERNAL_ERROR");
                    assertThat(exception.getMessage()).isEqualTo("Tool execution failed");
                    assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    @DisplayName("异步失败为 McpArgumentBindingException → Tool error 响应")
    void asyncBindingExceptionBecomesToolError() {
        McpToolRegistry registry = registry((a, c) -> CompletableFuture.failedFuture(
                new McpArgumentBindingException("customerId", "bad")));

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isTrue();
        assertThat(response.structuredContent())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "MCP_TOOL_ARGUMENT_BINDING_FAILED")
                .containsEntry("argument", "customerId");
    }

    @Test
    @DisplayName("异步失败为 McpCallCancelledException → 协议异常被透传")
    void asyncCallCancelledPropagates() {
        McpToolRegistry registry = registry((a, c) -> CompletableFuture.failedFuture(
                new McpCallCancelledException()));

        assertThatThrownBy(() -> new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(McpCallCancelledException.class);
    }

    @Test
    @DisplayName("异步失败为 McpProtocolException → 协议异常被透传")
    void asyncProtocolExceptionPropagates() {
        McpProtocolException expected = new McpProtocolException(
                "MCP_RATE_LIMITED", -32029, "rate", Map.of());
        McpToolRegistry registry = registry((a, c) -> CompletableFuture.failedFuture(expected));

        assertThatThrownBy(() -> new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isSameAs(expected);
    }

    @Test
    @DisplayName("异步失败为 McpToolExecutionException → Tool error 响应（async 路径）")
    void asyncToolExecutionExceptionBecomesToolError() {
        McpToolRegistry registry = registry((a, c) -> CompletableFuture.failedFuture(
                new McpToolExecutionException(
                        "validation failed", Map.of("field", "customerId"), null)));

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isTrue();
        assertThat(response.structuredContent())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("field", "customerId");
    }

    @Test
    @DisplayName("handler 返回 null → 被升级为 MCP_TOOL_INTERNAL_ERROR（内部 NPE 不泄漏）")
    void nullResponseFromHandlerIsSanitized() {
        McpToolRegistry registry = registry((a, c) -> CompletableFuture.completedFuture(null));

        assertThatThrownBy(() -> new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("MCP_TOOL_INTERNAL_ERROR");
                    assertThat(exception.getMessage()).isEqualTo("Tool execution failed");
                    assertThat(exception.getCause()).isInstanceOf(NullPointerException.class);
                });
    }

    @Test
    @DisplayName("annotations.policies 含非 String key → 静默过滤（仅保留 String key）")
    void policiesAnnotationFiltersNonStringKeys() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration registration = new McpToolRegistration(
                new McpToolDescriptor(
                        "filter.tool", null, null,
                        Map.of("type", "object",
                                "properties", Map.of("id", Map.of("type", "string"))),
                        Map.of(),
                        Map.of("policies", annotationsWithMixedPolicies().get("policies"))),
                successHandler());
        registry.register(registration);

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("filter.tool", Map.of("id", "1"), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isFalse();
    }

    @Test
    @DisplayName("annotations.policies 非 Map 类型 → 等价于空策略")
    void policiesAnnotationWithNonMapValueIsEmpty() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration registration = new McpToolRegistration(
                new McpToolDescriptor(
                        "nonmap.tool", null, null,
                        Map.of("type", "object",
                                "properties", Map.of("id", Map.of("type", "string"))),
                        Map.of(),
                        Map.of("policies", "not-a-map")),
                successHandler());
        registry.register(registration);

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("nonmap.tool", Map.of("id", "1"), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isFalse();
    }

    @Test
    @DisplayName("annotations.timeoutMs 非 Number → 不设置 timeout")
    void timeoutMsWithNonNumberValueIsIgnored() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration registration = new McpToolRegistration(
                new McpToolDescriptor(
                        "stringtimeout.tool", null, null,
                        Map.of("type", "object",
                                "properties", Map.of("id", Map.of("type", "string"))),
                        Map.of(),
                        Map.of("timeoutMs", "100")),
                successHandler());
        registry.register(registration);

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("stringtimeout.tool", Map.of("id", "1"), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isFalse();
    }

    @Test
    @DisplayName("annotations.timeoutMs ≤ 0 → 不设置 timeout")
    void timeoutMsWithZeroValueIsIgnored() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration registration = new McpToolRegistration(
                new McpToolDescriptor(
                        "zerotimeout.tool", null, null,
                        Map.of("type", "object",
                                "properties", Map.of("id", Map.of("type", "string"))),
                        Map.of(),
                        Map.of("timeoutMs", 0L)),
                successHandler());
        registry.register(registration);

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("zerotimeout.tool", Map.of("id", "1"), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isFalse();
    }

    @Test
    @DisplayName("error 响应跳过 outputSchema 校验")
    void errorResponseSkipsOutputValidation() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration registration = new McpToolRegistration(
                new McpToolDescriptor(
                        "customer.lookup", null, null,
                        Map.of("type", "object",
                                "properties", Map.of("customerId", Map.of("type", "string"))),
                        Map.of("type", "object",
                                "properties", Map.of("ok", Map.of("type", "boolean"))),
                        Map.of()),
                (a, c) -> CompletableFuture.completedFuture(
                        new McpToolResponse(List.of(), Map.of("wrong", "shape"), true)));
        registry.register(registration);

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isTrue();
    }

    @Test
    @DisplayName("input_required 响应跳过 outputSchema 校验")
    void inputRequiredResponseSkipsOutputValidation() {
        McpToolRegistry registry = new McpToolRegistry();
        McpToolRegistration registration = new McpToolRegistration(
                new McpToolDescriptor(
                        "customer.lookup", null, null,
                        Map.of("type", "object",
                                "properties", Map.of("customerId", Map.of("type", "string"))),
                        Map.of("type", "object",
                                "properties", Map.of("ok", Map.of("type", "boolean"))),
                        Map.of()),
                (a, c) -> CompletableFuture.completedFuture(
                        McpToolResponse.inputRequired(Map.of("customerId", "label"), "state-1")));
        registry.register(registration);

        McpToolResponse response = new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of(), context())
                .toCompletableFuture()
                .join();

        assertThat(response.error()).isFalse();
        assertThat(response.resultType()).isEqualTo("input_required");
    }

    @Test
    @DisplayName("调用前已取消 → 抛 McpCallCancelledException 且 handler 不被调用")
    void preCancelledContextStopsBeforeHandler() {
        java.util.concurrent.atomic.AtomicBoolean called = new java.util.concurrent.atomic.AtomicBoolean();
        McpToolRegistry registry = registry((a, c) -> {
            called.set(true);
            return CompletableFuture.completedFuture(
                    new McpToolResponse(List.of(), Map.of("ok", true), false));
        });
        McpCallContext cancelled = new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                () -> true,
                null);

        assertThatThrownBy(() -> new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of("customerId", "C-1"), cancelled)
                .toCompletableFuture()
                .join())
                .isInstanceOf(McpCallCancelledException.class);
        assertThat(called).isFalse();
    }

    @Test
    @DisplayName("dispatch 时 null arguments → 抛 NPE")
    void dispatchRejectsNullArguments() {
        McpToolRegistry registry = registry((a, c) -> CompletableFuture.completedFuture(null));

        assertThatThrownBy(() -> new McpToolDispatcher(registry)
                .dispatch("customer.lookup", null, context()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("arguments");
    }

    @Test
    @DisplayName("dispatch 时 null context → 抛 NPE")
    void dispatchRejectsNullContext() {
        McpToolRegistry registry = registry((a, c) -> CompletableFuture.completedFuture(null));

        assertThatThrownBy(() -> new McpToolDispatcher(registry)
                .dispatch("customer.lookup", Map.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("context");
    }

    @Test
    @DisplayName("构造时 null registry → 抛 NPE")
    void constructorRejectsNullRegistry() {
        assertThatThrownBy(() -> new McpToolDispatcher(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("registry");
    }

    private static McpToolRegistry registry(
            cn.richie696.component.mcp.api.server.McpToolHandler handler) {
        return registryWith(handler, Map.of());
    }

    private static McpToolRegistry registryWithPolicies(Map<String, Object> annotations) {
        return registryWith(successHandler(), annotations);
    }

    private static McpToolRegistry registryWith(
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

    private static cn.richie696.component.mcp.api.server.McpToolHandler successHandler() {
        return (a, c) -> CompletableFuture.completedFuture(
                new McpToolResponse(List.of(), Map.of("ok", true), false));
    }

    private static Map<String, Object> annotationsWithMixedPolicies() {
        Map<Object, Object> mixed = new java.util.LinkedHashMap<>();
        mixed.put(123, "drop");
        mixed.put("keep", "value");
        java.util.Map<String, Object> annotations = new java.util.LinkedHashMap<>();
        annotations.put("policies", mixed);
        return annotations;
    }

    private static McpCallContext context() {
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