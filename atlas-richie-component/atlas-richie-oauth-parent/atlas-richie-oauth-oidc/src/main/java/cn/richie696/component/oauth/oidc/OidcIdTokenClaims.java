package cn.richie696.component.oauth.oidc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 经过签名验证后的 OIDC ID Token 关键 Claims。 */
public record OidcIdTokenClaims(
        String issuer,
        String subject,
        List<String> audience,
        long expiresAt,
        long issuedAt,
        Long authenticationTime,
        String nonce,
        String accessTokenHash,
        Map<String, Object> claims
) {
    public OidcIdTokenClaims {
        audience = audience == null ? List.of() : List.copyOf(audience);
        claims = claims == null ? Collections.emptyMap() : Map.copyOf(claims);
    }
}
