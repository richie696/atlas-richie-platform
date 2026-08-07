package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC ID Token 的本地校验器，强制对齐 issuer / audience / nonce 三大协议约定。
 *
 * <p>处于 OAuth Service 与 {@link OidcIdTokenClaims} 之间：上游接 ID Token JWT 字符串、
 * 期望的 clientId 与 nonce，下游产出经过完整协议校验的 Claims 视图。它直接基于 java-jwt
 * 库做签名与 Claims 比对，不关心密钥来源——密钥可在测试中注入 {@code RSAPublicKey}，
 * 在生产中交给 {@link cn.richie696.component.oauth.core.spi.JwkSetProvider} 派生。
 *
 * <p>解决"RP 侧自己写一签发校验却漏掉 nonce/aud 一致性检查"导致的会话绑定绕过或重放
 * 风险，把 OIDC Core 1.0 §3.1.3.7 规定的强制校验项收敛到一个组件，降低 RP 接入 OIDC
 * 的协议门槛。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OidcIdTokenVerifier {

    private final RSAPublicKey publicKey;
    private final OidcProperties properties;

    public OidcIdTokenVerifier(RSAPublicKey publicKey, OidcProperties properties) {
        this.publicKey = publicKey;
        this.properties = properties;
    }

    public OidcIdTokenClaims verify(String token, String clientId, String expectedNonce) {
        try {
            if (properties.isRequireNonce() && (expectedNonce == null || expectedNonce.isBlank())) {
                throw new IllegalArgumentException("OIDC ID Token 验证必须提供 expected nonce");
            }
            var verification = JWT.require(Algorithm.RSA256(publicKey, null))
                    .withIssuer(properties.getIssuer())
                    .withAudience(clientId);
            if (expectedNonce != null && !expectedNonce.isBlank()) {
                verification.withClaim(OidcConstants.CLAIM_NONCE, expectedNonce);
            }
            DecodedJWT jwt = verification.build().verify(token);
            if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
                throw new IllegalArgumentException("ID Token 缺少 sub");
            }
            return toClaims(jwt);
        } catch (Exception e) {
            throw new OidcVerificationException("OIDC ID Token 验证失败", e);
        }
    }

    private OidcIdTokenClaims toClaims(DecodedJWT jwt) {
        Map<String, Object> claims = new LinkedHashMap<>();
        jwt.getClaims().forEach((name, claim) -> claims.put(name, claimValue(claim)));
        Long authTime = jwt.getClaim(OidcConstants.CLAIM_AUTH_TIME).asLong();
        return new OidcIdTokenClaims(jwt.getIssuer(), jwt.getSubject(), jwt.getAudience(),
                jwt.getExpiresAt() == null ? 0 : jwt.getExpiresAt().getTime() / 1000L,
                jwt.getIssuedAt() == null ? 0 : jwt.getIssuedAt().getTime() / 1000L,
                authTime,
                text(jwt.getClaim(OidcConstants.CLAIM_NONCE)),
                text(jwt.getClaim(OidcConstants.CLAIM_AT_HASH)),
                claims);
    }

    private Object claimValue(Claim claim) {
        if (claim == null || claim.isNull()) {
            return null;
        }
        String text = claim.asString();
        if (text != null) {
            return text;
        }
        Long number = claim.asLong();
        if (number != null) {
            return number;
        }
        List<String> values = claim.asList(String.class);
        return values == null ? null : values;
    }

    private String text(Claim claim) {
        return claim == null || claim.isNull() ? null : claim.asString();
    }
}
