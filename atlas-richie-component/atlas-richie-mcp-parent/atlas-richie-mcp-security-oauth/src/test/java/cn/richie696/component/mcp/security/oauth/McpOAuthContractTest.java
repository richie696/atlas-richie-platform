package cn.richie696.component.mcp.security.oauth;

import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import cn.richie696.component.oauth.test.OAuthTestAssertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 MCP OAuth 适配层与通用 OAuth 契约（{@code atlas-richie-oauth-contract}）的对齐：
 * {@link McpOAuthAccessToken} 可以被 {@link OAuthTestAssertions} 视作通用 token 校验；
 * MCP 授权请求必须携带 {@code resource}（RFC 8707）和 {@code S256} PKCE challenge；
 * 由此确保在多 AS 互通场景下凭证语义与通用 OAuth 2.1 保持一致。
 *
 * @author richie696
 * @since 2026-08-11
 */
class McpOAuthContractTest {

    @Test
    void mcpTokenCanBeValidatedBySharedOAuthContract() {
        McpOAuthAccessToken accessToken = new McpOAuthAccessToken(
                "at-1", "Bearer", Instant.now().plusSeconds(3600),
                "https://issuer.example", "https://mcp.example", Set.of("tools.read"));
        OAuthTestAssertions.assertValidToken(new OAuthTokenResponse(
                accessToken.value(), accessToken.tokenType(), 3600, null, "tools.read"));
        assertThat(accessToken.resource()).isEqualTo("https://mcp.example");
    }

    @Test
    void mcpAuthorizationRequestCarriesResourceAndS256Pkce() {
        String verifier = McpOAuthPkce.generateVerifier();
        URI authorization = new McpOAuthAuthorizationRequest(
                URI.create("https://issuer.example/authorize"), "client-1",
                URI.create("https://client.example/callback"), "tools.read",
                "https://mcp.example", "state-1", McpOAuthPkce.challenge(verifier)).toUri();
        assertThat(authorization.toString()).contains("resource=https%3A%2F%2Fmcp.example")
                .contains("code_challenge_method=S256");
    }
}
