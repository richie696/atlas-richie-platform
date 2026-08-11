package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpCompletionResult;

import java.util.concurrent.CompletionStage;

/**
 * 参数自动补全的处理端口。
 *
 * <p>对应 MCP 协议的 {@code completion/complete} 端点：Client 在用户输入过程中查询
 * 某参数（如城市名）的候选值。实现方应基于 {@link McpCompletionRequest} 中的当前已输入字符串
 * 与上下文参数，异步返回排序后的候选集合。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpCompletionHandler {
    /**
     * 执行一次补全查询。
     *
     * @param request 补全请求（含被补全参数名、当前已输入字符串、上下文参数）
     * @param context 业务调用上下文
     * @return 补全结果的异步结果
     */
    CompletionStage<McpCompletionResult> complete(McpCompletionRequest request, McpCallContext context);
}
