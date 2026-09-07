package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * MCP Client 获取访问令牌的稳定 SPI。具体 PKCE、refresh 或 client credentials 流程由平台实现。
 *
 * <p>作为 MCP Client Starter 与具体 OAuth 流程之间的解耦点，本接口定义了一个最小契约：
 * 给定资源与所需 scopes，返回当前可用的 access token（异步）。实现侧可以是
 * {@link McpOAuthTokenManager}（典型进程内自刷新实现），也可以是用户自定义的外部
 * secret store / vault 适配器。</p>
 *
 * <p>为何返回 {@link CompletionStage}：MCP 客户端可能运行在虚拟线程上，访问令牌有时
 * 需要异步获取（如远程 vault、网络 AS 刷新），用 {@code CompletionStage} 既兼容同步
 * 实现（直接 {@link java.util.concurrent.CompletableFuture#completedFuture}），也兼容响应式 reactive 风格，
 * 避免在 SPI 层绑定单一并发模型。</p>
 *
 * <p>{@link Optional#empty()} 的语义：当调用方提供的 resource 不在当前 provider 的管理
 * 范围内（例如跨租户、跨资源）时，provider 必须返回 {@code empty}，由 MCP runtime
 * 决定是否触发新一次授权码流。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpOAuthTokenProvider {
    /**
     * 获取适用于指定资源的访问令牌。
     *
     * @param resource 受众资源指示符（RFC 8707），可为空
     * @param requiredScopes 调用方需要的 scope 集合，可为空
     * @return 已完成阶段：当前可用 token 或 {@link Optional#empty()}（资源不匹配）
     */
    CompletionStage<Optional<McpOAuthAccessToken>> tokenFor(URI resource, Set<String> requiredScopes);
}
