package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcLogoutChannelServiceTest {

    @Test
    void backchannelLogoutCreatesSignedAudienceBoundLogoutToken() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        OidcProperties properties = new OidcProperties();
        properties.setIssuer("https://as.example");
        var service = new OidcBackchannelLogoutService(
                new RsaOidcLogoutTokenSigner("logout-1", (RSAPrivateKey) keys.getPrivate(), properties));
        var request = new OidcBackchannelLogoutService.OidcBackchannelLogoutRequest(
                "user-1", "sid-1", Instant.parse("2026-08-07T00:00:00Z"));
        var clients = List.of(new OidcClientLogoutConfiguration("client-1", null,
                URI.create("https://client.example/backchannel"), List.of()));
        AtomicReference<String> delivered = new AtomicReference<>();

        var deliveries = service.logout(request, clients,
                (endpoint, token) -> delivered.set(endpoint + "|" + token));
        var token = JWT.decode(deliveries.getFirst().logoutToken());

        assertThat(deliveries).hasSize(1);
        assertThat(delivered.get()).startsWith("https://client.example/backchannel|");
        assertThat(token.getIssuer()).isEqualTo("https://as.example");
        assertThat(token.getAudience()).containsExactly("client-1");
        assertThat(token.getSubject()).isEqualTo("user-1");
        assertThat(token.getClaim("sid").asString()).isEqualTo("sid-1");
        assertThat(token.getClaim("events").asMap()).containsKey(
                OidcBackchannelLogoutService.BACKCHANNEL_LOGOUT_EVENT);
    }

    @Test
    void frontchannelLogoutBuildsIframeUriWithoutOverwritingExistingQuery() {
        var service = new OidcFrontchannelLogoutService();
        var request = new OidcBackchannelLogoutService.OidcBackchannelLogoutRequest(
                null, "sid/1", Instant.now());
        var clients = List.of(new OidcClientLogoutConfiguration("client-1",
                URI.create("https://client.example/front?theme=dark"), null, List.of()));

        var frames = service.frames(request, clients, "https://as.example/issuer");

        assertThat(frames).singleElement().extracting(frame -> frame.iframeUri().toString())
                .satisfies(uri -> {
                    assertThat(uri).contains("theme=dark");
                    assertThat(uri).contains("iss=https%3A%2F%2Fas.example%2Fissuer");
                    assertThat(uri).contains("sid=sid%2F1");
                });
    }

    @Test
    void backchannelLogoutRejectsNonHttpEndpoint() {
        var service = new OidcBackchannelLogoutService((request) -> "logout-token");
        var request = new OidcBackchannelLogoutService.OidcBackchannelLogoutRequest(
                "user-1", null, Instant.now());
        var clients = List.of(new OidcClientLogoutConfiguration("client-1", null,
                URI.create("file:///tmp/logout"), List.of()));

        assertThatThrownBy(() -> service.prepare(request, clients))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP(S)");
    }
}
