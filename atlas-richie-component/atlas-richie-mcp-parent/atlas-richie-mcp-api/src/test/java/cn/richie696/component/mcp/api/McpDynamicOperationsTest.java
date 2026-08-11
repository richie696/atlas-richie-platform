package cn.richie696.component.mcp.api;

import cn.richie696.component.mcp.api.model.McpCompletionResult;
import cn.richie696.component.mcp.api.model.McpPromptContent;
import cn.richie696.component.mcp.api.model.McpPromptDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceContent;
import cn.richie696.component.mcp.api.model.McpResourceDescriptor;
import cn.richie696.component.mcp.api.model.McpResourceTemplateDescriptor;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpDynamicOperations} 抽象方法签名与 default 方法（listResourceTemplates / complete）的失败语义。
 */
@DisplayName("McpDynamicOperations 发现型 MCP Client 操作面")
class McpDynamicOperationsTest {

    private static final McpClientRequest REQUEST = new McpClientRequest(
            URI.create("https://mcp.local/x"), Map.of("Authorization", "Bearer t"));

    private static McpDynamicOperations fullOps() {
        return new FullOps();
    }

    @Test
    @DisplayName("实现所有抽象方法后各 endpoint 可正常返回")
    void shouldInvokeImplementedAbstractMethods() throws Exception {
        McpDynamicOperations ops = fullOps();
        assertThat(ops.listTools(REQUEST).toCompletableFuture().get()).isEmpty();
        assertThat(ops.callTool(REQUEST, "tool", Map.of("k", "v")).toCompletableFuture().get())
                .extracting(McpToolResponse::error).isEqualTo(false);
        assertThat(ops.listResources(REQUEST).toCompletableFuture().get()).isEmpty();
        assertThat(ops.readResource(REQUEST, "uri").toCompletableFuture().get())
                .extracting(McpResourceContent::contents).isEqualTo(List.of());
        assertThat(ops.listPrompts(REQUEST).toCompletableFuture().get()).isEmpty();
        assertThat(ops.getPrompt(REQUEST, "p", Map.of()).toCompletableFuture().get())
                .extracting(McpPromptContent::description).isEqualTo("d");
    }

    @Nested
    @DisplayName("default 方法：未重写时返回 failed Future")
    class DefaultMethods {

        @Test
        @DisplayName("listResourceTemplates 默认返回 failed Future")
        void shouldFailResourceTemplatesByDefault() {
            CompletionStage<List<McpResourceTemplateDescriptor>> stage =
                    fullOps().listResourceTemplates(REQUEST);
            assertThatThrownBy(() -> stage.toCompletableFuture().join())
                    .isInstanceOf(java.util.concurrent.CompletionException.class)
                    .hasCauseInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Resource templates");
        }

        @Test
        @DisplayName("complete 默认返回 failed Future")
        void shouldFailCompleteByDefault() {
            CompletionStage<McpCompletionResult> stage =
                    fullOps().complete(REQUEST, Map.of(), "arg", "v", Map.of());
            assertThatThrownBy(() -> stage.toCompletableFuture().join())
                    .isInstanceOf(java.util.concurrent.CompletionException.class)
                    .hasCauseInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Completions");
        }

        @Test
        @DisplayName("listResourceTemplates 允许子类覆盖")
        void shouldAllowOverrideForTemplates() throws Exception {
            McpDynamicOperations withTemplates = new TemplateSupportingDynamicOps();
            assertThat(withTemplates.listResourceTemplates(REQUEST).toCompletableFuture().get()).isEmpty();
        }
    }

    private static final class FullOps implements McpDynamicOperations {
        @Override
        public CompletionStage<List<McpToolDescriptor>> listTools(McpClientRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpToolResponse> callTool(McpClientRequest request, String toolName, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpToolResponse(List.of(), null, false));
        }

        @Override
        public CompletionStage<List<McpResourceDescriptor>> listResources(McpClientRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpResourceContent> readResource(McpClientRequest request, String uri) {
            return CompletableFuture.completedFuture(new McpResourceContent(List.of()));
        }

        @Override
        public CompletionStage<List<McpPromptDescriptor>> listPrompts(McpClientRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpPromptContent> getPrompt(McpClientRequest request, String name, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpPromptContent("d", List.of()));
        }
    }

    private static final class TemplateSupportingDynamicOps implements McpDynamicOperations {
        @Override
        public CompletionStage<List<McpToolDescriptor>> listTools(McpClientRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpToolResponse> callTool(McpClientRequest request, String toolName, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpToolResponse(List.of(), null, false));
        }

        @Override
        public CompletionStage<List<McpResourceDescriptor>> listResources(McpClientRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<List<McpResourceTemplateDescriptor>> listResourceTemplates(McpClientRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpResourceContent> readResource(McpClientRequest request, String uri) {
            return CompletableFuture.completedFuture(new McpResourceContent(List.of()));
        }

        @Override
        public CompletionStage<List<McpPromptDescriptor>> listPrompts(McpClientRequest request) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpPromptContent> getPrompt(McpClientRequest request, String name, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpPromptContent("d", List.of()));
        }
    }
}
