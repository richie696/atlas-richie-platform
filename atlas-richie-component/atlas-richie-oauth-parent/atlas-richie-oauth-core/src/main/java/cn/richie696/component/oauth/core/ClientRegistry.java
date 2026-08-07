/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package cn.richie696.component.oauth.core;

import cn.richie696.component.oauth.cache.GlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.ClientRepository;
import cn.richie696.component.oauth.core.support.CacheBackedClientRepository;
import cn.richie696.component.oauth.core.support.LegacyGlobalCacheClientRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * OAuth 客户端权威数据的领域 Facade。
 * <p>
 * 对外暴露客户端元数据查询、Secret 校验、字段级只读访问;对内将存储细节隐藏在
 * {@link ClientRepository}/{@link OAuthCache} 之后,默认实现走 Redis,业务方可以替换为 JDBC/LDAP
 * 等任意后端。
 * </p>
 * <p>
 * 处于整个 oauth 组件的数据中枢位置:Token 端点用它校验 Secret,AuthorizationEndpoint 用它判断
 * 客户端合法性,DynamicClientRegistrationEndpoint 通过它把 DCR 结果落到统一存储;反向依赖
 * 来自授权码 grant、scope 解析、PKCE 校验等所有需要客户端信息的协议路径。
 * </p>
 * <p>
 * 解决的问题:屏蔽 Redis/JDBC/LDAP 等存储实现差异,把"客户端是否存在、是否启用、Secret 是否匹配"
 * 这些高频校验逻辑收敛到一处,避免在多个端点重复编写。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class ClientRegistry {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final ClientRepository repository;

    /** 兼容旧的直接使用方式，默认走平台 Cache 适配器。 */
    public ClientRegistry() {
        this(new LegacyGlobalCacheClientRepository());
    }

    public ClientRegistry(OAuthCache cache) {
        this(new CacheBackedClientRepository(Objects.requireNonNull(cache, "cache 不能为空")));
    }

    public ClientRegistry(ClientRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository 不能为空");
    }

    public <T> T getClientConfig(String clientId, ClientConfig.Field field) {
        if (StringUtils.isBlank(clientId) || field == null) {
            return null;
        }
        return fieldValue(repository.find(clientId), field);
    }

    public Map<ClientConfig.Field, Object> getClientConfig(String clientId,
                                                            ClientConfig.Field field1,
                                                            ClientConfig.Field field2) {
        if (StringUtils.isBlank(clientId)) {
            return null;
        }
        ClientConfig config = repository.find(clientId);
        if (config == null) {
            return Collections.emptyMap();
        }
        Map<ClientConfig.Field, Object> result = new EnumMap<>(ClientConfig.Field.class);
        Object value1 = fieldValue(config, field1);
        Object value2 = fieldValue(config, field2);
        if (value1 != null) {
            result.put(field1, value1);
        }
        if (value2 != null) {
            result.put(field2, value2);
        }
        return result;
    }

    public boolean isClientValid(String clientId) {
        ClientConfig config = getClient(clientId);
        return config != null && Boolean.TRUE.equals(config.getEnabled());
    }

    public ClientConfig getClient(String clientId) {
        return StringUtils.isBlank(clientId) ? null : repository.find(clientId);
    }

    public boolean verifyClientSecret(String clientId, String clientSecret) {
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            return false;
        }
        ClientConfig config = getClient(clientId);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())
                || StringUtils.isBlank(config.getClientSecret())) {
            return false;
        }
        return MessageDigest.isEqual(config.getClientSecret().getBytes(StandardCharsets.UTF_8),
                clientSecret.getBytes(StandardCharsets.UTF_8));
    }

    /** 仅供测试和演示使用；生产客户端应由服务层审批后注册。 */
    public ClientConfig registerTestClient(String clientName) {
        if (StringUtils.isBlank(clientName)) {
            throw new IllegalArgumentException("clientName 不能为空");
        }
        String clientId = generateClientId();
        String clientSecret = generateClientSecret();
        ClientConfig config = ClientConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName(clientName)
                .enabled(true)
                .tokenEndpointAuthMethod("client_secret_post")
                .ipWhitelist(List.of("localhost", "127.0.0.1"))
                .build();
        repository.save(config);
        log.info("[TEST] 注册 OAuth 客户端成功: clientId={}, clientName={}", clientId, clientName);
        return config;
    }

    @SuppressWarnings("unchecked")
    private <T> T fieldValue(ClientConfig config, ClientConfig.Field field) {
        if (config == null || field == null) {
            return null;
        }
        Object value = switch (field) {
            case CLIENT_ID -> config.getClientId();
            case CLIENT_SECRET -> config.getClientSecret();
            case CLIENT_NAME -> config.getClientName();
            case ENABLED -> config.getEnabled();
            case SCOPES -> config.getScopes();
            case REDIRECT_URIS -> config.getRedirectUris();
            case GRANT_TYPES -> config.getGrantTypes();
            case TOKEN_ENDPOINT_AUTH_METHOD -> config.getTokenEndpointAuthMethod();
            case RESOURCE -> config.getResource();
            case IP_WHITELIST -> config.getIpWhitelist();
            case TOKEN_VALID_DURATION -> config.getTokenValidDuration();
            case REFRESH_TOKEN_VALID_DURATION -> config.getRefreshTokenValidDuration();
            case RATE_LIMIT -> config.getRateLimit();
        };
        return (T) value;
    }

    private String generateClientId() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 5; i++) {
            String candidate = "client-%s-%03d".formatted(datePrefix, RANDOM.nextInt(1000));
            if (repository.find(candidate) == null) {
                return candidate;
            }
        }
        return "client-%s-%s".formatted(datePrefix,
                UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    private String generateClientSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
