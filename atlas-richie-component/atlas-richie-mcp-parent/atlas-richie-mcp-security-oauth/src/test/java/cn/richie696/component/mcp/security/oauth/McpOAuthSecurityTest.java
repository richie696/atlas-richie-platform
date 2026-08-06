package cn.richie696.component.mcp.security.oauth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpOAuthSecurityTest {
    @Test
    void tokenProducesBearerHeaderAndExpiryIsClockSkewAware() {
        McpOAuthAccessToken token = new McpOAuthAccessToken(
                "secret", "Bearer", Instant.now().plusSeconds(30),
                "https://issuer.example", "https://mcp.example", Set.of("tools.read"));

        assertThat(token.authorizationHeader()).isEqualTo("Bearer secret");
        assertThat(token.expired(java.time.Duration.ofSeconds(5))).isFalse();
        assertThat(McpOAuthHeaders.unauthorizedChallenge(
                "https://mcp.example/.well-known/oauth-protected-resource", List.of("tools.read")))
                .contains("resource_metadata=\"https://mcp.example/.well-known/oauth-protected-resource\"")
                .contains("scope=\"tools.read\"");
    }

    @Test
    void defaultUriPolicyRejectsNonHttpsAndCredentials() {
        McpOAuthUriPolicy policy = McpOAuthUriPolicy.httpsOnly();

        assertThatThrownBy(() -> policy.validate(URI.create("http://issuer.example/meta")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.validate(URI.create("https://user:pass@issuer.example/meta")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pkceAuthorizationRequestUsesS256() {
        String verifier = McpOAuthPkce.generateVerifier();
        String challenge = McpOAuthPkce.challenge(verifier);
        assertThat(McpOAuthPkce.verify(verifier, challenge)).isTrue();
        assertThat(McpOAuthPkce.verify("different", challenge)).isFalse();
        URI authorization = new McpOAuthAuthorizationRequest(
                URI.create("https://idp.example/authorize"),
                "client",
                URI.create("https://client.example/callback"),
                "tools.read",
                "https://mcp.example/mcp",
                "state-1",
                challenge).toUri();
        assertThat(authorization.toString())
                .contains("response_type=code")
                .contains("code_challenge_method=S256")
                .contains("state=state-1");
    }
}
