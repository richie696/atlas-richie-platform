package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpToolHandler} 作为函数式接口可被 lambda 直接实现并异步返回结果。
 */
@DisplayName("McpToolHandler 业务 Tool 执行端口")
class McpToolHandlerTest {

    @Test
    @DisplayName("lambda 实现：按 arguments/context 返回 Tool 响应")
    void shouldReturnResponseFromLambda() throws Exception {
        McpCallContext context = new McpCallContext("r", "v", "t", "s", null, Map.of(), null, null);
        McpToolHandler handler = (args, ctx) -> {
            assertThat(args).containsEntry("customerId", "c-1");
            assertThat(ctx).isSameAs(context);
            return CompletableFuture.completedFuture(
                    new McpToolResponse(List.of(Map.of("text", "ok")), null, false));
        };

        CompletionStage<McpToolResponse> stage = handler.handle(Map.of("customerId", "c-1"), context);

        McpToolResponse response = stage.toCompletableFuture().get();
        assertThat(response.error()).isFalse();
        assertThat(response.resultType()).isEqualTo("complete");
    }

    @Test
    @DisplayName("异步实现：返回 failedFuture 也能被消费")
    void shouldPropagateFailedFuture() {
        McpToolHandler handler = (args, ctx) -> CompletableFuture.failedFuture(
                new IllegalStateException("downstream down"));

        CompletionStage<McpToolResponse> stage = handler.handle(Map.of(), null);
        assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    }
}
