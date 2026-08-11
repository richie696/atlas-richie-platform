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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpOperations} 抽象方法签名、default 方法失败语义以及 lambda 实现路径。
 */
@DisplayName("McpOperations 预配置型 MCP Client 操作面")
class McpOperationsTest {

    /**
     * 用 lambda 实现所有方法，便于聚焦测试 default 行为（未重写时返回 failedFuture）。
     */
    private static final McpOperations FULL_OPS = new McpOperations() {
        @Override
        public CompletionStage<List<McpToolDescriptor>> listTools(String serverId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpToolResponse> callTool(String serverId, String toolName, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpToolResponse(List.of(), null, false));
        }

        @Override
        public CompletionStage<List<McpResourceDescriptor>> listResources(String serverId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpResourceContent> readResource(String serverId, String uri) {
            return CompletableFuture.completedFuture(new McpResourceContent(List.of()));
        }

        @Override
        public CompletionStage<List<McpPromptDescriptor>> listPrompts(String serverId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpPromptContent> getPrompt(String serverId, String name, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpPromptContent("d", List.of()));
        }
    };

    @Test
    @DisplayName("实现所有抽象方法后 listTools/callTool/listResources/readResource/listPrompts/getPrompt 正常返回")
    void shouldInvokeImplementedAbstractMethods() throws Exception {
        assertThat(FULL_OPS.listTools("svc").toCompletableFuture().get()).isEmpty();
        assertThat(FULL_OPS.callTool("svc", "t", Map.of()).toCompletableFuture().get())
                .extracting(McpToolResponse::error).isEqualTo(false);
        assertThat(FULL_OPS.listResources("svc").toCompletableFuture().get()).isEmpty();
        assertThat(FULL_OPS.readResource("svc", "uri").toCompletableFuture().get())
                .extracting(McpResourceContent::contents).isEqualTo(List.of());
        assertThat(FULL_OPS.listPrompts("svc").toCompletableFuture().get()).isEmpty();
        assertThat(FULL_OPS.getPrompt("svc", "p", Map.of()).toCompletableFuture().get())
                .extracting(McpPromptContent::description).isEqualTo("d");
    }

    @Nested
    @DisplayName("default 方法：未重写时返回 failedFuture")
    class DefaultMethods {

        private final McpOperations bare = new McpOperations() {
            @Override
            public CompletionStage<List<McpToolDescriptor>> listTools(String serverId) {
                throw new UnsupportedOperationException("abstract");
            }

            @Override
            public CompletionStage<McpToolResponse> callTool(String serverId, String toolName, Map<String, Object> arguments) {
                throw new UnsupportedOperationException("abstract");
            }

            @Override
            public CompletionStage<List<McpResourceDescriptor>> listResources(String serverId) {
                throw new UnsupportedOperationException("abstract");
            }

            @Override
            public CompletionStage<McpResourceContent> readResource(String serverId, String uri) {
                throw new UnsupportedOperationException("abstract");
            }

            @Override
            public CompletionStage<List<McpPromptDescriptor>> listPrompts(String serverId) {
                throw new UnsupportedOperationException("abstract");
            }

            @Override
            public CompletionStage<McpPromptContent> getPrompt(String serverId, String name, Map<String, Object> arguments) {
                throw new UnsupportedOperationException("abstract");
            }
        };

        @Test
        @DisplayName("listResourceTemplates 默认返回 failed Future")
        void shouldFailResourceTemplatesByDefault() {
            CompletionStage<List<McpResourceTemplateDescriptor>> stage = bare.listResourceTemplates("svc");
            assertThatThrownBy(() -> stage.toCompletableFuture().join())
                    .isInstanceOf(java.util.concurrent.CompletionException.class)
                    .hasCauseInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Resource templates");
        }

        @Test
        @DisplayName("complete 默认返回 failed Future")
        void shouldFailCompleteByDefault() {
            CompletionStage<McpCompletionResult> stage = bare.complete("svc", Map.of(), "arg", "v", Map.of());
            assertThatThrownBy(() -> stage.toCompletableFuture().join())
                    .isInstanceOf(java.util.concurrent.CompletionException.class)
                    .hasCauseInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Completions");
        }

        @Test
        @DisplayName("listResourceTemplates 允许子类覆盖并返回模板列表")
        void shouldAllowOverrideForTemplates() throws Exception {
            McpOperations withTemplates = new TemplateSupportingOps();
            assertThat(withTemplates.listResourceTemplates("svc").toCompletableFuture().get()).isEmpty();
        }
    }

    /**
     * 一个全实现的 {@link McpOperations}，仅用于覆盖 {@code listResourceTemplates}。
     */
    private static final class TemplateSupportingOps implements McpOperations {
        @Override
        public CompletionStage<List<McpToolDescriptor>> listTools(String serverId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpToolResponse> callTool(String serverId, String toolName, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpToolResponse(List.of(), null, false));
        }

        @Override
        public CompletionStage<List<McpResourceDescriptor>> listResources(String serverId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpResourceContent> readResource(String serverId, String uri) {
            return CompletableFuture.completedFuture(new McpResourceContent(List.of()));
        }

        @Override
        public CompletionStage<List<McpPromptDescriptor>> listPrompts(String serverId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletionStage<McpPromptContent> getPrompt(String serverId, String name, Map<String, Object> arguments) {
            return CompletableFuture.completedFuture(new McpPromptContent("d", List.of()));
        }

        @Override
        public CompletionStage<List<McpResourceTemplateDescriptor>> listResourceTemplates(String serverId) {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
