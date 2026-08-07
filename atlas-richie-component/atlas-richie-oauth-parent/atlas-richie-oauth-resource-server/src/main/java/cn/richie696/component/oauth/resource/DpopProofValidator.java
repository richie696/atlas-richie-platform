package cn.richie696.component.oauth.resource;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.AlgorithmParameters;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/** DPoP RFC 9449 proof 校验器，支持 ES256、htu/htm、ath、nonce 和 jti 防重放。 */
public final class DpopProofValidator {

    private static final String TYPE = "dpop+jwt";
    private static final String ALGORITHM = "ES256";

    private final DpopReplayStore replayStore;
    private final Duration clockSkew;
    private final String expectedNonce;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DpopProofValidator(DpopReplayStore replayStore) {
        this(replayStore, Duration.ofMinutes(5), null);
    }

    public DpopProofValidator(DpopReplayStore replayStore, Duration clockSkew, String expectedNonce) {
        if (replayStore == null) {
            throw new IllegalArgumentException("replayStore must not be null");
        }
        this.replayStore = replayStore;
        this.clockSkew = clockSkew == null ? Duration.ofMinutes(5) : clockSkew;
        this.expectedNonce = expectedNonce;
    }

    public DpopProof validate(String proof, String accessToken, String method, URI requestUri) {
        if (proof == null || proof.isBlank()) {
            throw invalid("DPoP proof 不能为空");
        }
        if (method == null || method.isBlank() || requestUri == null) {
            throw invalid("DPoP 请求上下文不完整");
        }
        try {
            DecodedJWT decoded = JWT.decode(proof);
            String headerJson = new String(Base64.getUrlDecoder().decode(decoded.getHeader()),
                    StandardCharsets.UTF_8);
            JsonNode header = objectMapper.readTree(headerJson);
            if (!TYPE.equalsIgnoreCase(header.path("typ").asText())
                    || !ALGORITHM.equalsIgnoreCase(header.path("alg").asText())) {
                throw invalid("DPoP proof 必须使用 typ=dpop+jwt 和 alg=ES256");
            }
            JsonNode jwk = header.path("jwk");
            ECPublicKey publicKey = readPublicKey(jwk);
            verifySignature(decoded, proof, publicKey);

            String htm = requiredClaim(decoded, "htm");
            String htu = requiredClaim(decoded, "htu");
            String jti = requiredClaim(decoded, "jti");
            long iat = requiredIssuedAt(decoded);
            if (!method.equalsIgnoreCase(htm)) {
                throw invalid("DPoP htm 与请求方法不匹配");
            }
            if (!normalize(requestUri).equals(normalize(URI.create(htu)))) {
                throw invalid("DPoP htu 与请求 URI 不匹配");
            }
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - iat) > clockSkew.toSeconds()) {
                throw invalid("DPoP iat 超出允许时钟偏差");
            }
            String nonce = text(decoded.getClaim("nonce"));
            if (expectedNonce != null && !expectedNonce.isBlank()
                    && !expectedNonce.equals(nonce)) {
                throw invalid("DPoP nonce 不匹配");
            }
            if (accessToken != null && !accessToken.isBlank()) {
                String ath = text(decoded.getClaim("ath"));
                String expectedAth = sha256(accessToken);
                if (!expectedAth.equals(ath)) {
                    throw invalid("DPoP ath 与 access token 不匹配");
                }
                verifyTokenBinding(accessToken, thumbprint(jwk));
            }
            if (!replayStore.markIfUnseen(jti, Math.max(1L, clockSkew.toMillis() * 2L))) {
                throw invalid("DPoP jti 已被使用");
            }
            return new DpopProof(jti, htm, htu, Instant.ofEpochSecond(iat), thumbprint(jwk), nonce);
        } catch (ResourceServerException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("DPoP proof 校验失败", e);
        }
    }

    private ECPublicKey readPublicKey(JsonNode jwk) throws Exception {
        if (!"EC".equals(jwk.path("kty").asText())
                || !"P-256".equals(jwk.path("crv").asText())) {
            throw invalid("DPoP jwk 仅支持 P-256 EC 公钥");
        }
        byte[] x = decode(jwk.path("x").asText());
        byte[] y = decode(jwk.path("y").asText());
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = parameters.getParameterSpec(ECParameterSpec.class);
        return (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new ECPublicKeySpec(new ECPoint(new BigInteger(1, x), new BigInteger(1, y)), spec));
    }

    private void verifySignature(DecodedJWT decoded, String proof, ECPublicKey key) throws Exception {
        byte[] signature = Base64.getUrlDecoder().decode(decoded.getSignature());
        if (signature.length != 64) {
            throw invalid("DPoP ES256 签名格式无效");
        }
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(key);
        verifier.update((decoded.getHeader() + "." + decoded.getPayload()).getBytes(StandardCharsets.US_ASCII));
        if (!verifier.verify(rawToDer(signature))) {
            throw invalid("DPoP proof 签名无效");
        }
    }

    private void verifyTokenBinding(String accessToken, String thumbprint) {
        try {
            Claim cnf = JWT.decode(accessToken).getClaim("cnf");
            Map<String, Object> values = cnf.asMap();
            if (values == null || !thumbprint.equals(String.valueOf(values.get("jkt")))) {
                throw invalid("access token 的 cnf.jkt 与 DPoP 公钥不匹配");
            }
        } catch (ResourceServerException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("access token 缺少有效的 DPoP cnf.jkt", e);
        }
    }

    private String thumbprint(JsonNode jwk) {
        String canonical = "{\"crv\":\"" + jwk.path("crv").asText()
                + "\",\"kty\":\"" + jwk.path("kty").asText()
                + "\",\"x\":\"" + jwk.path("x").asText()
                + "\",\"y\":\"" + jwk.path("y").asText() + "\"}";
        return sha256(canonical);
    }

    private long requiredIssuedAt(DecodedJWT decoded) {
        DateValue value = new DateValue(decoded.getClaim("iat").asLong());
        if (value.value() == null) {
            throw invalid("DPoP 缺少 iat");
        }
        return value.value();
    }

    private String requiredClaim(DecodedJWT decoded, String name) {
        String value = text(decoded.getClaim(name));
        if (value == null || value.isBlank()) {
            throw invalid("DPoP 缺少 " + name);
        }
        return value;
    }

    private String text(Claim claim) {
        return claim == null || claim.isNull() ? null : claim.asString();
    }

    private String normalize(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        return uri.getScheme().toLowerCase() + "://" + uri.getRawAuthority().toLowerCase() + path;
    }

    private byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("DPoP jwk 缺少 EC 坐标");
        }
        return Base64.getUrlDecoder().decode(value);
    }

    private String sha256(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private byte[] rawToDer(byte[] raw) {
        byte[] r = integer(raw, 0);
        byte[] s = integer(raw, 32);
        int length = 2 + r.length + 2 + s.length;
        ByteBuffer result = ByteBuffer.allocate(2 + length);
        result.put((byte) 0x30).put((byte) length)
                .put((byte) 0x02).put((byte) r.length).put(r)
                .put((byte) 0x02).put((byte) s.length).put(s);
        return result.array();
    }

    private byte[] integer(byte[] raw, int offset) {
        int start = offset;
        while (start < offset + 31 && raw[start] == 0) start++;
        int length = offset + 32 - start;
        boolean highBit = (raw[start] & 0x80) != 0;
        byte[] result = new byte[length + (highBit ? 1 : 0)];
        if (highBit) {
            System.arraycopy(raw, start, result, 1, length);
        } else {
            System.arraycopy(raw, start, result, 0, length);
        }
        return result;
    }

    private ResourceServerException invalid(String message) {
        return new ResourceServerException(message);
    }

    private ResourceServerException invalid(String message, Throwable cause) {
        return new ResourceServerException(message, cause);
    }

    private record DateValue(Long value) {
    }
}
