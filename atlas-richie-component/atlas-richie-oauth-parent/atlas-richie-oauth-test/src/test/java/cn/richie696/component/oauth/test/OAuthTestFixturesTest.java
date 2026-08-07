package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.authz.PKCESupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTestFixturesTest {

    @Test
    void createsAuthorizationCodeRequestWithValidPkce() {
        OAuthTestFixtures.AuthorizationCodeRequest fixture =
                OAuthTestFixtures.defaultAuthorizationCodeRequest("read", "write");

        assertThat(fixture.request().clientId()).isEqualTo(OAuthTestFixtures.DEFAULT_CLIENT_ID);
        assertThat(fixture.request().scopes()).containsExactly("read", "write");
        assertThat(new PKCESupport().verifyChallenge(
                fixture.request().codeChallenge(),
                fixture.request().codeChallengeMethod(),
                fixture.codeVerifier())).isTrue();
    }

    @Test
    void createsClientCredentialsClientWithExpectedGrantType() {
        var client = OAuthTestFixtures.defaultClient("read");

        assertThat(client.getClientId()).isEqualTo(OAuthTestFixtures.DEFAULT_CLIENT_ID);
        assertThat(client.getGrantTypes()).containsExactly("client_credentials");
        assertThat(client.getScopes()).containsExactly("read");
    }

    @Test
    void createsOidcAuthorizationRequestWithOpenIdAndNonce() {
        var fixture = OAuthTestFixtures.oidcAuthorizationCodeRequest(
                OAuthTestFixtures.DEFAULT_CLIENT_ID,
                OAuthTestFixtures.DEFAULT_REDIRECT_URI,
                "profile", "email");

        assertThat(fixture.request().scopes()).containsExactly("openid", "profile", "email");
        assertThat(fixture.request().nonce()).isEqualTo("it-oidc-nonce-001");
    }
}
