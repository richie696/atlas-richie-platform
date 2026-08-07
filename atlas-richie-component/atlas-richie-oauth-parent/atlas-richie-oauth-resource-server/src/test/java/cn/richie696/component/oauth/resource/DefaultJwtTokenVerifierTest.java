package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultJwtTokenVerifierTest {

    @Test
    void verifiesIssuerAudienceAndScopes() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String token = JWT.create().withKeyId("k1").withIssuer("https://issuer")
                .withAudience("api").withSubject("user-1").withClaim("client_id", "client-1")
                .withClaim("tenant_id", "tenant-1")
                .withClaim("scope", "api.read api.write")
                .withExpiresAt(Date.from(Instant.now().plusSeconds(60)))
                .sign(Algorithm.RSA256((RSAPublicKey) pair.getPublic(), (java.security.interfaces.RSAPrivateKey) pair.getPrivate()));

        OAuthPrincipal principal = new DefaultJwtTokenVerifier("https://issuer", "api",
                new StaticJwkSource(Map.of("k1", (RSAPublicKey) pair.getPublic()))).verify(token);
        assertThat(principal.subject()).isEqualTo("user-1");
        assertThat(principal.clientId()).isEqualTo("client-1");
        assertThat(principal.scopes()).containsExactly("api.read", "api.write");
        assertThat(principal.claims()).containsEntry("tenant_id", "tenant-1");
    }

    @Test
    void rejectsUnknownKey() {
        assertThatThrownBy(() -> new DefaultJwtTokenVerifier("issuer", "api",
                new StaticJwkSource(Map.of())).verify("eyJhbGciOiJSUzI1NiIsImtpZCI6Im1pc3NpbmcifQ.x.y"))
                .isInstanceOf(ResourceServerException.class);
    }
}
