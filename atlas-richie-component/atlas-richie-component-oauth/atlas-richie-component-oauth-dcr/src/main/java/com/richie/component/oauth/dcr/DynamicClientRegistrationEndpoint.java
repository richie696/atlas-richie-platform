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
package com.richie.component.oauth.dcr;

import com.richie.component.cache.GlobalCache;
import com.richie.component.oauth.core.ClientRegistry;
import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.component.oauth.dcr.dto.ClientRegistrationRequest;
import com.richie.component.oauth.dcr.dto.ClientRegistrationResponse;
import com.richie.component.oauth.dcr.model.ClientIdMetadataDocument;
import com.richie.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import com.richie.component.oauth.dcr.support.SSRFProtection;
import com.richie.contract.exception.BusinessException;
import com.richie.contract.gateway.model.OAuth2Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Dynamic Client Registration Endpoint
 * <p>
 * 处理 OAuth 2.0 动态客户端注册请求（RFC 7591）。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class DynamicClientRegistrationEndpoint {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClientRegistry clientRegistry;
    private final ClientIdMetadataDocumentResolver metadataResolver;
    private final SSRFProtection ssrfProtection;
    private final OAuth2Properties properties;

    public DynamicClientRegistrationEndpoint(
            ClientRegistry clientRegistry,
            ClientIdMetadataDocumentResolver metadataResolver,
            SSRFProtection ssrfProtection,
            OAuth2Properties properties
    ) {
        this.clientRegistry = clientRegistry;
        this.metadataResolver = metadataResolver;
        this.ssrfProtection = ssrfProtection;
        this.properties = properties;
    }

    /**
     * 处理客户端注册请求
     *
     * @param request    注册请求
     * @param httpRequest HTTP 请求
     * @return 注册响应
     */
    public ClientRegistrationResponse registerClient(ClientRegistrationRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (request == null) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "注册请求不能为空");
        }

        List<String> redirectUris = request.getRedirectUris();
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uris 不能为空");
        }

        for (String uri : redirectUris) {
            validateRedirectUri(uri);
        }

        String clientId = generateClientId();
        String clientSecret = generateClientSecret();

        boolean isNoneAuthMethod = "none".equalsIgnoreCase(request.getTokenEndpointAuthMethod());
        if (!isNoneAuthMethod && StringUtils.isBlank(clientSecret)) {
            clientSecret = generateClientSecret();
        }

        long now = System.currentTimeMillis();
        long clientSecretExpiresAt = isNoneAuthMethod ? 0L : (now + TimeUnit.DAYS.toMillis(365));

        ClientIdMetadataDocument metadataDoc = ClientIdMetadataDocument.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName(request.getClientName())
                .redirectUris(redirectUris)
                .tokenEndpointAuthMethod(request.getTokenEndpointAuthMethod())
                .grantTypes(request.getGrantTypes())
                .scopes(request.getScopes())
                .clientUri(request.getClientUri())
                .logoUri(request.getLogoUri())
                .jwksUri(request.getJwksUri())
                .resource(request.getResource())
                .build();

        String redisKey = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId);
        GlobalCache.struct().set(redisKey, metadataDoc, TimeUnit.DAYS.toMillis(365));

        ClientConfig config = ClientConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName(request.getClientName())
                .enabled(true)
                .scopes(request.getScopes() != null ? request.getScopes() : Collections.emptyList())
                .build();

        String configRedisKey = OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId);
        GlobalCache.struct().set(configRedisKey, config, TimeUnit.DAYS.toMillis(365));

        String registrationAccessToken = generateRegistrationAccessToken();
        String regTokenRedisKey = OAuth2RedisKey.OAUTH2_REGISTRATION_TOKEN.getKey(clientId);
        GlobalCache.value().set(regTokenRedisKey, registrationAccessToken, TimeUnit.DAYS.toMillis(365));

        log.info("动态客户端注册成功: clientId={}, clientName={}", clientId, request.getClientName());

        return ClientRegistrationResponse.builder()
                .clientId(clientId)
                .clientSecret(isNoneAuthMethod ? null : clientSecret)
                .clientSecretExpiresAt(isNoneAuthMethod ? 0L : clientSecretExpiresAt)
                .registrationAccessToken(parseLong(registrationAccessToken))
                .registrationClientUri("/oauth/register/" + clientId)
                .clientName(request.getClientName())
                .redirectUris(redirectUris)
                .tokenEndpointAuthMethod(request.getTokenEndpointAuthMethod())
                .grantTypes(request.getGrantTypes())
                .scopes(request.getScopes())
                .clientUri(request.getClientUri())
                .logoUri(request.getLogoUri())
                .resource(request.getResource())
                .build();
    }

    /**
     * 更新已注册的客户端
     *
     * @param clientId   客户端 ID
     * @param request    更新请求
     * @param httpRequest HTTP 请求
     * @return 更新后的响应
     */
    public ClientRegistrationResponse updateClient(String clientId, ClientRegistrationRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (StringUtils.isBlank(clientId)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "client_id 不能为空");
        }

        ClientIdMetadataDocument existingDoc = metadataResolver.resolve(clientId, null);
        if (existingDoc == null) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_CLIENT, "客户端不存在");
        }

        if (request.getRedirectUris() != null && !request.getRedirectUris().isEmpty()) {
            for (String uri : request.getRedirectUris()) {
                validateRedirectUri(uri);
            }
        }

        if (request.getJwksUri() != null && !ssrfProtection.isUrlSafe(request.getJwksUri())) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "jwks_uri 不安全");
        }

        ClientIdMetadataDocument updatedDoc = ClientIdMetadataDocument.builder()
                .clientId(clientId)
                .clientSecret(existingDoc.getClientSecret())
                .clientName(request.getClientName() != null ? request.getClientName() : existingDoc.getClientName())
                .redirectUris(request.getRedirectUris() != null ? request.getRedirectUris() : existingDoc.getRedirectUris())
                .tokenEndpointAuthMethod(request.getTokenEndpointAuthMethod() != null ? request.getTokenEndpointAuthMethod() : existingDoc.getTokenEndpointAuthMethod())
                .grantTypes(request.getGrantTypes() != null ? request.getGrantTypes() : existingDoc.getGrantTypes())
                .scopes(request.getScopes() != null ? request.getScopes() : existingDoc.getScopes())
                .clientUri(request.getClientUri() != null ? request.getClientUri() : existingDoc.getClientUri())
                .logoUri(request.getLogoUri() != null ? request.getLogoUri() : existingDoc.getLogoUri())
                .jwksUri(request.getJwksUri() != null ? request.getJwksUri() : existingDoc.getJwksUri())
                .resource(request.getResource() != null ? request.getResource() : existingDoc.getResource())
                .build();

        String redisKey = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId);
        GlobalCache.struct().set(redisKey, updatedDoc, TimeUnit.DAYS.toMillis(365));

        log.info("动态客户端更新成功: clientId={}", clientId);

        return ClientRegistrationResponse.builder()
                .clientId(clientId)
                .clientSecret(updatedDoc.getClientSecret())
                .clientName(updatedDoc.getClientName())
                .redirectUris(updatedDoc.getRedirectUris())
                .tokenEndpointAuthMethod(updatedDoc.getTokenEndpointAuthMethod())
                .grantTypes(updatedDoc.getGrantTypes())
                .scopes(updatedDoc.getScopes())
                .clientUri(updatedDoc.getClientUri())
                .logoUri(updatedDoc.getLogoUri())
                .resource(updatedDoc.getResource())
                .build();
    }

    private void validateRedirectUri(String uri) {
        if (StringUtils.isBlank(uri)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uri 不能为空");
        }

        if (!ssrfProtection.isUrlSafe(uri)) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uri 不安全");
        }

        try {
            URI parsedUri = URI.create(uri);
            if (!parsedUri.isAbsolute()) {
                throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uri 必须是绝对 URL");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(OAuth2Constants.ERROR_INVALID_REQUEST, "redirect_uri 格式无效");
        }
    }

    private String generateClientId() {
        String datePrefix = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 5; i++) {
            String seq = String.format("%03d", RANDOM.nextInt(1000));
            String candidate = "dcr-%s-%s".formatted(datePrefix, seq);
            String key = OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(candidate);
            if (!GlobalCache.key().hasKey(key)) {
                return candidate;
            }
        }
        return "dcr-%s-%s".formatted(datePrefix, UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    private String generateClientSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateRegistrationAccessToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Long parseLong(String token) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            long value = 0;
            for (byte b : bytes) {
                value = (value << 8) | (b & 0xFF);
            }
            return value;
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }
}
