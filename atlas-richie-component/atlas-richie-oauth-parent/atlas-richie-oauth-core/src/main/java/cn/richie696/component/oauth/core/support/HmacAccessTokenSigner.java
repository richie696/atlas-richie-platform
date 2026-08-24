package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.AccessTokenSigner;
import cn.richie696.component.secret.api.SecretSnapshotChangedEvent;
import cn.richie696.component.secret.bootstrap.refresh.PreparedSecretRefresh;
import cn.richie696.component.secret.bootstrap.refresh.SecretRefreshParticipant;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.component.oauth.contract.OAuth2Constants;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 兼容当前组件配置的 HMAC Access Token 签名器。
 * <p>
 * 使用对称密钥(HMAC256)签发与验证 JWT,密钥来自 {@link cn.richie696.component.oauth.core.config.OAuth2Properties getTokenSecret()};
 * 同时保留对组件升级前由平台 JwtUtils 签发的存量 token 的兼容路径(由 TokenEndpoint 的回退逻辑触发)。
 * <p>
 * 处于 oauth-core 的默认签名器位置:由 {@link cn.richie696.component.oauth.core.config.OAuth2AutoConfiguration}
 * 作为 {@link AccessTokenSigner} 的默认 Bean 注册;生产 Authorization Server 应注入
 * {@link RsaAccessTokenSigner} 等非对称实现,本类仅用于开发/兼容场景。
 * <p>
 * 解决的问题:在缺少密钥管理服务的轻量场景下,用对称密钥也能跑通 OAuth Token 端点;同时通过把
 * 保留 claim(iss/sub/aud/exp/iat/nbf/jti/scope/client_id)显式列出,保证扩展声明不会覆盖协议
 * 关键字段。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class HmacAccessTokenSigner implements AccessTokenSigner, SecretRefreshParticipant {

    private final OAuth2Properties properties;
    private final AtomicReference<KeyWindow> keys;

    public HmacAccessTokenSigner(OAuth2Properties properties) {
        this.properties = properties;
        this.keys = new AtomicReference<>(new KeyWindow(properties.getTokenSecret(), null, null));
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
        String secret = keys.get().current();
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
        KeyWindow window = activeWindow();
        String secret = window.current();
        if (StringUtils.isBlank(secret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CONFIG, "Token 密钥未配置");
        }
        try {
            return verifyWith(accessToken, secret);
        } catch (RuntimeException currentFailure) {
            if (window.previousActive() && StringUtils.isNotBlank(window.previous())) {
                try {
                    return verifyWith(accessToken, window.previous());
                } catch (RuntimeException ignored) {
                    // Fall through to the single sanitized error below.
                }
            }
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_TOKEN, "Access token 无效");
        }
    }

    private KeyWindow activeWindow() {
        KeyWindow window = keys.get();
        if (window.previous() != null && !window.previousActive()) {
            KeyWindow currentOnly = new KeyWindow(window.current(), null, null);
            keys.compareAndSet(window, currentOnly);
            return keys.get();
        }
        return window;
    }

    private AccessTokenClaims verifyWith(String accessToken, String secret) {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
        DecodedJWT jwt = verifier.verify(accessToken);
        Claim scopeClaim = jwt.getClaim(OAuth2Constants.JWT_CLAIM_SCOPE);
        String scope = scopeClaim == null || scopeClaim.isNull() ? null : scopeClaim.asString();
        List<String> scopes = StringUtils.isBlank(scope)
                ? List.of() : Arrays.stream(scope.split("\\s+")).toList();
        return new AccessTokenClaims(jwt.getClaim(OAuth2Constants.JWT_CLAIM_CLIENT_ID).asString(),
                jwt.getSubject(), jwt.getIssuer(), jwt.getAudience().isEmpty() ? null : jwt.getAudience().getFirst(),
                jwt.getId(), jwt.getExpiresAt() == null ? 0 : jwt.getExpiresAt().getTime(), scopes);
    }

    @Override
    public PreparedSecretRefresh prepare(
            ConfigurableEnvironment environment,
            SecretSnapshotChangedEvent candidate) {
        OAuth2Properties next = Binder.get(environment)
                .bind("platform.component.oauth", OAuth2Properties.class)
                .orElseThrow(() -> new IllegalStateException("OAuth configuration is missing"));
        if (StringUtils.isBlank(next.getTokenSecret())) {
            throw new IllegalStateException("OAuth token Secret must not be blank");
        }
        Duration verificationWindow = next.getSigningKeyVerificationWindow();
        if (verificationWindow == null || verificationWindow.isNegative() || verificationWindow.isZero()) {
            throw new IllegalStateException("OAuth signing key verification window must be positive");
        }
        KeyWindow previous = keys.get();
        Duration previousVerificationWindow = properties.getSigningKeyVerificationWindow();
        if (next.getTokenSecret().equals(previous.current())) {
            return new PreparedSecretRefresh() {
                @Override
                public void commit() {
                    properties.setSigningKeyVerificationWindow(verificationWindow);
                }

                @Override
                public void rollback() {
                    properties.setSigningKeyVerificationWindow(previousVerificationWindow);
                }
            };
        }
        KeyWindow replacement = new KeyWindow(
                next.getTokenSecret(),
                previous.current(),
                Instant.now().plus(verificationWindow));
        String previousProperty = properties.getTokenSecret();
        return new PreparedSecretRefresh() {
            @Override
            public void commit() {
                if (!keys.compareAndSet(previous, replacement)) {
                    throw new IllegalStateException(
                            "OAuth signing key changed while Secret refresh was being prepared");
                }
                properties.setTokenSecret(next.getTokenSecret());
                properties.setSigningKeyVerificationWindow(verificationWindow);
            }

            @Override
            public void rollback() {
                keys.compareAndSet(replacement, previous);
                properties.setTokenSecret(previousProperty);
                properties.setSigningKeyVerificationWindow(previousVerificationWindow);
            }
        };
    }

    private record KeyWindow(String current, String previous, Instant previousValidUntil) {
        boolean previousActive() {
            return previousValidUntil != null && Instant.now().isBefore(previousValidUntil);
        }
    }
}
