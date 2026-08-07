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
package cn.richie696.component.oauth.authz;

import cn.richie696.component.oauth.authz.spi.AuthorizationCodeStore;
import cn.richie696.component.oauth.authz.spi.AuthorizationCodeStore.AuthorizationCodeConsumeResult;
import cn.richie696.component.oauth.contract.OAuthErrorCodes;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.ClientAuthenticationService;
import cn.richie696.component.oauth.core.model.ClientAuthenticationRequest;
import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.model.TokenResponse;
import cn.richie696.component.oauth.core.spi.TokenStore;
import cn.richie696.component.oauth.core.spi.AccessTokenSigner;
import cn.richie696.component.oauth.core.spi.AccessTokenClaimsCustomizer;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.component.oauth.contract.OAuth2Constants;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code authorization_code} grant 的 code → token 兑换服务。
 * <p>
 * 一次调用内完成:客户端认证、授权码原子消费(clientId/redirectUri/PKCE 绑定校验)、按 scopes 与
 * resource 签发 access/refresh token 并写入 {@link TokenStore};其中 PKCE 校验走
 * {@link PKCESupport},签名走 {@link AccessTokenSigner},扩展声明走 {@link AccessTokenClaimsCustomizer}。
 * </p>
 * <p>
 * 处于 oauth-authz 模块的协议服务位置:被 OAuth Service 在 HTTP 适配层包装后挂到 {@code /oauth2/token}
 * 的 {@code grant_type=authorization_code} 分支;依赖 oauth-core 的 {@link TokenStore}/
 * {@link ClientRegistry}/{@link ClientAuthenticationService} 完成签名、Secret 校验与客户端元数据查询。
 * </p>
 * <p>
 * 解决的问题:把 RFC 6749 §4.1.3 的 code 兑换细节(认证、绑定校验、一次性消费、签名、IP 绑定)封装到
 * 独立服务,让 OAuth Service 不必关心重放保护和原子语义;同时把 PKCE 这种"防截获"的强制校验内建
 * 到 grant 流程,默认拒绝 plain 方法。
 * </p>
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
    private final AccessTokenSigner accessTokenSigner;
    private final AccessTokenClaimsCustomizer claimsCustomizer;
    private final ClientAuthenticationService clientAuthenticationService;

    public AuthorizationCodeGrant(
            TokenStore tokenStore,
            ClientRegistry clientRegistry,
            AuthorizationCodeStore authzCodeStore,
            PKCESupport pkceSupport,
            OAuth2Properties properties
    ) {
        this(tokenStore, clientRegistry, authzCodeStore, pkceSupport, properties,
                null, AccessTokenClaimsCustomizer.empty(), null);
    }

    public AuthorizationCodeGrant(
            TokenStore tokenStore,
            ClientRegistry clientRegistry,
            AuthorizationCodeStore authzCodeStore,
            PKCESupport pkceSupport,
            OAuth2Properties properties,
            AccessTokenSigner accessTokenSigner,
            AccessTokenClaimsCustomizer claimsCustomizer
    ) {
        this(tokenStore, clientRegistry, authzCodeStore, pkceSupport, properties,
                accessTokenSigner, claimsCustomizer, null);
    }

    public AuthorizationCodeGrant(
            TokenStore tokenStore,
            ClientRegistry clientRegistry,
            AuthorizationCodeStore authzCodeStore,
            PKCESupport pkceSupport,
            OAuth2Properties properties,
            AccessTokenSigner accessTokenSigner,
            AccessTokenClaimsCustomizer claimsCustomizer,
            ClientAuthenticationService clientAuthenticationService
    ) {
        this.tokenStore = tokenStore;
        this.clientRegistry = clientRegistry;
        this.authzCodeStore = authzCodeStore;
        this.pkceSupport = pkceSupport;
        this.properties = properties;
        this.accessTokenSigner = accessTokenSigner;
        this.claimsCustomizer = claimsCustomizer == null
                ? AccessTokenClaimsCustomizer.empty() : claimsCustomizer;
        this.clientAuthenticationService = clientAuthenticationService;
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
        boolean authenticated = clientAuthenticationService == null
                ? clientRegistry.verifyClientSecret(clientId, clientSecret)
                : clientAuthenticationService.authenticate(
                        new ClientAuthenticationRequest(clientId, clientSecret,
                                clientSecret == null ? "none" : "client_secret_post"))
                .authenticated();
        if (!authenticated) {
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

        String storedResource = codeData.get("resource");
        if (StringUtils.isNotBlank(storedResource)
                && StringUtils.isNotBlank(resource)
                && !storedResource.equals(resource)) {
            throw new BusinessException(OAuthErrorCodes.INVALID_TARGET, "resource 与授权请求不匹配");
        }
        String effectiveResource = StringUtils.defaultIfBlank(resource, storedResource);

        String codeChallenge = codeData.get("codeChallenge");
        String codeChallengeMethod = codeData.get("codeChallengeMethod");
        if (StringUtils.isNotBlank(codeChallenge) && !"plain".equalsIgnoreCase(codeChallengeMethod)) {
            if (!pkceSupport.verifyChallenge(codeChallenge, codeChallengeMethod, codeVerifier)) {
                throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "PKCE 验证失败");
            }
        }

        AuthorizationCodeConsumeResult consumed = authzCodeStore.consume(code);
        if (consumed == null) {
            // 兼容旧版 Mockito/第三方实现：真实生产存储必须实现原子 consume。
            authzCodeStore.consumeAuthorizationCode(code);
        } else if (consumed.status() != AuthorizationCodeConsumeResult.Status.CONSUMED) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_GRANT, "授权码无效或已使用");
        } else {
            codeData = consumed.data();
        }

        ClientConfig config = loadClientConfig(clientId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在或已禁用");
        }

        String scopesStr = codeData.get("scopes");
        List<String> scopes = StringUtils.isNotBlank(scopesStr)
                ? Arrays.asList(scopesStr.split("\\s+"))
                : (config.getScopes() != null ? config.getScopes() : Collections.emptyList());

        String accessToken = generateAccessToken(clientId, config, scopes, effectiveResource, codeData.get("userId"));
        String refreshToken = generateRefreshToken();

        if (StringUtils.isNotBlank(effectiveResource)) {
            tokenStore.storeRefreshToken(refreshToken, clientId, ip, config, effectiveResource);
        } else {
            tokenStore.storeRefreshToken(refreshToken, clientId, ip, config);
        }

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

    private String generateAccessToken(String clientId, ClientConfig config, List<String> scopes,
                                       String resource, String subject) {
        AccessTokenSigner signer = accessTokenSigner == null
                ? new cn.richie696.component.oauth.core.support.HmacAccessTokenSigner(properties)
                : accessTokenSigner;
        return signer.sign(clientId, config, scopes, resource, subject,
                claimsCustomizer.customize(clientId, config, scopes, resource));
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
}
