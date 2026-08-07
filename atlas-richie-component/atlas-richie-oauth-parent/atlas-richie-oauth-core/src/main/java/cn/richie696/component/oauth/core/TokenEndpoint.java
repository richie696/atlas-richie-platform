/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.oauth.core;

import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.model.TokenIntrospection;
import cn.richie696.component.oauth.core.model.TokenResponse;
import cn.richie696.component.oauth.core.spi.TokenStore;
import cn.richie696.component.oauth.core.spi.TokenStore.RefreshTokenConsumeResult;
import cn.richie696.component.oauth.core.spi.AccessTokenClaimsCustomizer;
import cn.richie696.component.oauth.core.spi.OAuthAuditSink;
import cn.richie696.component.oauth.core.spi.AccessTokenSigner;
import cn.richie696.component.oauth.core.model.ClientAuthenticationRequest;
import cn.richie696.component.oauth.core.model.DeviceAuthorizationRecord;
import cn.richie696.component.oauth.cache.GlobalCacheOAuthCache;
import cn.richie696.component.oauth.core.support.HmacAccessTokenSigner;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.cache.OAuthLock;
import cn.richie696.component.oauth.contract.OAuthErrorCodes;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.component.oauth.contract.OAuth2Constants;
import cn.richie696.context.utils.spring.JwtUtils;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

