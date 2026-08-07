package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.component.oauth.core.spi.JwkSetProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.KeyFactory;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 使用 RSA SHA-256 签发 OIDC ID Token 的默认实现。 */
public final class RsaOidcIdTokenSigner implements OidcIdTokenSigner, JwkSetProvider {

    private final String keyId;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final OidcProperties properties;

    public RsaOidcIdTokenSigner(String keyId, RSAPrivateKey privateKey, OidcProperties properties) {
        this(keyId, privateKey, derivePublicKey(privateKey), properties);
    }

    public RsaOidcIdTokenSigner(String keyId, RSAPrivateKey privateKey,
                                RSAPublicKey publicKey, OidcProperties properties) {
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.properties = properties;
    }

    @Override
    public String sign(OidcIdTokenRequest request) {
        requireRsaAlgorithm();
        long now = Instant.now().getEpochSecond();
        long authTime = request.authenticationTime() == null
                ? now : request.authenticationTime().getEpochSecond();
        long expiresAt = now + properties.getIdTokenTtlSeconds();

        JWTCreator.Builder builder = JWT.create()
                .withKeyId(keyId)
                .withIssuer(required(properties.getIssuer(), "OIDC issuer 未配置"))
                .withSubject(request.subject())
                .withAudience(request.clientId())
                .withIssuedAt(new Date(now * 1000L))
                .withExpiresAt(new Date(expiresAt * 1000L))
                .withJWTId(UUID.randomUUID().toString())
                .withClaim(OidcConstants.CLAIM_AUTH_TIME, authTime);

        if (StringUtils.isNotBlank(request.nonce())) {
            builder.withClaim(OidcConstants.CLAIM_NONCE, request.nonce());
        }
        if (StringUtils.isNotBlank(request.accessToken())) {
            builder.withClaim(OidcConstants.CLAIM_AT_HASH, leftHalfHash(request.accessToken()));
        }
        request.claims().forEach((name, value) -> addClaim(builder, name, value));
        return builder.sign(Algorithm.RSA256(null, privateKey));
    }

    private void addClaim(JWTCreator.Builder builder, String name, Object value) {
        if (value != null && !name.equals(OidcConstants.CLAIM_ISSUER)
                && !name.equals(OidcConstants.CLAIM_SUBJECT)
                && !name.equals(OidcConstants.CLAIM_AUDIENCE)
                && !name.equals(OidcConstants.CLAIM_EXPIRATION)
                && !name.equals(OidcConstants.CLAIM_ISSUED_AT)) {
            if (value instanceof String string) {
                builder.withClaim(name, string);
            } else if (value instanceof Boolean bool) {
                builder.withClaim(name, bool);
            } else if (value instanceof Integer integer) {
                builder.withClaim(name, integer);
            } else if (value instanceof Long longValue) {
                builder.withClaim(name, longValue);
            } else if (value instanceof Double doubleValue) {
                builder.withClaim(name, doubleValue);
            } else if (value instanceof Map<?, ?> map) {
                builder.withClaim(name, map.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()), Map.Entry::getValue)));
            } else if (value instanceof List<?> list) {
                builder.withClaim(name, list);
            } else {
                throw new IllegalArgumentException("不支持的 OIDC ID Token Claim 类型: " + value.getClass());
            }
        }
    }

    private String leftHalfHash(String accessToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            byte[] leftHalf = java.util.Arrays.copyOf(hash, hash.length / 2);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成 at_hash", e);
        }
    }

    private void requireRsaAlgorithm() {
        if (!"RS256".equalsIgnoreCase(properties.getIdTokenSigningAlgorithm())) {
            throw new IllegalStateException("当前 RSA ID Token signer 仅支持 RS256");
        }
    }

    private String required(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    @Override
    public List<Map<String, Object>> keys() {
        if (publicKey == null) {
            throw new IllegalStateException("发布 OIDC JWKS 需要 RSA public key");
        }
        return List.of(Map.of("kty", "RSA", "kid", keyId, "use", "sig", "alg", "RS256",
                "n", Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(publicKey.getModulus().toByteArray())),
                "e", Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(publicKey.getPublicExponent().toByteArray()))));
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            return null;
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (Exception e) {
            throw new IllegalStateException("无法从 RSA private key 派生 public key", e);
        }
    }

    private byte[] unsigned(byte[] value) {
        return value.length > 1 && value[0] == 0
                ? java.util.Arrays.copyOfRange(value, 1, value.length) : value;
    }
}
