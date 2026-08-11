package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCancellationToken;
import cn.richie696.component.mcp.api.model.McpToolDescriptor;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolInvocation;
import cn.richie696.component.mcp.api.server.McpToolInvocationChain;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.protocol.McpProtocolVersions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpTimeoutInvocationInterceptor} 的 deadline 语义：
 * 无 timeout 时直传；设置 timeout 时把 TimeoutException 转为 MCP_TOOL_TIMEOUT；
 * 非超时异常原样透传；order() 早于 audit 拦截器。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpTimeoutInvocationInterceptor 测试")
class McpTimeoutInvocationInterceptorTest {

    @Test
    @DisplayName("order() 返回 MIN_VALUE+100，确保先于 audit 拦截器执行")
    void exposesStableOrder() {
        McpTimeoutInvocationInterceptor interceptor = new McpTimeoutInvocationInterceptor();

        assertThat(interceptor.order()).isEqualTo(Integer.MIN_VALUE + 100);
    }

    @Test
    @DisplayName("无 timeout 时直接透传下游阶段")
    void passesThroughWhenNoTimeout() {
        McpTimeoutInvocationInterceptor interceptor = new McpTimeoutInvocationInterceptor();
        McpToolInvocation invocation = invocation(null);
        McpToolResponse response = successResponse();
        McpToolInvocationChain chain = inv -> CompletableFuture.completedFuture(response);

        McpToolResponse actual = interceptor.intercept(invocation, chain)
                .toCompletableFuture()
                .join();

        assertThat(actual).isSameAs(response);
    }

    @Test
    @DisplayName("设置 timeout 时把 TimeoutException 转换为 MCP_TOOL_TIMEOUT")
    void translatesTimeoutToProtocolException() {
        McpTimeoutInvocationInterceptor interceptor = new McpTimeoutInvocationInterceptor();
        McpToolInvocation invocation = invocation(Duration.ofMillis(50));
        CompletableFuture<McpToolResponse> pending = new CompletableFuture<>();
        McpToolInvocationChain chain = inv -> pending;

        CompletionStage<McpToolResponse> stage = interceptor.intercept(invocation, chain);

        assertThatThrownBy(stage.toCompletableFuture()::join)
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOfSatisfying(McpProtocolException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo("MCP_TOOL_TIMEOUT");
                    assertThat(exception.jsonRpcCode()).isEqualTo(-32001);
                    assertThat(exception.getMessage()).isEqualTo("Tool execution timed out");
                    assertThat(exception.data()).containsEntry("tool", "alpha.tool");
                });
    }

    @Test
    @DisplayName("设置 timeout 时非超时异常原样透传")
    void passesThroughNonTimeoutFailures() {
        McpTimeoutInvocationInterceptor interceptor = new McpTimeoutInvocationInterceptor();
        McpToolInvocation invocation = invocation(Duration.ofMillis(50));
        RuntimeException failure = new IllegalStateException("boom");
        McpToolInvocationChain chain = inv -> CompletableFuture.failedFuture(failure);

        assertThatThrownBy(() -> interceptor.intercept(invocation, chain)
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isSameAs(failure);
    }

    @Test
    @DisplayName("设置 timeout 且快速完成时正常返回")
    void passesThroughFastSuccess() {
        McpTimeoutInvocationInterceptor interceptor = new McpTimeoutInvocationInterceptor();
        McpToolInvocation invocation = invocation(Duration.ofSeconds(5));
        McpToolResponse response = successResponse();
        McpToolInvocationChain chain = inv -> CompletableFuture.completedFuture(response);

        McpToolResponse actual = interceptor.intercept(invocation, chain)
                .toCompletableFuture()
                .join();

        assertThat(actual).isSameAs(response);
    }

    @Test
    @DisplayName("下游若已包了一层 CompletionException + TimeoutException，依然能正确转换为协议错误")
    void translatesCompletionWrappedTimeout() {
        McpTimeoutInvocationInterceptor interceptor = new McpTimeoutInvocationInterceptor();
        McpToolInvocation invocation = invocation(Duration.ofMillis(25));
        McpToolInvocationChain chain = inv -> CompletableFuture.failedFuture(
                new CompletionException(new TimeoutException("from downstream")));

        assertThatThrownBy(() -> interceptor.intercept(invocation, chain)
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .cause()
                .isInstanceOf(McpProtocolException.class)
                .extracting("errorCode").isEqualTo("MCP_TOOL_TIMEOUT");
    }

    private static McpToolResponse successResponse() {
        return new McpToolResponse(
                List.of(Map.of("type", "text", "text", "ok")),
                Map.of("ok", true), false);
    }

    private static McpToolInvocation invocation(Duration timeout) {
        McpToolDescriptor descriptor = new McpToolDescriptor(
                "alpha.tool", "Alpha", "test",
                Map.of("type", "object", "properties", Map.of()),
                Map.of(),
                Map.of());
        McpCallContext context = new McpCallContext(
                "request-1",
                McpProtocolVersions.V_2026_07_28,
                "tenant-1",
                "alice",
                null,
                Map.of(),
                McpCancellationToken.NONE,
                null);
        return new McpToolInvocation(
                descriptor, Map.of(), context, timeout, Map.of(),
                (a, c) -> CompletableFuture.completedFuture(successResponse()));
    }
}