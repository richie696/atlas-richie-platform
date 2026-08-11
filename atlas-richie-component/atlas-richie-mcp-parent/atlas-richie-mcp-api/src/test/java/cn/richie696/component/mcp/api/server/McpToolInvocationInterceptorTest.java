package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolInvocationInterceptor} 的 order() 默认值与典型实现模式（前置 → chain.proceed → 后置）。
 */
@DisplayName("McpToolInvocationInterceptor 拦截器扩展点")
class McpToolInvocationInterceptorTest {

    @Test
    @DisplayName("order() 默认返回 0")
    void defaultOrderShouldBeZero() {
        McpToolInvocationInterceptor interceptor = (inv, chain) -> chain.proceed(inv);

        assertThat(interceptor.order()).isEqualTo(0);
    }

    @Test
    @DisplayName("覆盖 order()：自定义排序值生效")
    void shouldAllowCustomOrder() {
        McpToolInvocationInterceptor interceptor = new McpToolInvocationInterceptor() {
            @Override
            public int order() {
                return -100;
            }

            @Override
            public CompletionStage<McpToolResponse> intercept(McpToolInvocation invocation, McpToolInvocationChain chain) {
                return chain.proceed(invocation);
            }
        };

        assertThat(interceptor.order()).isEqualTo(-100);
    }

    @Test
    @DisplayName("典型模式：前置 → chain.proceed → 后置处理")
    void shouldSupportPrePostPattern() throws Exception {
        AtomicReference<String> trace = new AtomicReference<>();
        McpToolInvocationInterceptor interceptor = (inv, chain) -> {
            trace.set("before");
            return chain.proceed(inv).thenApply(response -> {
                trace.set(trace.get() + ":after");
                return new McpToolResponse(
                        List.of(Map.of("decorated", true)),
                        response.structuredContent(),
                        response.error());
            });
        };

        McpToolInvocation invocation = createInvocation();
        McpToolInvocationChain chain = inv -> CompletableFuture.completedFuture(
                new McpToolResponse(List.of(Map.of("text", "ok")), null, false));

        McpToolResponse response = interceptor.intercept(invocation, chain).toCompletableFuture().get();

        assertThat(trace.get()).isEqualTo("before:after");
        assertThat(response.content().get(0)).containsEntry("decorated", true);
    }

    @Test
    @DisplayName("短路：拦截器不调用 chain.proceed 时返回自定义结果")
    void shouldAllowShortCircuit() throws Exception {
        McpToolInvocationInterceptor blocker = (inv, chain) -> CompletableFuture.completedFuture(
                new McpToolResponse(List.of(Map.of("text", "blocked")), null, true));

        McpToolInvocation invocation = createInvocation();
        McpToolInvocationChain chain = inv -> {
            throw new AssertionError("chain.proceed should not be called when short-circuited");
        };

        McpToolResponse response = blocker.intercept(invocation, chain).toCompletableFuture().get();
        assertThat(response.error()).isTrue();
        assertThat(response.content().get(0)).containsEntry("text", "blocked");
    }

    private static McpToolInvocation createInvocation() {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "noop", null, null, Map.of(), Map.of(), Map.of());
        return new McpToolInvocation(
                descriptor,
                Map.of("k", "v"),
                new McpCallContext("r", "v", "t", "s", null, Map.of(), null, null),
                Duration.ofSeconds(1),
                Map.of("p", "v"),
                (args, ctx) -> CompletableFuture.completedFuture(
                        new McpToolResponse(List.of(), null, false)));
    }
}
