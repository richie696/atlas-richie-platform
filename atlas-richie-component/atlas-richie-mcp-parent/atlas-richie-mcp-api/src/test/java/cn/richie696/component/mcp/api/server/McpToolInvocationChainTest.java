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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolInvocationChain} 作为函数式接口的 proceed(invocation) 语义。
 */
@DisplayName("McpToolInvocationChain 拦截器推进句柄")
class McpToolInvocationChainTest {

    @Test
    @DisplayName("lambda 实现：proceed 把 invocation 交给下一节点并返回结果")
    void shouldForwardInvocation() throws Exception {
        McpToolInvocation invocation = InvocationFactory.create();
        McpToolInvocationChain chain = inv -> {
            assertThat(inv).isSameAs(invocation);
            return CompletableFuture.completedFuture(
                    new McpToolResponse(List.of(Map.of("text", "ok")), null, false));
        };

        McpToolResponse response = chain.proceed(invocation).toCompletableFuture().get();
        assertThat(response.error()).isFalse();
    }

    @Test
    @DisplayName("lambda 实现：返回 failedFuture 也能被消费")
    void shouldAllowFailingChain() {
        McpToolInvocationChain chain = inv -> CompletableFuture.failedFuture(
                new IllegalStateException("blocked"));

        CompletionStage<McpToolResponse> stage = chain.proceed(InvocationFactory.create());
        assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    }

    private static final class InvocationFactory {
        static McpToolInvocation create() {
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

        private InvocationFactory() { }
    }
}
