package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.AccessTokenSigner;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.component.oauth.contract.OAuth2Constants;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 兼容当前组件配置的 HMAC Access Token 签名器。
 * <p>
 * 使用对称密钥(HMAC256)签发与验证 JWT,密钥来自 {@link cn.richie696.component.oauth.core.config.OAuth2Properties getTokenSecret()};
 * 同时保留对组件升级前由平台 JwtUtils 签发的存量 token 的兼容路径(由 TokenEndpoint 的回退逻辑触发)。
 * </p>
 * <p>
 * 处于 oauth-core 的默认签名器位置:由 {@link cn.richie696.component.oauth.core.config.OAuth2AutoConfiguration}
 * 作为 {@link AccessTokenSigner} 的默认 Bean 注册;生产 Authorization Server 应注入
 * {@link RsaAccessTokenSigner} 等非对称实现,本类仅用于开发/兼容场景。
 * </p>
 * <p>
 * 解决的问题:在缺少密钥管理服务的轻量场景下,用对称密钥也能跑通 OAuth Token 端点;同时通过把
 * 保留 claim(iss/sub/aud/exp/iat/nbf/jti/scope/client_id)显式列出,保证扩展声明不会覆盖协议
 * 关键字段。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public class HmacAccessTokenSigner implements AccessTokenSigner {

    private final OAuth2Properties properties;

    public HmacAccessTokenSigner(OAuth2Properties properties) {
        this.properties = properties;
    }

    @Override
    public String sign(String clientId, ClientConfig client, List<String> scopes, String resource) {
        return sign(clientId, client, scopes, resource, Map.of());
    }

    @Override
    public String sign(String clientId, ClientConfig client, List<String> scopes,
                       String resource, Map<String, Object> additionalClaims) {
        return sign(clientId, client, scopes, resource, null, additionalClaims);
    }

    @Override
    public String sign(String clientId, ClientConfig client, List<String> scopes,
                       String resource, String subject, Map<String, Object> additionalClaims) {
        String secret = properties.getTokenSecret();
        if (StringUtils.isBlank(secret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CONFIG, "Token 密钥未配置");
        }
        long durationHours = client.getTokenValidDuration() == null
                ? properties.getDefaultTokenValidDuration() : client.getTokenValidDuration();
        long expiresAt = System.currentTimeMillis() + durationHours * 3600_000L;
        Algorithm algorithm = Algorithm.HMAC256(secret);
        var builder = JWT.create()
                .withClaim(OAuth2Constants.JWT_CLAIM_USERNAME, clientId)
                .withClaim(OAuth2Constants.JWT_CLAIM_CLIENT_ID, clientId)
                .withClaim(OAuth2Constants.JWT_CLAIM_TYPE, OAuth2Constants.JWT_CLAIM_TYPE_THIRD_PARTY)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(expiresAt))
                .withJWTId(UUID.randomUUID().toString())
                .withIssuer(StringUtils.defaultIfBlank(properties.getIssuer(), "Richie Inc."))
                .withSubject(StringUtils.defaultIfBlank(subject, OAuth2Constants.JWT_SUBJECT_THIRD_PARTY_ACCESS_TOKEN))
                .withAudience(StringUtils.defaultIfBlank(resource,
                        StringUtils.defaultIfBlank(properties.getAudience(), clientId)));
        if (scopes != null && !scopes.isEmpty()) {
            builder.withClaim(OAuth2Constants.JWT_CLAIM_SCOPE, String.join(" ", scopes));
        }
        if (additionalClaims != null) {
            additionalClaims.forEach((name, value) -> addClaim(builder, name, value));
        }
        return builder.sign(algorithm);
    }

    private Set<String> reservedClaims() {
        return Set.of("iss", "sub", "aud", "exp", "iat", "nbf", "jti", "scope", "client_id");
    }

    @SuppressWarnings("unchecked")
    private void addClaim(JWTCreator.Builder builder, String name, Object value) {
        if (name == null || value == null || reservedClaims().contains(name)) {
            return;
        }
        if (value instanceof String string && !string.isBlank()) {
            builder.withClaim(name, string);
        } else if (value instanceof Boolean bool) {
            builder.withClaim(name, bool);
        } else if (value instanceof Integer number) {
            builder.withClaim(name, number);
        } else if (value instanceof Long number) {
            builder.withClaim(name, number);
        } else if (value instanceof Double number) {
            builder.withClaim(name, number);
        } else if (value instanceof List<?> list) {
            builder.withClaim(name, list.stream().map(Object::toString).toList());
        } else if (value instanceof Map<?, ?> map) {
            builder.withClaim(name, (Map<String, Object>) map);
        }
    }

    @Override
    public AccessTokenClaims verify(String accessToken) {
        String secret = properties.getTokenSecret();
        if (StringUtils.isBlank(secret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CONFIG, "Token 密钥未配置");
        }
        try {
            JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
            DecodedJWT jwt = verifier.verify(accessToken);
            Claim scopeClaim = jwt.getClaim(OAuth2Constants.JWT_CLAIM_SCOPE);
            String scope = scopeClaim == null || scopeClaim.isNull() ? null : scopeClaim.asString();
            List<String> scopes = StringUtils.isBlank(scope)
                    ? List.of() : Arrays.stream(scope.split("\\s+")).toList();
            return new AccessTokenClaims(jwt.getClaim(OAuth2Constants.JWT_CLAIM_CLIENT_ID).asString(),
                    jwt.getSubject(), jwt.getIssuer(), jwt.getAudience().isEmpty() ? null : jwt.getAudience().getFirst(),
                    jwt.getId(), jwt.getExpiresAt() == null ? 0 : jwt.getExpiresAt().getTime(), scopes);
        } catch (Exception e) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_TOKEN, "Access token 无效");
        }
    }
}
