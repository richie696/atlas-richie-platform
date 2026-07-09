/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.oauth.authz;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.component.oauth.core.model.TokenResponse;
import com.richie.component.oauth.core.spi.TokenStore;
import com.richie.contract.exception.BusinessException;
import com.richie.contract.gateway.model.OAuth2Constants;
import com.richie.context.utils.spring.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth 2.1 授权码模式处理
 * <p>
 * 处理授权码模式下的 code→token 交换流程。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class AuthorizationCodeGrant {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TokenStore tokenStore;
    private final ClientRegistry clientRegistry;
    private final AuthorizationCodeStore authzCodeStore;
    private final PKCESupport pkceSupport;
    private final OAuth2Properties properties;

    public AuthorizationCodeGrant(
            TokenStore tokenStore,
            ClientRegistry clientRegistry,
            AuthorizationCodeStore authzCodeStore,
            PKCESupport pkceSupport,
            OAuth2Properties properties
    ) {
        this.tokenStore = tokenStore;
        this.clientRegistry = clientRegistry;
        this.authzCodeStore = authzCodeStore;
        this.pkceSupport = pkceSupport;
        this.properties = properties;
    }

    /**
     * 使用授权码换取 Token
     *
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥
     * @param code         授权码
     * @param codeVerifier PKCE code_verifier
     * @param redirectUri  重定向 URI（需与授权请求一致）
     * @param resource     RFC 8707 resource 参数
     * @param ip           客户端 IP
     * @return Token 响应
     */
    public TokenResponse exchangeCodeForToken(
            String clientId,
            String clientSecret,
            String code,
            String codeVerifier,
            String redirectUri,
            String resource,
            String ip
    ) {
        if (!clientRegistry.verifyClientSecret(clientId, clientSecret)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端认证失败");
        }

        Map<String, String> codeData = authzCodeStore.loadAuthorizationCode(code);
        if (codeData == null || codeData.isEmpty()) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "授权码无效或已过期");
        }

        if (!clientId.equals(codeData.get("clientId"))) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "客户端 ID 不匹配");
        }

        String storedRedirectUri = codeData.get("redirectUri");
        if (StringUtils.isNotBlank(redirectUri) && !redirectUri.equals(storedRedirectUri)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "重定向 URI 不匹配");
        }

        String codeChallenge = codeData.get("codeChallenge");
        String codeChallengeMethod = codeData.get("codeChallengeMethod");
        if (StringUtils.isNotBlank(codeChallenge) && !"plain".equalsIgnoreCase(codeChallengeMethod)) {
            if (!pkceSupport.verifyChallenge(codeChallenge, codeChallengeMethod, codeVerifier)) {
                throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "PKCE 验证失败");
            }
        }

        authzCodeStore.consumeAuthorizationCode(code);

        ClientConfig config = loadClientConfig(clientId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已禁用");
        }

        String scopesStr = codeData.get("scopes");
        List<String> scopes = StringUtils.isNotBlank(scopesStr)
                ? Arrays.asList(scopesStr.split("\\s+"))
                : (config.getScopes() != null ? config.getScopes() : Collections.emptyList());

        String accessToken = generateAccessToken(clientId, config, scopes, resource);
        String refreshToken = generateRefreshToken();

        tokenStore.storeRefreshToken(refreshToken, clientId, ip, config);

        long expiresIn = config.getTokenValidDuration() != null
                ? config.getTokenValidDuration() * 3600L
                : OAuth2Constants.DEFAULT_ACCESS_TOKEN_EXPIRES_IN;
        long ttlMillis = expiresIn * 1000L;
        tokenStore.bindAccessTokenIp(accessToken, clientId, ip, ttlMillis);

        log.info("授权码换 Token 成功: clientId={}, ip={}", clientId, ip);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType(OAuth2Constants.TOKEN_TYPE_BEARER)
                .expiresIn(expiresIn)
                .refreshToken(refreshToken)
                .scope(String.join(" ", scopes))
                .build();
    }

    private String generateAccessToken(String clientId, ClientConfig config, List<String> scopes, String resource) {
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
        if (scopes != null && !scopes.isEmpty()) {
            params.put(OAuth2Constants.JWT_CLAIM_SCOPE, String.join(" ", scopes));
        }

        if (StringUtils.isNotBlank(resource)) {
            params.put("aud", resource);
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
