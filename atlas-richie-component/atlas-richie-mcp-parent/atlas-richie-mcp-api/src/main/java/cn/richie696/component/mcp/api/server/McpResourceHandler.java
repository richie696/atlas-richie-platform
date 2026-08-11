package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.model.McpResourceContent;

import java.util.concurrent.CompletionStage;

/**
 * Resource 读取的处理端口。
 *
 * <p>对应 MCP 协议的 {@code resources/read} 端点：根据 URI 异步返回 Resource 内容。
 * 一个 {@code McpResourceHandler} 实例可以负责一类 URI（前缀匹配），由框架根据 URI
 * 路由到具体实现。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpResourceHandler {
    /**
     * 读取一份 Resource。
     *
     * @param uri 资源 URI
     * @param context 业务调用上下文
     * @return Resource 内容的异步结果
     */
    CompletionStage<McpResourceContent> read(String uri, McpCallContext context);
}
