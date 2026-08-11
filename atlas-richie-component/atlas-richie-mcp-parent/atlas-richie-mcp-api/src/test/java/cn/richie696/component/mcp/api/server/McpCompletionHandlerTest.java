package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpCompletionHandler} 作为函数式接口可被 lambda 直接实现并异步返回补全结果。
 */
@DisplayName("McpCompletionHandler 参数补全处理端口")
class McpCompletionHandlerTest {

    @Test
    @DisplayName("lambda 实现：按 request/context 返回补全结果")
    void shouldReturnCompletion() throws Exception {
        McpCallContext context = new McpCallContext("r", "v", "t", "s", null, Map.of(), null, null);
        McpCompletionRequest request = new McpCompletionRequest(
                Map.of("type", "tool", "name", "lookup"),
                "city", "Shang",
                Map.of("country", "CN"));
        McpCompletionHandler handler = (req, ctx) -> {
            assertThat(req).isSameAs(request);
            assertThat(ctx).isSameAs(context);
            return CompletableFuture.completedFuture(new McpCompletionResult(
                    java.util.List.of("Shanghai", "Shantou"), 2, false));
        };

        CompletionStage<McpCompletionResult> stage = handler.complete(request, context);

        McpCompletionResult result = stage.toCompletableFuture().get();
        assertThat(result.values()).containsExactly("Shanghai", "Shantou");
        assertThat(result.hasMore()).isFalse();
    }
}
