package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpResourceHandler} 作为函数式接口可被 lambda 直接实现并异步返回 Resource 内容。
 */
@DisplayName("McpResourceHandler Resource 读取处理端口")
class McpResourceHandlerTest {

    @Test
    @DisplayName("lambda 实现：按 uri/context 返回 Resource 内容")
    void shouldReturnContent() throws Exception {
        McpCallContext context = new McpCallContext("r", "v", "t", "s", null, Map.of(), null, null);
        McpResourceHandler handler = (uri, ctx) -> {
            assertThat(uri).isEqualTo("file:///a.txt");
            assertThat(ctx).isSameAs(context);
            return CompletableFuture.completedFuture(new McpResourceContent(
                    java.util.List.of(Map.of("text", "hello"))));
        };

        CompletionStage<McpResourceContent> stage = handler.read("file:///a.txt", context);

        McpResourceContent content = stage.toCompletableFuture().get();
        assertThat(content.contents()).hasSize(1);
    }

    @Test
    @DisplayName("异步失败：返回 failedFuture 也合法")
    void shouldAllowFailingFuture() {
        McpResourceHandler handler = (uri, ctx) -> CompletableFuture.failedFuture(
                new IllegalAccessException("denied"));

        CompletionStage<McpResourceContent> stage = handler.read("file:///", null);
        assertThat(stage.toCompletableFuture().isCompletedExceptionally()).isTrue();
    }
}
