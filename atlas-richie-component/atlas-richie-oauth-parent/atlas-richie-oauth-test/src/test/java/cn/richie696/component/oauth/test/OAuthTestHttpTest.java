package cn.richie696.component.oauth.test;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTestHttpTest {

    @Test
    void buildsAndParsesAuthorizationQuery() {
        var fixture = OAuthTestFixtures.defaultAuthorizationCodeRequest("read", "write");
        URI uri = OAuthTestHttp.authorizationUri(
                URI.create("https://as.example.test/oauth2/authorize"), fixture.request());

        Map<String, String> parameters = OAuthTestHttp.queryParameters(uri);

        assertThat(parameters)
                .containsEntry("client_id", OAuthTestFixtures.DEFAULT_CLIENT_ID)
                .containsEntry("redirect_uri", OAuthTestFixtures.DEFAULT_REDIRECT_URI)
                .containsEntry("scope", "read write")
                .containsEntry("code_challenge_method", "S256");
    }

    @Test
    void buildsTokenFormWithoutNullParameters() {
        var request = OAuthTestFixtures.clientCredentialsTokenRequest(
                "client-1", "secret-1", "read", null);

        assertThat(OAuthTestHttp.tokenForm(request))
                .containsEntry("grant_type", "client_credentials")
                .containsEntry("client_id", "client-1")
                .doesNotContainKey("resource")
                .doesNotContainKey("code");
    }
}
