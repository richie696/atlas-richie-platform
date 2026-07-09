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
package com.richie.component.oauth.core;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.Claim;
import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.redis.manage.CacheLock;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.component.oauth.core.model.TokenIntrospection;
import com.richie.component.oauth.core.model.TokenResponse;
import com.richie.component.oauth.core.spi.TokenStore;
import com.richie.contract.exception.BusinessException;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.context.utils.spring.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    public TokenEndpoint(TokenStore tokenStore, ClientRegistry clientRegistry, OAuth2Properties properties) {
        this.tokenStore = tokenStore;
        this.clientRegistry = clientRegistry;
        this.properties = properties;
    }

    public TokenResponse generateToken(String clientId, String clientSecret, String ip) {
        if (!clientRegistry.verifyClientSecret(clientId, clientSecret)) {
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

        String accessToken = generateAccessToken(clientId, config, finalScopes);
        String refreshToken = generateRefreshToken();

        tokenStore.storeRefreshToken(refreshToken, clientId, ip, config);

        long expiresIn = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration() * 3600L
                : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;
        long accessTokenTtlMillis = expiresIn * 1000L;

        tokenStore.bindAccessTokenIp(accessToken, clientId, ip, accessTokenTtlMillis);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType(OAuth2Constants.TOKEN_TYPE_BEARER)
                .expiresIn(expiresIn)
                .refreshToken(refreshToken)
                .build();
    }

    public TokenResponse refreshToken(String refreshToken, String ip) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "刷新令牌不能为空");
        }

        String lockKey = OAuth2RedisKey.OAUTH2_REFRESH_TOKEN_LOCK.getKey(refreshToken);

        try (CacheLock lock = GlobalCache.lock().optimisticWithRenewal(lockKey, 5L)) {
            if (!lock.isSuccess()) {
                throw new BusinessException(OAuth2Constants.ERROR_RATE_LIMIT_EXCEEDED,
                        "刷新令牌正在处理中，请稍后重试");
            }

            Map<String, String> tokenData = tokenStore.loadRefreshToken(refreshToken);
            if (tokenData == null || tokenData.isEmpty()) {
                throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "刷新令牌无效或已使用");
            }

            String boundIp = tokenData.get("ip");
            if (StringUtils.isNotBlank(boundIp) && !boundIp.equals(ip)) {
                log.warn("刷新令牌绑定 IP 不匹配: boundIp={}, currentIp={}", boundIp, ip);
                throw new BusinessException(OAuth2Constants.ERROR_IP_NOT_ALLOWED, "刷新令牌绑定 IP 不匹配");
            }

            tokenStore.removeRefreshToken(refreshToken);

            String clientId = tokenData.get(OAuth2Constants.JWT_CLAIM_CLIENT_ID);
            ClientConfig config = loadClientConfig(clientId);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已被禁用");
            }

            List<String> refreshScopes = config.getScopes() != null ? config.getScopes() : Collections.emptyList();
            String newAccessToken = generateAccessToken(clientId, config, refreshScopes);
            String newRefreshToken = generateRefreshToken();

            tokenStore.storeRefreshToken(newRefreshToken, clientId, ip, config);

            long expiresIn = config.getTokenValidDuration() != null
                    ? config.getTokenValidDuration() * 3600L
                    : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;
            long accessTokenTtlMillis = expiresIn * 1000L;

            tokenStore.bindAccessTokenIp(newAccessToken, clientId, ip, accessTokenTtlMillis);

            return TokenResponse.builder()
                    .accessToken(newAccessToken)
                    .tokenType(OAuth2Constants.TOKEN_TYPE_BEARER)
                    .expiresIn(expiresIn)
                    .refreshToken(newRefreshToken)
                    .build();
        }
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
            String tokenSecret = properties.getTokenSecret();
            if (StringUtils.isBlank(tokenSecret)) {
                log.warn("撤销 access_token 失败，Token 密钥未配置");
                return;
            }

            if (!JwtUtils.verify(token, tokenSecret)) {
                log.info("撤销 access_token: token 无效或签名错误，直接忽略");
                return;
            }

            Date expiredTime = JwtUtils.getExpiredTime(token);
            if (expiredTime == null) {
                log.info("撤销 access_token: 无过期时间，直接忽略");
                return;
            }

            long now = System.currentTimeMillis();
            long ttlMillis = expiredTime.getTime() - now;
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

        String tokenSecret = properties.getTokenSecret();
        if (StringUtils.isBlank(tokenSecret)) {
            log.warn("第三方系统 Token 密钥未配置");
            return null;
        }
        if (!JwtUtils.verify(accessToken, tokenSecret)) {
            log.debug("Access token 签名验证失败");
            return null;
        }

        if (tokenStore.isBlacklisted(accessToken)) {
            log.debug("Access token 已被拉入黑名单，拒绝访问");
            return null;
        }

        Date expiredTime = JwtUtils.getExpiredTime(accessToken);
        if (expiredTime == null || expiredTime.getTime() < System.currentTimeMillis()) {
            log.debug("Access token 已过期");
            return null;
        }

        String clientId = JwtUtils.getArgument(accessToken, OAuth2Constants.JWT_CLAIM_CLIENT_ID);
        if (StringUtils.isBlank(clientId)) {
            log.debug("Access token 中未找到 clientId");
            return null;
        }
        return clientId;
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
        String tokenSecret = properties.getTokenSecret();
        if (StringUtils.isBlank(tokenSecret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CONFIG, "Token 密钥未配置");
        }

        long expiresIn = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration() * 3600L * 1000L
                : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN * 1000L;
        long expiredTime = System.currentTimeMillis() + expiresIn;

        Map<String, String> params = new HashMap<>();
        params.put(OAuth2Constants.JWT_CLAIM_CLIENT_ID, clientId);
        params.put(OAuth2Constants.JWT_CLAIM_TYPE, OAuth2Constants.JWT_CLAIM_TYPE_THIRD_PARTY);
        if (finalScopes != null && !finalScopes.isEmpty()) {
            params.put(OAuth2Constants.JWT_CLAIM_SCOPE, String.join(" ", finalScopes));
        }

        return generateJwtToken(clientId, params, tokenSecret, expiredTime);
    }

    private String generateJwtToken(String username, Map<String, String> params, String secret, long expiredTime) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTCreator.Builder builder = JWT.create()
                .withClaim(OAuth2Constants.JWT_CLAIM_USERNAME, username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(expiredTime))
                .withJWTId(UUID.randomUUID().toString())
                .withIssuer("Richie Inc.")
                .withSubject(OAuth2Constants.JWT_SUBJECT_THIRD_PARTY_ACCESS_TOKEN)
                .withAudience(username);

        if (params != null) {
            params.forEach(builder::withClaim);
        }

        return builder.sign(algorithm);
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
}
