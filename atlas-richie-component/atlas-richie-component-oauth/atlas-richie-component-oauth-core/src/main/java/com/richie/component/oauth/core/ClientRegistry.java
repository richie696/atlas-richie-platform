package com.richie.component.oauth.core;

import com.richie.component.cache.GlobalCache;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.core.model.ClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 客户端注册中心
 * <p>
 * 负责 OAuth 2.1 客户端配置的读写，数据存储在 Redis Hash。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class ClientRegistry {

    private static final SecureRandom RANDOM = new SecureRandom();

    @SuppressWarnings("unchecked")
    public <T> T getClientConfig(String clientId, ClientConfig.Field field) {
        if (StringUtils.isBlank(clientId) || field == null) {
            return null;
        }

        String redisKey = OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId);
        Object rawValue = GlobalCache.field().get(redisKey, field.getName(), Object.class);
        if (rawValue == null) {
            log.debug("客户端配置不存在: clientId={}, field={}", clientId, field.getName());
            return null;
        }

        return field.parseRawValue(rawValue);
    }

    @SuppressWarnings("unchecked")
    public Map<ClientConfig.Field, Object> getClientConfig(String clientId, ClientConfig.Field field1, ClientConfig.Field field2) {
        if (StringUtils.isBlank(clientId)) {
            return null;
        }

        String redisKey = OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId);

        Object rawValue1 = GlobalCache.field().get(redisKey, field1.getName(), Object.class);
        Object rawValue2 = GlobalCache.field().get(redisKey, field2.getName(), Object.class);

        if (rawValue1 == null && rawValue2 == null) {
            return Collections.emptyMap();
        }

        Map<ClientConfig.Field, Object> result = new java.util.EnumMap<>(ClientConfig.Field.class);
        if (rawValue1 != null) {
            result.put(field1, field1.parseRawValue(rawValue1));
        }
        if (rawValue2 != null) {
            result.put(field2, field2.parseRawValue(rawValue2));
        }

        return result;
    }

    public boolean isClientValid(String clientId) {
        Boolean enabled = getClientConfig(clientId, ClientConfig.Field.ENABLED);
        return Boolean.TRUE.equals(enabled);
    }

    public boolean verifyClientSecret(String clientId, String clientSecret) {
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            return false;
        }

        String storedSecret = getClientConfig(clientId, ClientConfig.Field.CLIENT_SECRET);
        if (StringUtils.isBlank(storedSecret)) {
            log.warn("客户端不存在: clientId={}", clientId);
            return false;
        }

        Boolean enabled = getClientConfig(clientId, ClientConfig.Field.ENABLED);
        if (!Boolean.TRUE.equals(enabled)) {
            log.warn("客户端已禁用: clientId={}", clientId);
            return false;
        }

        return Strings.CS.equals(storedSecret, clientSecret);
    }

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
                .build();

        ClientConfig storageConfig = ClientConfig.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName(clientName)
                .enabled(true)
                .ipWhitelist(List.of("localhost", "127.0.0.1"))
                .build();

        String redisKey = OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId);
        long ttl = TimeUnit.DAYS.toMillis(365);
        GlobalCache.struct().set(redisKey, storageConfig, ttl);

        log.info("[TEST] 注册第三方客户端成功: clientId={}, clientName={}", clientId, clientName);
        return config;
    }

    private String generateClientId() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 5; i++) {
            String seq = String.format("%03d", RANDOM.nextInt(1000));
            String candidate = "client-%s-%s".formatted(datePrefix, seq);
            String key = OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(candidate);
            if (!GlobalCache.key().hasKey(key)) {
                return candidate;
            }
        }
        return "client-%s-%s".formatted(datePrefix, UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    private String generateClientSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
