package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpToolInvocation} 紧凑构造器对 tool/context/handler 的必填校验，
 * 以及对 arguments/policies 的不可变拷贝。
 */
@DisplayName("McpToolInvocation 调用快照 record")
class McpToolInvocationTest {

    private static McpToolInvocation create() {
        return new McpToolInvocation(
                new McpToolDescriptor("noop", null, null, Map.of(), Map.of(), Map.of()),
                Map.of("k", "v"),
                new McpCallContext("r", "v", "t", "s", null, Map.of(), null, null),
                Duration.ofSeconds(1),
                Map.of("p", "v"),
                (args, ctx) -> CompletableFuture.completedFuture(
                        new McpToolResponse(List.of(), null, false)));
    }

    @Test
    @DisplayName("完整构造：暴露全部字段并对 Map 做不可变拷贝")
    void shouldExposeAllFields() {
        McpToolInvocation invocation = create();

        assertThat(invocation.tool().name()).isEqualTo("noop");
        assertThat(invocation.arguments()).containsEntry("k", "v");
        assertThat(invocation.context().tenantId()).isEqualTo("t");
        assertThat(invocation.timeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(invocation.policies()).containsEntry("p", "v");
        assertThat(invocation.handler()).isNotNull();
    }

    @Test
    @DisplayName("arguments/policies 不可变：外部突变不影响内部")
    void shouldDefensivelyCopyArgumentsAndPolicies() {
        Map<String, Object> mutableArgs = new LinkedHashMap<>(Map.of("k", "v"));
        Map<String, Object> mutablePolicies = new LinkedHashMap<>(Map.of("p", "v"));
        McpToolInvocation invocation = new McpToolInvocation(
                new McpToolDescriptor("noop", null, null, null, null, null),
                mutableArgs,
                new McpCallContext("r", "v", "t", "s", null, Map.of(), null, null),
                Duration.ofSeconds(1),
                mutablePolicies,
                (args, ctx) -> CompletableFuture.completedFuture(
                        new McpToolResponse(List.of(), null, false)));

        mutableArgs.put("k2", "v2");
        mutablePolicies.put("p2", "v2");

        assertThat(invocation.arguments()).containsOnlyKeys("k");
        assertThat(invocation.policies()).containsOnlyKeys("p");
    }

    @Nested
    @DisplayName("必填校验")
    class RequiredFieldValidation {

        @Test
        @DisplayName("null tool 抛 NullPointerException")
        void shouldRejectNullTool() {
            assertThatThrownBy(() -> new McpToolInvocation(
                    null, Map.of(), ctx(), Duration.ofSeconds(1), Map.of(), handler()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tool");
        }

        @Test
        @DisplayName("null context 抛 NullPointerException")
        void shouldRejectNullContext() {
            assertThatThrownBy(() -> new McpToolInvocation(
                    new McpToolDescriptor("noop", null, null, null, null, null),
                    Map.of(), null, Duration.ofSeconds(1), Map.of(), handler()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("context");
        }

        @Test
        @DisplayName("null handler 抛 NullPointerException")
        void shouldRejectNullHandler() {
            assertThatThrownBy(() -> new McpToolInvocation(
                    new McpToolDescriptor("noop", null, null, null, null, null),
                    Map.of(), ctx(), Duration.ofSeconds(1), Map.of(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("handler");
        }
    }

    @Test
    @DisplayName("arguments/policies 为 null 时回落到不可变空 Map")
    void shouldFallbackNullMaps() {
        McpToolInvocation invocation = new McpToolInvocation(
                new McpToolDescriptor("noop", null, null, null, null, null),
                null, ctx(), Duration.ofSeconds(1), null, handler());

        assertThat(invocation.arguments()).isEmpty();
        assertThat(invocation.policies()).isEmpty();
    }

    @Test
    @DisplayName("handler 可被调用并返回 Tool 响应")
    void shouldInvokeHandler() throws Exception {
        McpToolInvocation invocation = create();
        CompletionStage<McpToolResponse> stage = invocation.handler().handle(Map.of(), invocation.context());
        assertThat(stage.toCompletableFuture().get().error()).isFalse();
    }

    private static McpCallContext ctx() {
        return new McpCallContext("r", "v", "t", "s", null, Map.of(), null, null);
    }

    private static McpToolHandler handler() {
        return (args, ctx) -> CompletableFuture.completedFuture(
                new McpToolResponse(List.of(), null, false));
    }
}
