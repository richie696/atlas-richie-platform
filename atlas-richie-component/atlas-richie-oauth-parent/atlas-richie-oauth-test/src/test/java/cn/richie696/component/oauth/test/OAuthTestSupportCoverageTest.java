package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 覆盖 oauth-test 对外提供的协议断言、夹具与黑盒 HTTP 工具边界。 */
class OAuthTestSupportCoverageTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void validatesTokenIntrospectionRedirectAndErrorContracts() {
        OAuthTestAssertions.assertValidToken(
                new OAuthTokenResponse("access", "Bearer", 60, "refresh", "read"));
        OAuthTestAssertions.assertValidToken(
                new cn.richie696.component.oauth.core.model.TokenResponse(
                        "access", "Bearer", 60L, "refresh", "read"));
        OAuthTestAssertions.assertActive(
                new OAuthIntrospectionResponse(true, "client-1", "Bearer", "read", "user-1",
                        "https://issuer", "api", 10, 1, "jti", Map.of()), "client-1");
        OAuthTestAssertions.assertInactive(OAuthIntrospectionResponse.inactive());
        OAuthTestAssertions.assertAuthorizationSuccessRedirect(
                URI.create("https://client.example/callback?code=abc&state=s1"), "s1");
        OAuthTestAssertions.assertOAuthError(Map.of("error", "invalid_request"), "invalid_request");

        assertThatThrownBy(() -> OAuthTestAssertions.assertValidToken(
                new OAuthTokenResponse("", "Bearer", 60, null, null)))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> OAuthTestAssertions.assertActive(
                OAuthIntrospectionResponse.inactive(), null))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> OAuthTestAssertions.assertAuthorizationSuccessRedirect(
                URI.create("https://client.example/callback?error=access_denied"), null))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void buildsEndpointDpopAndAdditionalFixtures() {
        OAuthTestEndpoints defaults = OAuthTestEndpoints.defaults();
        assertThat(defaults.token()).isEqualTo("/oauth2/token");
        assertThat(new OAuthTestEndpoints(" metadata ", "authorize", "", "introspect", "revoke",
                "jwks", "register").authorize()).isEqualTo("/authorize");
        assertThat(new OAuthTestEndpoints("", "", "", "", "", "", "").token()).isEqualTo("/");
        assertThatThrownBy(() -> new OAuthTestEndpoints(null, "", "", "", "", "", ""))
                .isInstanceOf(NullPointerException.class);

        OAuthTestDpopMaterial dpop = OAuthTestDpopMaterial.generate();
        assertThat(dpop.jwk()).containsEntry("kty", "EC");
        assertThat(dpop.jwkThumbprint()).isNotBlank();
        String proof = dpop.proof("GET", URI.create("https://api.example/resource"), "access", "jti-1", "n-1");
        assertThat(com.auth0.jwt.JWT.decode(proof).getClaim("htm").asString()).isEqualTo("GET");
        assertThat(dpop.boundAccessToken("test-secret")).isNotBlank();

        var auth = OAuthTestFixtures.authorizationCodeClient("client", "secret", null, "openid");
        assertThat(auth.getRedirectUris()).containsExactly(OAuthTestFixtures.DEFAULT_REDIRECT_URI);
        var oidc = OAuthTestFixtures.oidcAuthorizationCodeRequest("client", null);
        assertThat(oidc.request().scopes()).containsExactly("openid");
        assertThat(OAuthTestFixtures.authorizationCodeTokenRequest("client", "secret", oidc, "code")
                .code()).isEqualTo("code");
        assertThat(OAuthTestFixtures.refreshTokenRequest("client", "secret", "refresh", "read")
                .refreshToken()).isEqualTo("refresh");
    }

    @Test
    void sendsAllHttpClientActionsAgainstLocalServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] body = (exchange.getRequestMethod() + " " + exchange.getRequestURI()).getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        OAuthTestHttpClient client = new OAuthTestHttpClient(
                URI.create("http://localhost:" + server.getAddress().getPort() + "/"));

        HttpResponse<String> get = client.get("health");
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(client.postForm("token", Map.of("a", "b")).statusCode()).isEqualTo(200);
        var request = OAuthTestFixtures.defaultAuthorizationCodeRequest("read").request();
        assertThat(client.authorize("authorize", request).statusCode()).isEqualTo(200);
        assertThat(client.token("token", OAuthTestFixtures.clientCredentialsTokenRequest(
                "client", "secret", "read", null)).statusCode()).isEqualTo(200);
        assertThat(client.introspect("introspect", "token", "client", "secret").statusCode())
                .isEqualTo(200);
        assertThat(client.revoke("revoke", "token", "client", "secret", null).statusCode())
                .isEqualTo(200);
        assertThat(OAuthTestHttp.bearer("token")).isEqualTo("Bearer token");
    }
}
