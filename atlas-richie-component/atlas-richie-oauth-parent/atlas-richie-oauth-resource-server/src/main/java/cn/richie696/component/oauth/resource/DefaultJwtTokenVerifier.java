package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.Verification;

import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 基于 issuer / audience / JWKS 的 RSA JWT 访问令牌本地校验器默认实现。
 *
 * <p>处于 {@link ResourceServerAuthenticator} 的 JWT 优先路径与 {@link JwkSource} 之间：
 * 上游传 accessToken，下游通过 {@code JwkSource} 按 {@code kid} 拿到公钥，再强制
 * 校验签名、issuer 与 audience，最终产出 {@link OAuthPrincipal}。它只做协议层校验，
 * 不触碰 HTTP、缓存、metrics 与 DPoP。
 *
 * <p>解决"Resource Server 不得不在每个项目里自己实现一遍 JWT 验签 + 协议 Claims 比对"
 * 的重复劳动，把 RFC 9068 要求的 iss/aud/exp/nbf/iat 等校验收敛在一处，并允许通过
 * {@code JwkSource} 替换 JWKS 来源（HTTP 拉取 / 静态配置 / 外部缓存）。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class DefaultJwtTokenVerifier implements JwtTokenVerifier {

    private final String issuer;
    private final String audience;
    private final JwkSource jwkSource;

    public DefaultJwtTokenVerifier(String issuer, String audience, JwkSource jwkSource) {
        this.issuer = issuer;
        this.audience = audience;
        this.jwkSource = jwkSource;
    }

    @Override
    public OAuthPrincipal verify(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResourceServerException("Bearer token 不能为空");
        }
        try {
            DecodedJWT decoded = JWT.decode(accessToken);
            RSAPublicKey key = jwkSource.find(decoded.getKeyId());
            if (key == null) {
                throw new ResourceServerException("找不到 JWT 签名公钥: kid=" + decoded.getKeyId());
            }
            Algorithm algorithm = Algorithm.RSA256(key, null);
            Verification verification = JWT.require(algorithm);
            if (issuer != null && !issuer.isBlank()) {
                verification.withIssuer(issuer);
            }
            if (audience != null && !audience.isBlank()) {
                verification.withAudience(audience);
            }
            DecodedJWT verified = verification.build().verify(accessToken);
            List<String> scopes = parseScopes(verified.getClaim("scope"));
            String subject = verified.getSubject();
            String clientId = claimText(verified, "client_id");
            if (clientId == null) {
                clientId = claimText(verified, "clientId");
            }
            return new OAuthPrincipal(subject, clientId, verified.getIssuer(),
                    verified.getAudience().isEmpty() ? null : verified.getAudience().getFirst(),
                    verified.getId(), scopes, claims(verified));
        } catch (ResourceServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ResourceServerException("JWT access token 校验失败", e);
        }
    }

    private String claimText(DecodedJWT jwt, String name) {
        Claim claim = jwt.getClaim(name);
        return claim == null || claim.isNull() ? null : claim.asString();
    }

    private List<String> parseScopes(Claim claim) {
        String scope = claim == null || claim.isNull() ? null : claim.asString();
        return scope == null || scope.isBlank()
                ? List.of()
                : Arrays.stream(scope.trim().split("\\s+")).filter(s -> !s.isBlank()).toList();
    }

    private Map<String, Object> claims(DecodedJWT jwt) {
        Map<String, Object> result = new LinkedHashMap<>();
        jwt.getClaims().forEach((name, claim) -> {
            if (claim == null || claim.isNull()) return;
            try {
                String text = claim.asString();
                if (text != null) {
                    result.put(name, text);
                    return;
                }
            } catch (Exception ignored) {
            }
            try {
                List<String> values = claim.asList(String.class);
                if (values != null) {
                    result.put(name, values);
                    return;
                }
            } catch (Exception ignored) {
            }
            try {
                Boolean bool = claim.asBoolean();
                if (bool != null) {
                    result.put(name, bool);
                    return;
                }
            } catch (Exception ignored) {
            }
            try {
                Long number = claim.asLong();
                if (number != null) result.put(name, number);
            } catch (Exception ignored) {
            }
        });
        return Map.copyOf(result);
    }
}
