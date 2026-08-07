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

/** 依赖 issuer、audience 和 nonce 的 OIDC ID Token 验证器。 */
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
