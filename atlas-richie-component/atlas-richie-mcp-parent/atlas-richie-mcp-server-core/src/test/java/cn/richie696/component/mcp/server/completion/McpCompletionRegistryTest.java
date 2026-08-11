package cn.richie696.component.mcp.server.completion;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.server.McpCompletionHandler;
import cn.richie696.component.mcp.api.server.McpCompletionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpCompletionRegistry} 的极简语义：handler 必填校验、
 * {@link #handler()} 返回原引用。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpCompletionRegistry 测试")
class McpCompletionRegistryTest {

    @Test
    @DisplayName("构造时 null handler 抛 NPE")
    void rejectsNullHandler() {
        assertThatThrownBy(() -> new McpCompletionRegistry(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler");
    }

    @Test
    @DisplayName("handler() 返回构造时传入的原引用")
    void handlerAccessorReturnsSameInstance() {
        McpCompletionHandler handler = (request, context) -> CompletableFuture.completedFuture(
                new McpCompletionResult(List.of("a", "b"), 2, false));

        McpCompletionRegistry registry = new McpCompletionRegistry(handler);

        assertThat(registry.handler()).isSameAs(handler);
    }

    @Test
    @DisplayName("handler() 返回的实例可以被正常调用（验证链路通畅）")
    void handlerIsCallable() throws Exception {
        McpCompletionHandler handler = (request, context) ->
                CompletableFuture.completedFuture(new McpCompletionResult(
                        List.of(request.value().toUpperCase()), 1, false));
        McpCompletionRegistry registry = new McpCompletionRegistry(handler);
        McpCompletionRequest request = new McpCompletionRequest(
                Map.of("type", "tool"), "argument", "abc", Map.of());

        McpCompletionResult result = registry.handler().complete(request, context()).toCompletableFuture().get();

        assertThat(result.values()).containsExactly("ABC");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.hasMore()).isFalse();
    }

    @SuppressWarnings("unused")
    private static McpCallContext context() {
        return null;
    }
}