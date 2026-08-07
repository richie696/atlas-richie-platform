package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.support.RsaAccessTokenSigner;
import cn.richie696.component.oauth.resource.JwkSource;
import cn.richie696.component.oauth.resource.StaticJwkSource;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth Server 和 Resource Server 集成测试共用的 RSA/JWKS 测试密钥材料。
 * 每次调用 {@link #generate(String)} 都会生成新密钥，不依赖工作区文件或外部 KMS。
 */
public final class OAuthTestKeyMaterial {

    private final String keyId;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    private OAuthTestKeyMaterial(String keyId, RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public static OAuthTestKeyMaterial generate(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new OAuthTestKeyMaterial(keyId == null || keyId.isBlank() ? "it-key-1" : keyId,
                    (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
        } catch (Exception e) {
            throw new IllegalStateException("无法生成 OAuth RSA 测试密钥", e);
        }
    }

    public static OAuthTestKeyMaterial generate() {
        return generate("it-key-1");
    }

    public String keyId() {
        return keyId;
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public JwkSource jwkSource() {
        return new StaticJwkSource(Map.of(keyId, publicKey));
    }

    public RsaAccessTokenSigner signer(OAuth2Properties properties) {
        return new RsaAccessTokenSigner(privateKey, publicKey, properties);
    }

    /** 生成 Resource Server 可直接校验的带 kid、issuer、audience 和 scope 的 JWT。 */
    public String accessToken(String issuer, String audience, String clientId,
                              String subject, List<String> scopes) {
        Algorithm algorithm = Algorithm.RSA256(publicKey, privateKey);
        var builder = JWT.create()
                .withKeyId(keyId)
                .withIssuer(issuer)
                .withAudience(audience)
                .withSubject(subject)
                .withClaim("client_id", clientId)
                .withClaim("scope", String.join(" ", scopes == null ? List.of() : scopes))
                .withJWTId(UUID.randomUUID().toString())
                .withIssuedAt(new java.util.Date())
                .withExpiresAt(new java.util.Date(System.currentTimeMillis() + 3_600_000L));
        return builder.sign(algorithm);
    }

    /** 返回 RFC 7517 JWKS JSON，适合 MockWebServer 或内置 HTTP Server 的响应体。 */
    public String jwksJson() {
        return "{\"keys\":[{"
                + "\"kty\":\"RSA\","
                + "\"use\":\"sig\","
                + "\"alg\":\"RS256\","
                + "\"kid\":\"" + escapeJson(keyId) + "\","
                + "\"n\":\"" + base64Url(unsigned(publicKey.getModulus())) + "\","
                + "\"e\":\"" + base64Url(unsigned(publicKey.getPublicExponent())) + "\""
                + "}]}";
    }

    public String privateKeyPem() {
        return pem("PRIVATE KEY", privateKey.getEncoded());
    }

    public String publicKeyPem() {
        return pem("PUBLIC KEY", publicKey.getEncoded());
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return bytes.length > 1 && bytes[0] == 0
                ? java.util.Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String pem(String type, byte[] value) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(value);
        return "-----BEGIN " + type + "-----\n" + encoded + "\n-----END " + type + "-----\n";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
