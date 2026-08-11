package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.util.concurrent.CompletionStage;

/**
 * Proceeds with the next interceptor or the business handler.
 *
 * <p>拦截器链上的"前进"句柄——实现上等价于 Spring 的 {@code FilterChain.proceed()}。
 * 当前拦截器完成前置处理后，调用 {@link #proceed(McpToolInvocation)} 让链上的下一个
 * 拦截器（或最终的 {@link McpToolHandler}）继续执行。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpToolInvocationChain {
    /**
     * 推进到链上的下一步。
     *
     * @param invocation 当前调用快照（拦截器可修改参数后传入新 invocation）
     * @return Tool 结果的异步结果
     */
    CompletionStage<McpToolResponse> proceed(McpToolInvocation invocation);
}
