package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RsaAccessTokenSignerTest {

    @Test
    void signsVerifiesAndPublishesJwkWithTypedClaims() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var properties = new OAuth2Properties();
        properties.setIssuer("https://auth.example");
        var signer = new RsaAccessTokenSigner("key-1", (java.security.interfaces.RSAPrivateKey) pair.getPrivate(),
                (java.security.interfaces.RSAPublicKey) pair.getPublic(), properties);
        var client = ClientConfig.builder().clientId("client-1").tokenValidDuration(1).build();

        String token = signer.sign("client-1", client, List.of("read"), "https://api.example", "user-1",
                Map.of("tenantId", 1001L, "roles", List.of("admin")));
        var claims = signer.verify(token);

        assertThat(claims.clientId()).isEqualTo("client-1");
        assertThat(claims.subject()).isEqualTo("user-1");
        assertThat(claims.audience()).isEqualTo("https://api.example");
        assertThat(signer.keys()).singleElement().satisfies(jwk -> {
            assertThat(jwk).containsEntry("kid", "key-1").containsEntry("alg", "RS256");
            assertThat(jwk.get("n")).isNotNull();
            assertThat(jwk.get("e")).isNotNull();
        });
    }
}
