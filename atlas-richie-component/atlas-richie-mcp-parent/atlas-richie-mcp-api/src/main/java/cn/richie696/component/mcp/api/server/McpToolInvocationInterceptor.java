package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.util.concurrent.CompletionStage;

/**
 * Ordered extension point around server tool invocation.
 *
 * <p>框架在 Tool 调用前后提供的统一拦截点，用于实现"鉴权/限流/审计/重试"等横切逻辑。
 * 实现方应基于 {@link #order()} 控制执行顺序：值越小越靠近外层（在 Tool 真正执行前最先
 * 触发），适合做权限校验；值越大越靠近内层（最接近业务方法），适合做性能监控。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpToolInvocationInterceptor {
    /**
     * 拦截器排序值，值越小越先执行（外层）。
     *
     * @return 排序值，默认 {@code 0}
     */
    default int order() {
        return 0;
    }

    /**
     * 拦截一次 Tool 调用。
     *
     * <p>实现典型模式：</p>
     * <pre>{@code
     * public CompletionStage<McpToolResponse> intercept(McpToolInvocation inv, McpToolInvocationChain chain) {
     *     // 前置逻辑
     *     return chain.proceed(inv).thenApply(response -> {
     *         // 后置逻辑
     *         return response;
     *     });
     * }
     * }</pre>
     *
     * @param invocation 当前调用快照
     * @param chain 拦截器链（推进到下一步）
     * @return Tool 结果的异步结果
     */
    CompletionStage<McpToolResponse> intercept(
            McpToolInvocation invocation,
            McpToolInvocationChain chain);
}
