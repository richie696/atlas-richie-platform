package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpToolResponse;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 业务 Tool 的稳定执行端口。
 *
 * <p>该接口是业务 Tool 方法的函数式抽象：框架把客户端调用转换为
 * {@code (arguments, context) → CompletionStage<McpToolResponse>}，业务侧只需要
 * 实现这个方法（无论是声明式 {@code @McpTool} 注解还是命令式 Provider）即可。
 * 使用 {@link CompletionStage} 而非阻塞返回的原因：MCP 协议强调异步与协作式取消，
 * 业务可能调用慢服务/等待事件，阻塞模型会与协议设计冲突。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpToolHandler {
    /**
     * 执行一次 Tool 调用。
     *
     * @param arguments 已绑定到方法声明类型的入参
     * @param context 业务调用上下文
     * @return Tool 结果的异步结果
     */
    CompletionStage<McpToolResponse> handle(Map<String, Object> arguments, McpCallContext context);
}
