package cn.richie696.component.oauth.resource;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DpopProofValidatorTest {

    @Test
    void validatesRequestBindingAthCnfAndRejectsJtiReplay() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        String jwkThumbprint = thumbprint(publicKey);
        String accessToken = JWT.create()
                .withClaim("cnf", Map.of("jkt", jwkThumbprint))
                .sign(Algorithm.HMAC256("access-token-secret"));
        String proof = proof(keyPair, publicKey, accessToken, "jti-1", "nonce-1");
        var validator = new DpopProofValidator(new InMemoryDpopReplayStore(),
                java.time.Duration.ofMinutes(5), "nonce-1");

        DpopProof result = validator.validate(proof, accessToken, "GET",
                URI.create("https://api.example/resource?ignored=true"));

        assertThat(result.jti()).isEqualTo("jti-1");
        assertThat(result.jwkThumbprint()).isEqualTo(jwkThumbprint);
        assertThatThrownBy(() -> validator.validate(proof, accessToken, "GET",
                URI.create("https://api.example/resource")))
                .isInstanceOf(ResourceServerException.class)
                .hasMessageContaining("jti");
    }

    @Test
    void rejectsMethodMismatch() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair keyPair = generator.generateKeyPair();
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        String proof = proof(keyPair, publicKey, null, "jti-2", null);

        assertThatThrownBy(() -> new DpopProofValidator(new InMemoryDpopReplayStore())
                .validate(proof, null, "POST", URI.create("https://api.example/resource")))
                .isInstanceOf(ResourceServerException.class)
                .hasMessageContaining("htm");
    }

    private String proof(KeyPair keyPair, ECPublicKey publicKey, String accessToken,
                         String jti, String nonce) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "EC");
        jwk.put("crv", "P-256");
        jwk.put("x", coordinate(publicKey.getW().getAffineX()));
        jwk.put("y", coordinate(publicKey.getW().getAffineY()));
        var builder = JWT.create()
                .withHeader(Map.of("typ", "dpop+jwt", "jwk", jwk))
                .withClaim("htu", "https://api.example/resource?query=ignored")
                .withClaim("htm", "GET")
                .withClaim("iat", Instant.now().getEpochSecond())
                .withJWTId(jti);
        if (accessToken != null) {
            builder.withClaim("ath", sha256(accessToken));
        }
        if (nonce != null) {
            builder.withClaim("nonce", nonce);
        }
        return builder.sign(Algorithm.ECDSA256(null, (ECPrivateKey) keyPair.getPrivate()));
    }

    private String thumbprint(ECPublicKey publicKey) {
        String canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\""
                + coordinate(publicKey.getW().getAffineX()) + "\",\"y\":\""
                + coordinate(publicKey.getW().getAffineY()) + "\"}";
        return sha256(canonical);
    }

    private String coordinate(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[32];
        int source = raw.length > 32 ? raw.length - 32 : 0;
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, source, fixed, 32 - length, length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(fixed);
    }

    private String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
