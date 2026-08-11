package cn.richie696.component.mcp.api.server;

import cn.richie696.component.mcp.api.McpCallContext;

/**
 * Resolves tenant, principal, scopes and other business context for a server request.
 *
 * <p>该工厂是 Server 端"MCP 协议层 → 业务上下文层"的桥：协议层只提供 HTTP header、请求 ID
 * 等透传数据；租户/主体/Scope 等业务信息需要工厂从凭据/Token 中解析得到。这样设计的目的是
 * 让 {@code atlas-richie-mcp-server} 不依赖具体鉴权实现，由业务侧注入自己的
 * {@code McpCallContextFactory} 即可接入 OAuth/JWT/API Key 等不同方案。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpCallContextFactory {
    /**
     * 根据协议层请求生成业务调用上下文。
     *
     * @param request 协议层不可变请求数据
     * @return 业务调用上下文（非 {@code null}）
     */
    McpCallContext create(McpServerCallContextRequest request);

    /**
     * 创建一个把所有主体视为 {@code anonymous} 的工厂。
     *
     * <p>该工厂仅透传协议层字段，把 tenant/subject 都硬编码为 {@code "anonymous"}，
     * 适用于本地调试、无鉴权要求的演示场景；生产环境应当由业务侧提供真实实现。</p>
     *
     * @return 匿名工厂实例
     */
    static McpCallContextFactory anonymous() {
        return request -> new McpCallContext(
                request.requestId(),
                request.protocolVersion(),
                "anonymous",
                "anonymous",
                request.defaultDeadline(),
                request.attributes(),
                request.cancellationToken(),
                request.progressReporter());
    }
}
