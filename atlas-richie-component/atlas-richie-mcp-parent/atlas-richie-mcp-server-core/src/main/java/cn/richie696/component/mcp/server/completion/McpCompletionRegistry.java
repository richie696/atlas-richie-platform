package cn.richie696.component.mcp.server.completion;

import cn.richie696.component.mcp.api.server.McpCompletionHandler;

import java.util.Objects;

/** Single completion provider per server endpoint. */
/**
 * 每个 MCP 服务端点对应一个的补全（Completion）注册容器：持有补全提供者。
 *
 * <p>MCP 协议允许服务端声明一个补全能力，用于在客户端 UI（如 IDE 输入框）
 * 中按当前输入提供候选项。本类作为最薄一层容器，对外暴露只读
 * {@link #handler()}；按设计每个服务端点只允许一个补全策略，
 * 避免出现"多个补全结果拼接歧义"。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpCompletionRegistry {
    private final McpCompletionHandler handler;

    /**
     * 构造补全注册容器。
     *
     * @param handler 补全提供者，不能为 null
     * @throws NullPointerException 当 handler 为 null 时
     */
    public McpCompletionRegistry(McpCompletionHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * 返回当前端点绑定的补全提供者。
     *
     * @return 非 null 的 {@link McpCompletionHandler}
     */
    public McpCompletionHandler handler() {
        return handler;
    }
}
