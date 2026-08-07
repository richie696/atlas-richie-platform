package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/** 使用 RS256 签发符合 OIDC Backchannel Logout 规范的 Logout Token。 */
public final class RsaOidcLogoutTokenSigner implements OidcLogoutTokenSigner {

    private final String keyId;
    private final RSAPrivateKey privateKey;
    private final OidcProperties properties;

    public RsaOidcLogoutTokenSigner(String keyId, RSAPrivateKey privateKey, OidcProperties properties) {
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.properties = properties;
    }

    @Override
    public String sign(OidcLogoutTokenRequest request) {
        if (properties.getIssuer() == null || properties.getIssuer().isBlank()) {
            throw new IllegalStateException("OIDC issuer 未配置");
        }
        Instant issuedAt = request.issuedAt();
        var builder = JWT.create()
                .withKeyId(keyId)
                .withIssuer(properties.getIssuer())
                .withAudience(request.clientId())
                .withIssuedAt(Date.from(issuedAt))
                .withJWTId(UUID.randomUUID().toString())
                .withClaim(OidcConstants.CLAIM_EVENTS,
                        Map.of(OidcBackchannelLogoutService.BACKCHANNEL_LOGOUT_EVENT, Map.of()));
        if (request.subject() != null && !request.subject().isBlank()) {
            builder.withSubject(request.subject());
        }
        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            builder.withClaim(OidcConstants.CLAIM_SID, request.sessionId());
        }
        return builder.sign(Algorithm.RSA256(null, privateKey));
    }
}
