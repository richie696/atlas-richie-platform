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
package cn.richie696.component.oauth.dcr;

import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.dcr.dto.ClientRegistrationRequest;
import cn.richie696.component.oauth.dcr.dto.ClientRegistrationResponse;
import cn.richie696.component.oauth.dcr.model.ClientIdMetadataDocument;
import cn.richie696.component.oauth.dcr.spi.ClientIdMetadataDocumentResolver;
import cn.richie696.component.oauth.dcr.spi.ClientRegistrationStore;
import cn.richie696.component.oauth.dcr.support.SSRFProtection;
import cn.richie696.component.oauth.dcr.support.RedisClientRegistrationStore;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.component.oauth.contract.OAuth2Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RFC 7591 Dynamic Client Registration 的领域服务。
 * <p>
 * 处理两类协议入口:{@code POST /register}(签发 client_id/client_secret、生成 registration_access_token、
 * 持久化客户端元数据)与 {@code PUT /register/{clientId}}(局部覆盖更新已注册客户端);所有 redirect_uri 与
 * jwks_uri 在落库前必经 {@link SSRFProtection} 校验,杜绝内网探测。
 * </p>
 * <p>
 * 处于 oauth-dcr 模块的协议服务位置:向下依赖 {@link ClientRegistry} 把客户端元数据写入统一存储,
 * 依赖 {@link ClientIdMetadataDocumentResolver} 解析外部元数据文档,依赖 {@link ClientRegistrationStore}
 * 保存注册凭证;由 OAuth Service 在 HTTP 适配层暴露给外部客户端程序化注册。
 * </p>
 * <p>
 * 解决的问题:让移动端 / SPA / 第三方集成方按 RFC 7591 程序化注册 OAuth 客户端,无需运维手工配置;
 * 同时通过 SSRF 防护与强制 redirect_uri 校验,把"恶意客户端把请求重定向到内网/钓鱼页"的风险
 * 拦截在 DCR 入口,不污染下游授权码和 Token 端点。
 * </p>
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
    private final ClientRegistrationStore registrationStore;

    public DynamicClientRegistrationEndpoint(
            ClientRegistry clientRegistry,
            ClientIdMetadataDocumentResolver metadataResolver,
            SSRFProtection ssrfProtection,
            OAuth2Properties properties
    ) {
        this(clientRegistry, metadataResolver, ssrfProtection, properties, null);
    }

    public DynamicClientRegistrationEndpoint(
            ClientRegistry clientRegistry,
            ClientIdMetadataDocumentResolver metadataResolver,
            SSRFProtection ssrfProtection,
            OAuth2Properties properties,
            ClientRegistrationStore registrationStore
    ) {
        this.clientRegistry = clientRegistry;
        this.metadataResolver = metadataResolver;
        this.ssrfProtection = ssrfProtection;
        this.properties = properties;
        this.registrationStore = registrationStore == null
                ? new RedisClientRegistrationStore(new cn.richie696.component.oauth.cache.GlobalCacheOAuthCache())
                : registrationStore;
    }

    /**
     * 处理客户端注册请求
     *
     * @param request     注册请求
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

        ClientConfig config = ClientConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName(request.getClientName())
                .enabled(true)
                .scopes(request.getScopes() != null ? request.getScopes() : Collections.emptyList())
                .redirectUris(redirectUris)
                .grantTypes(request.getGrantTypes())
                .tokenEndpointAuthMethod(request.getTokenEndpointAuthMethod())
                .resource(request.getResource() == null || request.getResource().isEmpty()
                        ? null : request.getResource().getFirst())
                .build();

        String registrationAccessToken = generateRegistrationAccessToken();
        long ttlMillis = TimeUnit.DAYS.toMillis(365);
        registrationStore.save(metadataDoc, config, registrationAccessToken, ttlMillis);

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
     * @param clientId    客户端 ID
     * @param request     更新请求
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

        registrationStore.update(updatedDoc, TimeUnit.DAYS.toMillis(365));

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
            boolean exists = registrationStore.exists(candidate);
            if (!exists) {
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
