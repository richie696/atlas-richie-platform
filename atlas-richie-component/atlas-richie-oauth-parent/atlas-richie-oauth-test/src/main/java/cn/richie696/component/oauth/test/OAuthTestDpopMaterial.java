package cn.richie696.component.oauth.test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth 测试支撑工具（不属于生产运行时）：RFC 9449 DPoP 测试密钥与 proof JWT 夹具。
 *
 * <p>职责链位置：在集成测试链路中位于 Resource Server / OAuth Service 之前，
 * 为 DPoP 校验单元或黑盒测试提供可重复使用的 EC P-256 密钥对、JWK、JWK thumbprint、
 * 以及带 {@code htu / htm / iat / ath / nonce / jti} 声明的合法 DPoP proof；
 * 不依赖外部 KMS、HSM 或任何网络资源，每个用例可独立重新生成密钥材料。</p>
 *
 * <p>解决以下问题：DPoP 资源绑定链路中的 proof、JWK thumbprint 与 access token
 * 的 {@code cnf.jkt} 绑定都需要稳定的密钥材料作前置；该夹具把密钥生成、
 * JWK 规范化、thumbprint 计算与 proof 构造统一在一个类里，避免每个测试用
 * 例重复实现 RFC 9449 附录要求的 JWK 序列化与 SHA-256 摘要。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OAuthTestDpopMaterial {

    private final KeyPair keyPair;
    private final ECPublicKey publicKey;

    private OAuthTestDpopMaterial(KeyPair keyPair) {
        this.keyPair = keyPair;
        this.publicKey = (ECPublicKey) keyPair.getPublic();
    }

    public static OAuthTestDpopMaterial generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            KeyPair keyPair = generator.generateKeyPair();
            return new OAuthTestDpopMaterial(keyPair);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成 DPoP 测试密钥", e);
        }
    }

    public String jwkThumbprint() {
        return sha256(canonicalJwk());
    }

    public String proof(String method, URI requestUri, String accessToken, String jti, String nonce) {
        Map<String, Object> jwk = jwk();
        var builder = JWT.create()
                .withHeader(Map.of("typ", "dpop+jwt", "jwk", jwk))
                .withClaim("htu", requestUri.toString())
                .withClaim("htm", method)
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

    public String boundAccessToken(String secret) {
        return JWT.create()
                .withClaim("cnf", Map.of("jkt", jwkThumbprint()))
                .sign(Algorithm.HMAC256(secret));
    }

    public Map<String, Object> jwk() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kty", "EC");
        result.put("crv", "P-256");
        result.put("x", coordinate(publicKey.getW().getAffineX()));
        result.put("y", coordinate(publicKey.getW().getAffineY()));
        return result;
    }

    private String canonicalJwk() {
        Map<String, Object> jwk = jwk();
        return "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\""
                + jwk.get("x") + "\",\"y\":\"" + jwk.get("y") + "\"}";
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
                            .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
