package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * 使用 RS256 签发符合 OIDC Backchannel Logout 规范的 Logout Token 的默认实现。
 *
 * <p>处于 {@link OidcBackchannelLogoutService} 与 OAuth Service 的密钥托管层之间：
 * 上游接 {@link OidcLogoutTokenRequest}，下游按 Backchannel Logout 规范把
 * {@code iss / aud / iat / jti / events / sub / sid} 写入 JWT 并使用配置的 RSA 私钥
 * 签名。事件类型固定为 {@link OidcBackchannelLogoutService#BACKCHANNEL_LOGOUT_EVENT}。
 *
 * <p>解决"OP 需要为 Backchannel Logout 单独再实现一遍带 events claim 的 JWT 签发"的
 * 重复劳动，把这一协议面打成可注入的默认 Bean，业务侧只需提供 RSA 私钥与
 * {@code kid} 即可；同时保证密钥轮换时通过 {@code kid} 维持与 RP 间的 JWKS 兼容。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
