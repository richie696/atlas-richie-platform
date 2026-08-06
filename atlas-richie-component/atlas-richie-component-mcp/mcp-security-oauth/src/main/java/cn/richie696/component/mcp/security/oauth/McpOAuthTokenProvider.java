package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * MCP Client 获取访问令牌的稳定 SPI。具体 PKCE、refresh 或 client credentials 流程由平台实现。
 */
@FunctionalInterface
public interface McpOAuthTokenProvider {
    CompletionStage<Optional<McpOAuthAccessToken>> tokenFor(URI resource, Set<String> requiredScopes);
}
