package cn.richie696.component.mcp.security.oauth;

import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import cn.richie696.component.oauth.test.OAuthTestAssertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
