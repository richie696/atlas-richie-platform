package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void rotationVerifiesPreviousTokenAndPublishesBothPublicKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var first = generator.generateKeyPair();
        var second = generator.generateKeyPair();
        var properties = new OAuth2Properties();
        properties.setIssuer("https://auth.example");
        var signer = new RsaAccessTokenSigner("key-1",
                (java.security.interfaces.RSAPrivateKey) first.getPrivate(),
                (java.security.interfaces.RSAPublicKey) first.getPublic(), properties);
        var client = ClientConfig.builder().clientId("client-1").tokenValidDuration(1).build();
        String oldToken = signer.sign("client-1", client, List.of("read"), "api");

        signer.rotate("key-2",
                (java.security.interfaces.RSAPrivateKey) second.getPrivate(),
                (java.security.interfaces.RSAPublicKey) second.getPublic(), Duration.ofHours(1));
        String newToken = signer.sign("client-1", client, List.of("read"), "api");

        assertThat(signer.verify(oldToken).clientId()).isEqualTo("client-1");
        assertThat(signer.verify(newToken).clientId()).isEqualTo("client-1");
        assertThat(signer.keys()).extracting(jwk -> jwk.get("kid"))
                .containsExactly("key-2", "key-1");
    }

    @Test
    void previousRsaKeyLeavesVerificationAndJwksAfterWindow() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var first = generator.generateKeyPair();
        var second = generator.generateKeyPair();
        var properties = new OAuth2Properties();
        properties.setIssuer("https://auth.example");
        var signer = new RsaAccessTokenSigner("key-1",
                (java.security.interfaces.RSAPrivateKey) first.getPrivate(),
                (java.security.interfaces.RSAPublicKey) first.getPublic(), properties);
        var client = ClientConfig.builder().clientId("client-1").tokenValidDuration(1).build();
        String oldToken = signer.sign("client-1", client, List.of("read"), "api");

        signer.rotate("key-2",
                (java.security.interfaces.RSAPrivateKey) second.getPrivate(),
                (java.security.interfaces.RSAPublicKey) second.getPublic(), Duration.ofMillis(1));
        Thread.sleep(10);

        assertThat(signer.keys()).extracting(jwk -> jwk.get("kid")).containsExactly("key-2");
        assertThatThrownBy(() -> signer.verify(oldToken))
                .isInstanceOf(cn.richie696.contract.exception.BusinessException.class);
    }
}
