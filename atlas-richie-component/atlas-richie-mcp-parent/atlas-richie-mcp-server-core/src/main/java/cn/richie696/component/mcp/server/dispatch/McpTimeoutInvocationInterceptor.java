package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolInvocation;
import cn.richie696.component.mcp.api.server.McpToolInvocationChain;
import cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor;
import cn.richie696.component.mcp.protocol.McpProtocolException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Applies a per-tool deadline without exposing scheduler details to business handlers. */
/**
 * Tool 调用超时拦截器：把每次调用的执行时长控制在声明的 deadline 之内。
 *
 * <p>以 {@link java.util.concurrent.CompletableFuture#orTimeout(long, TimeUnit)}
 * 包装下游阶段：触发时把 {@link java.util.concurrent.TimeoutException} 转写为
 * MCP 协议错误 {@code MCP_TOOL_TIMEOUT}（错误码 -32001，遵循 JSON-RPC server-error 区间），
 * 业务方既能看到友好的错误码也能从 {@code data.tool} 字段定位到具体工具。
 * 不在业务 handler 中引入额外调度器，确保统一的超时语义。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpTimeoutInvocationInterceptor implements McpToolInvocationInterceptor {
    /**
     * 排序：让超时拦截器先于绝大多数治理拦截器执行，确保 deadline 计时尽早启动。
     *
     * @return 排序值，越小越先执行
     */
    @Override
    public int order() {
        return Integer.MIN_VALUE + 100;
    }

    @Override
    public CompletionStage<McpToolResponse> intercept(
            McpToolInvocation invocation,
            McpToolInvocationChain chain) {
        CompletionStage<McpToolResponse> stage = chain.proceed(invocation);
        if (invocation.timeout() == null) return stage;
        CompletableFuture<McpToolResponse> result = new CompletableFuture<>();
        stage.toCompletableFuture()
                .orTimeout(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((response, throwable) -> {
                    if (throwable == null) {
                        result.complete(response);
                    } else {
                        Throwable cause = unwrap(throwable);
                        if (cause instanceof java.util.concurrent.TimeoutException) {
                            result.completeExceptionally(new McpProtocolException(
                                    "MCP_TOOL_TIMEOUT",
                                    -32001,
                                    "Tool execution timed out",
                                    Map.of("tool", invocation.tool().name())));
                        } else {
                            result.completeExceptionally(cause);
                        }
                    }
                });
        return result;
    }

    private Throwable unwrap(Throwable throwable) {
        return throwable instanceof java.util.concurrent.CompletionException
                && throwable.getCause() != null ? throwable.getCause() : throwable;
    }
}
