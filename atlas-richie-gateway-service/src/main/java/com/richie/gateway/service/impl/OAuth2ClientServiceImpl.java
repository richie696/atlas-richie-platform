package com.richie.gateway.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.gateway.constants.GatewayRedisKey;
import com.richie.gateway.service.OAuth2ClientService;
import com.richie.gateway.vo.ThirdPartyClientConfigVO;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 第三方客户端服务实现（网关侧）
 * <p>
 * 负责从 Redis 读取客户端配置，用于网关认证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientServiceImpl implements OAuth2ClientService {

    private static final SecureRandom RANDOM = new SecureRandom();

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getClientConfig(String clientId, ThirdPartyClientConfigVO.Field... fields) {
        if (StringUtils.isBlank(clientId) || fields == null || fields.length == 0) {
            return null;
        }
        String redisKey = GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId);
        if (fields.length == 1) {
            Object rawValue = GlobalCache.getHashCache(redisKey, fields[0].getName(), Object.class);
            Object parsedValue = fields[0].parseRawValue(rawValue);
            if (parsedValue == null) {
                log.debug("客户端配置不存在: clientId={}, field={}", clientId, fields[0].getName());
            }
            return (T) parsedValue;
        }

        Map<ThirdPartyClientConfigVO.Field, Object> resultMap = new EnumMap<>(ThirdPartyClientConfigVO.Field.class);
        List<String> hashKeys = Arrays.stream(fields).map(ThirdPartyClientConfigVO.Field::getName).toList();
        List<Object> rawValues = GlobalCache.getHashCache(redisKey, hashKeys, new TypeReference<>() {
        });

        if (rawValues.size() == fields.length) {
            for (int i = 0; i < fields.length; i++) {
                resultMap.put(fields[i], fields[i].parseRawValue(rawValues.get(i)));
            }
        } else {
            // 当批量返回数量与请求数量不一致时回退到单字段查询，确保兼容性和准确性
            for (ThirdPartyClientConfigVO.Field field : fields) {
                Object rawValue = GlobalCache.getHashCache(redisKey, field.getName(), Object.class);
                resultMap.put(field, field.parseRawValue(rawValue));
            }
        }

        if (resultMap.isEmpty()) {
            log.debug("客户端配置不存在: clientId={}", clientId);
        }
        return (T) resultMap;
    }

    @Override
    public boolean isClientValid(String clientId) {
        Boolean enabled = getClientConfig(clientId, ThirdPartyClientConfigVO.Field.ENABLED);
        return Boolean.TRUE.equals(enabled);
    }

    @Override
    public boolean verifyClientSecret(String clientId, String clientSecret) {
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            return false;
        }

        String storedSecret = getClientConfig(clientId, ThirdPartyClientConfigVO.Field.CLIENT_SECRET);
        if (StringUtils.isBlank(storedSecret)) {
            log.warn("客户端不存在: clientId={}", clientId);
            return false;
        }

        Boolean enabled = getClientConfig(clientId, ThirdPartyClientConfigVO.Field.ENABLED);
        if (!Boolean.TRUE.equals(enabled)) {
            log.warn("客户端已禁用: clientId={}", clientId);
            return false;
        }

        // 验证密钥（使用安全的字符串比较，防止时序攻击）
        return Strings.CS.equals(storedSecret, clientSecret);
    }

    @Override
    public ThirdPartyClientConfigVO registerTestClient(String clientName) {
        if (StringUtils.isBlank(clientName)) {
            throw new IllegalArgumentException("clientName 不能为空");
        }

        String clientId = generateClientId();
        String clientSecret = generateClientSecret();

        ThirdPartyClientConfigVO config = ThirdPartyClientConfigVO.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName(clientName)
                .enabled(true)
                .build();

        var clientConfig = ThirdPartyClientConfigVO.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientName(clientName)
                .enabled(true)
                .ipWhitelist(List.of("localhost", "127.0.0.1")).build();

        String redisKey = GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId);
        // 测试用途：给一个较长的过期时间（例如 365 天）
        long ttl = TimeUnit.DAYS.toMillis(365);
        GlobalCache.addObjectToHash(redisKey, clientConfig, ttl);

        log.info("[TEST] 注册第三方客户端成功: clientId={}, clientName={}", clientId, clientName);
        return config;
    }

    /**
     * 生成类似 client-YYYYMMDD-XXX 的 clientId（XXX 为三位随机数）。
     */
    private String generateClientId() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // 20251217
        for (int i = 0; i < 5; i++) {
            String seq = String.format("%03d", RANDOM.nextInt(1000));
            String candidate = "client-%s-%s".formatted(datePrefix, seq);
            String key = GatewayRedisKey.OAUTH2_CLIENT_CONFIG.getKey(candidate);
            if (!GlobalCache.hasKey(key)) {
                return candidate;
            }
        }
        // 兜底：使用随机 UUID 作为后缀，尽量避免冲突
        return "client-%s-%s".formatted(datePrefix, UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    /**
     * 生成 32 位随机 clientSecret（URL-Safe Base64 格式）。
     */
    private String generateClientSecret() {
        byte[] bytes = new byte[24]; // 24 字节 -> Base64URL 约 32 个字符
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
