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

/** 兼容当前组件配置的 HMAC 签名器；生产 AS 应替换为非对称签名实现。 */
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
