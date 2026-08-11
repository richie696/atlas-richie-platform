package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpPromptHandler} 作为函数式接口可被 lambda 直接实现并异步返回 Prompt 内容。
 */
@DisplayName("McpPromptHandler Prompt 渲染处理端口")
class McpPromptHandlerTest {

    @Test
    @DisplayName("lambda 实现：按 arguments/context 返回多轮消息")
    void shouldRenderPrompt() throws Exception {
        McpCallContext context = new McpCallContext("r", "v", "t", "s", null, Map.of(), null, null);
        McpPromptHandler handler = (args, ctx) -> {
            assertThat(args).containsEntry("topic", "AI");
            assertThat(ctx).isSameAs(context);
            return CompletableFuture.completedFuture(new McpPromptContent(
                    "summary prompt",
                    List.of(
                            Map.of("role", "system", "content", "you are helpful"),
                            Map.of("role", "user", "content", "summarize AI"))));
        };

        CompletionStage<McpPromptContent> stage = handler.get(Map.of("topic", "AI"), context);

        McpPromptContent prompt = stage.toCompletableFuture().get();
        assertThat(prompt.description()).isEqualTo("summary prompt");
        assertThat(prompt.messages()).hasSize(2);
    }
}