/**
 * OAuth 2.1 Token 端点
 * <p>
 * 负责 token 全生命周期管理：签发、刷新、验证、撤销。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class TokenEndpoint {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TokenStore tokenStore;
    private final ClientRegistry clientRegistry;
    private final OAuth2Properties properties;
    private final AccessTokenSigner accessTokenSigner;
    private final OAuthCache oauthCache;
    private final AccessTokenClaimsCustomizer claimsCustomizer;
    private final OAuthAuditSink auditSink;
    private final ClientAuthenticationService clientAuthenticationService;
    private final DeviceAuthorizationService deviceAuthorizationService;

    public TokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry, OAuth2Properties properties) {
        this(tokenStore, clientRegistry, properties, null, null);
    }

    public TokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry,
                         OAuth2Properties properties, AccessTokenSigner accessTokenSigner) {
        this(tokenStore, clientRegistry, properties, accessTokenSigner, null);
    }

    public TokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry,
                         OAuth2Properties properties, AccessTokenSigner accessTokenSigner,
                         OAuthCache oauthCache) {
        this(tokenStore, clientRegistry, properties, accessTokenSigner, oauthCache,
                AccessTokenClaimsCustomizer.empty());
    }

    public TokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry,
                         OAuth2Properties properties, AccessTokenSigner accessTokenSigner,
                         OAuthCache oauthCache, AccessTokenClaimsCustomizer claimsCustomizer) {
        this(tokenStore, clientRegistry, properties, accessTokenSigner, oauthCache,
                claimsCustomizer, OAuthAuditSink.noOp());
    }

    public TokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry,
                         OAuth2Properties properties, AccessTokenSigner accessTokenSigner,
                         OAuthCache oauthCache, AccessTokenClaimsCustomizer claimsCustomizer,
                         OAuthAuditSink auditSink) {
        this(tokenStore, clientRegistry, properties, accessTokenSigner, oauthCache,
                claimsCustomizer, auditSink, null, null);
    }

    public TokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry,
                         OAuth2Properties properties, AccessTokenSigner accessTokenSigner,
                         OAuthCache oauthCache, AccessTokenClaimsCustomizer claimsCustomizer,
                         OAuthAuditSink auditSink,
                         ClientAuthenticationService clientAuthenticationService) {
        this(tokenStore, clientRegistry, properties, accessTokenSigner, oauthCache,
                claimsCustomizer, auditSink, clientAuthenticationService, null);
    }

    public TokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry,
                         OAuth2Properties properties, AccessTokenSigner accessTokenSigner,
                         OAuthCache oauthCache, AccessTokenClaimsCustomizer claimsCustomizer,
                         OAuthAuditSink auditSink,
                         ClientAuthenticationService clientAuthenticationService,
                         DeviceAuthorizationService deviceAuthorizationService) {
        this.tokenStore = tokenStore;
        this.clientRegistry = clientRegistry;
        this.properties = properties;
        this.accessTokenSigner = accessTokenSigner;
        this.oauthCache = oauthCache == null ? new GlobalCacheOAuthCache() : oauthCache;
        this.claimsCustomizer = claimsCustomizer == null
                ? AccessTokenClaimsCustomizer.empty() : claimsCustomizer;
        this.auditSink = auditSink == null ? OAuthAuditSink.noOp() : auditSink;
        this.clientAuthenticationService = clientAuthenticationService;
        this.deviceAuthorizationService = deviceAuthorizationService;
    }

    public TokenResponse generateToken(String clientId, String clientSecret, String ip) {
        return generateToken(clientId, clientSecret, ip, null);
    }

    /**
     * client_credentials 签发入口，支持 RFC 8707 resource 参数。
     */
    public TokenResponse generateToken(String clientId, String clientSecret, String ip, String resource) {
        if (!authenticateClient(clientId, clientSecret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端认证失败");
        }

        ClientConfig config = loadClientConfig(clientId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已被禁用");
        }

        if (properties.isEnableDailyIssueLimit()) {
            enforceDailyIssueLimit(clientId, config);
        }

        if (properties.isRevokePreviousTokensOnIssue()) {
            revokePreviousTokensForClient(clientId);
        }

        List<String> finalScopes = config.getScopes() != null ? config.getScopes() : Collections.emptyList();
        if (finalScopes.isEmpty()) {
            log.warn("客户端未配置任何权限范围: clientId={}", clientId);
        }

        String effectiveResource = resolveResource(resource, config);
        String accessToken = generateAccessToken(clientId, config, finalScopes, effectiveResource);
        String refreshToken = generateRefreshToken();

        if (StringUtils.isNotBlank(effectiveResource)) {
            tokenStore.storeRefreshToken(refreshToken, clientId, ip, config, effectiveResource);
        } else {
            tokenStore.storeRefreshToken(refreshToken, clientId, ip, config);
        }

        long expiresIn = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration() * 3600L
                : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;
        long accessTokenTtlMillis = expiresIn * 1000L;

        tokenStore.bindAccessTokenIp(accessToken, clientId, ip, accessTokenTtlMillis);

        TokenResponse response = TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType(OAuth2Constants.TOKEN_TYPE_BEARER)
                .expiresIn(expiresIn)
                .refreshToken(refreshToken)
                .build();
        auditSink.record(new OAuthAuditSink.OAuthAuditEvent(
                "TOKEN_ISSUED", clientId, null, null, effectiveResource, ip,
                true, null, null, Map.of("grant_type", "client_credentials")));
        return response;
    }

    public TokenResponse refreshToken(String refreshToken, String ip) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "刷新令牌不能为空");
        }

        String lockKey = OAuth2RedisKey.OAUTH2_REFRESH_TOKEN_LOCK.getKey(refreshToken);
        try (OAuthLock lock = oauthCache.tryLock(lockKey, 5L)) {
            if (!lock.acquired()) {
                throw new BusinessException(OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED,
                        "刷新令牌正在处理中，请稍后重试");
            }
            return refreshTokenInternal(refreshToken, ip);
        }
    }

    /** RFC 8628 设备码兑换入口。登录、MFA 和用户确认由 DeviceAuthorizationService 的调用方负责。 */
    public TokenResponse exchangeDeviceCode(String clientId, String clientSecret,
                                            String deviceCode, String ip, String resource) {
        if (deviceAuthorizationService == null) {
            throw new BusinessException(OAuth2Constants.ERROR_UNSUPPORTED_GRANT_TYPE,
                    "Device Authorization Grant 未配置");
        }
        if (!authenticateClient(clientId, clientSecret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端认证失败");
        }
        DeviceAuthorizationService.PollResult poll = deviceAuthorizationService.poll(deviceCode, clientId);
        if (!poll.authorized()) {
            throw new BusinessException(poll.errorCode() == null
                    ? OAuth2Constants.ERROR_INVALID_GRANT : poll.errorCode(), "设备授权尚未完成或已失效");
        }
        DeviceAuthorizationRecord record = deviceAuthorizationService.consumeAuthorized(deviceCode, clientId);
        if (record == null) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "设备授权码无效或已使用");
        }
        ClientConfig config = loadClientConfig(clientId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已被禁用");
        }
        String effectiveResource = resolveResource(StringUtils.defaultIfBlank(resource, record.resource()), config);
        List<String> scopes = record.scopes() == null ? List.of() : record.scopes();
        String accessToken = generateAccessToken(clientId, config, scopes, effectiveResource, record.subject());
        String refreshToken = generateRefreshToken();
        if (StringUtils.isNotBlank(effectiveResource)) {
            tokenStore.storeRefreshToken(refreshToken, clientId, ip, config, effectiveResource);
        } else {
            tokenStore.storeRefreshToken(refreshToken, clientId, ip, config);
        }
        long expiresIn = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration() * 3600L : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;
        tokenStore.bindAccessTokenIp(accessToken, clientId, ip, expiresIn * 1000L);
        return TokenResponse.builder().accessToken(accessToken).tokenType(OAuth2Constants.TOKEN_TYPE_BEARER)
                .expiresIn(expiresIn).refreshToken(refreshToken).scope(String.join(" ", scopes)).build();
    }

    private TokenResponse refreshTokenInternal(String refreshToken, String ip) {
        // 这里只做 IP 绑定的预检查；真正的有效性判断和一次性消费由下面的原子 SPI 调用决定。
        Map<String, String> preConsumedTokenData = tokenStore.loadRefreshToken(refreshToken);
        if (preConsumedTokenData != null && !preConsumedTokenData.isEmpty()) {
            validateRefreshTokenIp(preConsumedTokenData, ip);
        }

        RefreshTokenConsumeResult consumeResult = tokenStore.consumeRefreshToken(refreshToken);
        if (consumeResult == null) {
            // TokenStore 契约要求返回非 null 结果；未知消费状态必须拒绝签发。
            log.error("TokenStore.consumeRefreshToken 返回 null，拒绝刷新令牌: tokenHash={}",
                    Integer.toHexString(refreshToken.hashCode()));
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT,
                    "刷新令牌消费状态不可确认");
        } else if (consumeResult.status() == RefreshTokenConsumeResult.Status.REPLAYED) {
            String replayClientId = consumeResult.data().get("client_id");
            long count = StringUtils.isBlank(replayClientId)
                    ? 0 : tokenStore.incrementAnomalyRefreshCount(replayClientId);
            log.warn("检测到 refresh_token 重放: clientId={}, anomalyCount={}", replayClientId, count);
            auditSink.record(new OAuthAuditSink.OAuthAuditEvent(
                    "REFRESH_TOKEN_REPLAYED", replayClientId, null, null, null, ip,
                    false, OAuth2Constants.ERROR_INVALID_GRANT, null, Map.of()));
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "刷新令牌已被使用");
        } else if (consumeResult.status() != RefreshTokenConsumeResult.Status.CONSUMED) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "刷新令牌无效或已使用");
        }

        // 只有 CONSUMED 状态返回的数据才允许进入后续签发流程。
        Map<String, String> consumedTokenData = consumeResult.data();
        if (consumedTokenData == null || consumedTokenData.isEmpty()) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "刷新令牌无效或已使用");
        }

        String clientId = StringUtils.defaultIfBlank(
                consumedTokenData.get(OAuth2Constants.JWT_CLAIM_CLIENT_ID),
                consumedTokenData.get("client_id"));
        ClientConfig config = loadClientConfig(clientId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已被禁用");
        }

        List<String> refreshScopes = config.getScopes() != null ? config.getScopes() : Collections.emptyList();
        String resource = StringUtils.defaultIfBlank(consumedTokenData.get("resource"), config.getResource());
        String newAccessToken = generateAccessToken(clientId, config, refreshScopes, resource);
        String newRefreshToken = generateRefreshToken();

        if (StringUtils.isNotBlank(resource)) {
            tokenStore.storeRefreshToken(newRefreshToken, clientId, ip, config, resource);
        } else {
            tokenStore.storeRefreshToken(newRefreshToken, clientId, ip, config);
        }

        long expiresIn = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration() * 3600L
                : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;
        long accessTokenTtlMillis = expiresIn * 1000L;

        tokenStore.bindAccessTokenIp(newAccessToken, clientId, ip, accessTokenTtlMillis);

        TokenResponse response = TokenResponse.builder()
                .accessToken(newAccessToken)
                .tokenType(OAuth2Constants.TOKEN_TYPE_BEARER)
                .expiresIn(expiresIn)
                .refreshToken(newRefreshToken)
                .build();
        auditSink.record(new OAuthAuditSink.OAuthAuditEvent(
                "TOKEN_REFRESHED", clientId, null, null, resource, ip,
                true, null, null, Map.of("grant_type", "refresh_token")));
        return response;
    }

    /**
     * 验证 Access Token（兼容无 audience 校验的调用）
     */
    public ClientConfig verifyAccessToken(String accessToken) {
        return verifyAccessToken(accessToken, null);
    }

    /**
     * 验证 Access Token（可选 audience 校验）
     * <p>
     * 当 {@code expectedAudience} 不为空时，会额外校验 token 中的 {@code aud} 声明
     * 是否与期望值一致（RFC 8707 Resource Indicator 与 audience 映射）。
     *
     * @param accessToken      Access Token
     * @param expectedAudience 期望的 audience，为空时不校验
     * @return 客户端配置，验证失败返回 null
     */
    public ClientConfig verifyAccessToken(String accessToken, String expectedAudience) {
        String clientId = validateAccessToken(accessToken);
        if (StringUtils.isBlank(clientId)) {
            return null;
        }

        // RFC 8707 / RFC 9728 audience 校验
        if (StringUtils.isNotBlank(expectedAudience)) {
            String tokenAudience = extractAudience(accessToken);
            if (!expectedAudience.equals(tokenAudience)) {
                log.warn("Access token audience 不匹配, expected={}, actual={}",
                        expectedAudience, tokenAudience);
                return null;
            }
        }

        Map<ClientConfig.Field, Object> fieldMap = clientRegistry.getClientConfig(
                clientId, ClientConfig.Field.ENABLED, ClientConfig.Field.SCOPES);
        if (fieldMap == null || fieldMap.isEmpty()) {
            return null;
        }

        Boolean enabled = (Boolean) fieldMap.get(ClientConfig.Field.ENABLED);
        if (!Boolean.TRUE.equals(enabled)) {
            log.debug("客户端不存在或已禁用: clientId={}", clientId);
            return null;
        }

        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) fieldMap.getOrDefault(ClientConfig.Field.SCOPES, Collections.emptyList());

        return ClientConfig.builder()
                .clientId(clientId)
                .enabled(enabled)
                .scopes(scopes)
                .build();
    }

    public List<String> getIpWhitelist(String accessToken) {
        String clientId = validateAccessToken(accessToken);
        if (StringUtils.isBlank(clientId)) {
            return null;
        }

        Map<ClientConfig.Field, Object> fieldMap = clientRegistry.getClientConfig(
                clientId, ClientConfig.Field.ENABLED, ClientConfig.Field.IP_WHITELIST);
        if (fieldMap == null || fieldMap.isEmpty()) {
            return null;
        }

        Boolean enabled = (Boolean) fieldMap.get(ClientConfig.Field.ENABLED);
        if (!Boolean.TRUE.equals(enabled)) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<String> ipWhitelist = (List<String>) fieldMap.getOrDefault(ClientConfig.Field.IP_WHITELIST, Collections.emptyList());
        return ipWhitelist;
    }

    public void revokeToken(String token, String tokenTypeHint) {
        if (StringUtils.isBlank(token)) {
            return;
        }

        boolean isRefreshToken = OAuth2Constants.GRANT_TYPE_REFRESH_TOKEN.equals(tokenTypeHint);
        if (!isRefreshToken) {
            isRefreshToken = !token.contains(".");
        }

        if (isRefreshToken) {
            tokenStore.removeRefreshToken(token);
            log.info("撤销 refresh_token: {}", token);
        } else {
            AccessTokenSigner signer = accessTokenSigner == null
                    ? new HmacAccessTokenSigner(properties) : accessTokenSigner;
            AccessTokenSigner.AccessTokenClaims claims;
            try {
                claims = signer.verify(token);
            } catch (RuntimeException ex) {
                claims = verifyLegacyJwt(token, false);
                if (claims == null) {
                    log.info("撤销 access_token: token 无效或签名错误，直接忽略");
                    return;
                }
            }

            long ttlMillis = claims.expiresAt() - System.currentTimeMillis();
            if (ttlMillis <= 0) {
                log.info("撤销 access_token: 已过期，无需加入黑名单");
                return;
            }

            tokenStore.addToBlacklist(token, ttlMillis);
            tokenStore.removeAccessTokenIpBinding(token);

            log.info("撤销 access_token: 已加入黑名单并移除 IP 绑定，剩余有效期(ms)={}", ttlMillis);
        }
    }

    public TokenIntrospection introspectToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return TokenIntrospection.builder().active(false).build();
        }

        ClientConfig config = verifyAccessToken(accessToken);
        if (config == null) {
            return TokenIntrospection.builder().active(false).build();
        }

        TokenIntrospection.TokenIntrospectionBuilder builder = TokenIntrospection.builder()
                .active(true)
                .clientId(config.getClientId())
                .tokenType(OAuth2Constants.TOKEN_TYPE_BEARER);

        if (config.getScopes() != null && !config.getScopes().isEmpty()) {
            builder.scope(String.join(" ", config.getScopes()));
        }

        return builder.build();
    }

    // ==================== Private Helpers ====================

    private String validateAccessToken(String accessToken) {
        if (StringUtils.isBlank(accessToken)) {
            return null;
        }

        try {
            AccessTokenSigner signer = accessTokenSigner == null
                    ? new HmacAccessTokenSigner(properties) : accessTokenSigner;
            AccessTokenSigner.AccessTokenClaims claims;
            try {
                claims = signer.verify(accessToken);
            } catch (RuntimeException ex) {
                claims = verifyLegacyJwt(accessToken, true);
                if (claims == null) {
                    throw ex;
                }
            }
            if (StringUtils.isBlank(claims.clientId()) || tokenStore.isBlacklisted(accessToken)) {
                return null;
            }
            if (claims.expiresAt() <= System.currentTimeMillis()) {
                log.debug("Access token 已过期");
                return null;
            }
            return claims.clientId();
        } catch (RuntimeException e) {
            log.debug("Access token 签名验证失败", e);
            return null;
        }
    }

    /**
     * 兼容组件升级前由平台 JwtUtils 签发的存量令牌。
     * <p>新签发和新接入的生产代码应注入 {@link AccessTokenSigner}；该回退只服务于平滑升级。</p>
     */
    private AccessTokenSigner.AccessTokenClaims verifyLegacyJwt(String token, boolean requireClientId) {
        String secret = properties.getTokenSecret();
        if (StringUtils.isBlank(secret) || !JwtUtils.verify(token, secret)) {
            return null;
        }
        Date expiresAt = JwtUtils.getExpiredTime(token);
        String clientId = JwtUtils.getArgument(token, "clientId");
        if (expiresAt == null || (requireClientId && StringUtils.isBlank(clientId))) {
            return null;
        }
        return new AccessTokenSigner.AccessTokenClaims(
                clientId, JwtUtils.getUsername(token), null, null, null,
                expiresAt.getTime(), List.of());
    }

    /**
     * 从 JWT 中提取 {@code aud} 声明
     * <p>
     * auth0 库的 {@code withAudience()} 会将 {@code aud} 存储为 JSON 数组，
     * 而 {@code withClaim("aud", ...)} 存储为字符串，此处兼容两种格式。
     */
    private String extractAudience(String accessToken) {
        try {
            var jwt = JWT.decode(accessToken);
            Claim claim = jwt.getClaim("aud");
            if (claim.isNull()) {
                return null;
            }
            List<String> list = claim.asList(String.class);
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
            return claim.asString();
        } catch (Exception e) {
            log.debug("提取 aud 声明失败", e);
            return null;
        }
    }

    private String generateAccessToken(String clientId, ClientConfig config, List<String> finalScopes) {
        return generateAccessToken(clientId, config, finalScopes, null);
    }

    private String generateAccessToken(String clientId, ClientConfig config,
                                       List<String> finalScopes, String resource) {
        return generateAccessToken(clientId, config, finalScopes, resource, null);
    }

    private String generateAccessToken(String clientId, ClientConfig config,
                                       List<String> finalScopes, String resource, String subject) {
        AccessTokenSigner signer = accessTokenSigner == null
                ? new HmacAccessTokenSigner(properties) : accessTokenSigner;
        return signer.sign(clientId, config, finalScopes, resource, subject,
                claimsCustomizer.customize(clientId, config, finalScopes, resource));
    }

    private String resolveResource(String requestedResource, ClientConfig config) {
        String registeredResource = config == null ? null : config.getResource();
        if (StringUtils.isNotBlank(requestedResource)
                && StringUtils.isNotBlank(registeredResource)
                && !registeredResource.equals(requestedResource)) {
            throw new BusinessException(OAuthErrorCodes.INVALID_TARGET, "resource 未注册或不属于当前客户端");
        }
        return StringUtils.defaultIfBlank(requestedResource, registeredResource);
    }

    private void validateRefreshTokenIp(Map<String, String> tokenData, String ip) {
        String boundIp = tokenData.get("ip");
        if (StringUtils.isNotBlank(boundIp) && !boundIp.equals(ip)) {
            log.warn("刷新令牌绑定 IP 不匹配: boundIp={}, currentIp={}", boundIp, ip);
            throw new BusinessException(OAuth2Constants.ERROR_IP_NOT_ALLOWED, "刷新令牌绑定 IP 不匹配");
        }
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void enforceDailyIssueLimit(String clientId, ClientConfig config) {
        int tokenHours = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration()
                : (properties.getDefaultTokenValidDuration() != null ? properties.getDefaultTokenValidDuration() : 1);
        if (tokenHours <= 0) {
            tokenHours = 1;
        }

        int base = 24 / tokenHours;
        if (base <= 0) {
            base = 1;
        }
        int maxIssuesPerDay = base + 2;

        String today = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        long currentCount = tokenStore.incrementDailyIssueCount(clientId, today, TimeUnit.DAYS.toMillis(1));

        if (currentCount > maxIssuesPerDay) {
            log.warn("客户端当日签发次数已达上限, clientId={}, count={}, limit={}", clientId, currentCount, maxIssuesPerDay);
            throw new BusinessException(OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED,
                    "当日签发令牌次数已达上限，请稍后再试");
        }
    }

    private void revokePreviousTokensForClient(String clientId) {
        String previousRefreshToken = tokenStore.getClientRefreshTokenIndex(clientId);
        if (StringUtils.isBlank(previousRefreshToken)) {
            return;
        }

        log.info("立即作废功能：作废客户端之前的 refresh_token, clientId={}, previousRefreshToken={}", clientId, previousRefreshToken);
        tokenStore.removeRefreshToken(previousRefreshToken);
        tokenStore.removeClientRefreshTokenIndex(clientId);
    }

    private ClientConfig loadClientConfig(String clientId) {
        if (StringUtils.isBlank(clientId)) {
            return null;
        }

        ClientConfig repositoryConfig = clientRegistry.getClient(clientId);
        if (repositoryConfig != null) {
            return repositoryConfig;
        }

        Boolean enabled = clientRegistry.getClientConfig(clientId, ClientConfig.Field.ENABLED);
        if (enabled == null) {
            return null;
        }

        return ClientConfig.builder()
                .clientId(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_ID))
                .clientSecret(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET))
                .clientName(clientRegistry.getClientConfig(clientId, ClientConfig.Field.CLIENT_NAME))
                .enabled(enabled)
                .scopes(clientRegistry.getClientConfig(clientId, ClientConfig.Field.SCOPES))
                .tokenValidDuration(clientRegistry.getClientConfig(clientId, ClientConfig.Field.TOKEN_VALID_DURATION))
                .refreshTokenValidDuration(clientRegistry.getClientConfig(clientId, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))
                .rateLimit(clientRegistry.getClientConfig(clientId, ClientConfig.Field.RATE_LIMIT))
                .build();
    }

    private boolean authenticateClient(String clientId, String clientSecret) {
        if (clientAuthenticationService == null) {
            return clientRegistry.verifyClientSecret(clientId, clientSecret);
        }
        return clientAuthenticationService.authenticate(
                new ClientAuthenticationRequest(clientId, clientSecret,
                        clientSecret == null ? "none" : "client_secret_post"))
                .authenticated();
    }
}
