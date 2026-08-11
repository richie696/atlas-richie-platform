package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpPromptContent;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Prompt 渲染的处理端口。
 *
 * <p>对应 MCP 协议的 {@code prompts/get} 端点：把模板名 + 参数渲染为一组多轮消息。
 * 与 {@link McpToolHandler} 类似，这里也是函数式接口 + 异步签名，区别是返回值类型
 * 是 {@link McpPromptContent} 而非 {@link cn.richie696.component.mcp.api.model.McpToolResponse}。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpPromptHandler {
    /**
     * 渲染一次 Prompt。
     *
     * @param arguments 模板参数
     * @param context 业务调用上下文
     * @return 多轮消息内容的异步结果
     */
    CompletionStage<McpPromptContent> get(Map<String, Object> arguments, McpCallContext context);
}
