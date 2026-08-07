package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RsaOidcIdTokenSignerTest {

    @Test
    void signsIdTokenWithStandardClaimsAndAtHash() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        var properties = new OidcProperties();
        properties.setIssuer("https://as.example.test");
        properties.setIdTokenTtlSeconds(600);
        var signer = new RsaOidcIdTokenSigner("kid-1",
                (java.security.interfaces.RSAPrivateKey) keyPair.getPrivate(),
                (java.security.interfaces.RSAPublicKey) keyPair.getPublic(), properties);
        var service = new OidcIdTokenService(properties, signer);

        String token = service.issue(new OidcIdTokenRequest(
                "user-1", "client-1", "nonce-1", Instant.now(),
                List.of("openid", "profile"), Map.of("name", "Alice"), "access-token-1"));
        var decoded = JWT.decode(token);

        assertThat(decoded.getKeyId()).isEqualTo("kid-1");
        assertThat(decoded.getIssuer()).isEqualTo("https://as.example.test");
        assertThat(decoded.getSubject()).isEqualTo("user-1");
        assertThat(decoded.getAudience()).containsExactly("client-1");
        assertThat(decoded.getClaim("nonce").asString()).isEqualTo("nonce-1");
        assertThat(decoded.getClaim("at_hash").asString()).isNotBlank();
        assertThat(decoded.getClaim("name").asString()).isEqualTo("Alice");
        assertThat(signer.keys()).singleElement().satisfies(jwk ->
                assertThat(jwk).containsEntry("kid", "kid-1").containsEntry("alg", "RS256"));

        var verified = new OidcIdTokenVerifier(
                (java.security.interfaces.RSAPublicKey) keyPair.getPublic(), properties)
                .verify(token, "client-1", "nonce-1");
        assertThat(verified.subject()).isEqualTo("user-1");
        assertThat(verified.nonce()).isEqualTo("nonce-1");
    }
}
