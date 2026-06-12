package com.richie.component.oauth.core.support;

import com.richie.component.cache.GlobalCache;
import com.richie.component.oauth.core.config.OAuth2RedisKey;
import com.richie.component.oauth.core.model.ClientConfig;
import com.richie.component.oauth.core.spi.TokenStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 实现的 TokenStore
 * <p>
 * 使用 GlobalCache（Redis）存储 refresh_token、access_token 黑名单、IP 绑定等数据。
 *
 * @author richie696
 * @since 2026-06-12
 */
@Slf4j
public class DefaultTokenStore implements TokenStore {

    @Override
    public void storeRefreshToken(String refreshToken, String clientId, String ip, ClientConfig config) {
        String key = OAuth2RedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken);

        long ttlHours = config.getRefreshTokenValidDuration() != null
                ? config.getRefreshTokenValidDuration()
                : 720;
        long ttl = ttlHours * 60 * 60 * 1000;

        Map<String, Object> data = Map.of(
                "client_id", clientId,
                "ip", ip != null ? ip : "",
                "grant_type", "client_credentials",
                "created_at", String.valueOf(System.currentTimeMillis())
        );

        GlobalCache.struct().set(key, data, ttl);

        log.debug("存储 refresh_token: key={}, clientId={}, ttl={}h", key, clientId, ttlHours);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> loadRefreshToken(String refreshToken) {
        String key = OAuth2RedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken);
        return GlobalCache.field().getAll(key, String.class);
    }

    @Override
    public void removeRefreshToken(String refreshToken) {
        String key = OAuth2RedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken);
        GlobalCache.key().removeCache(key);
    }

    @Override
    public void addToBlacklist(String accessToken, long ttlMillis) {
        String key = OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_BLACKLIST.getKey(accessToken);
        GlobalCache.value().set(key, "1", ttlMillis);
    }

    @Override
    public boolean isBlacklisted(String accessToken) {
        String key = OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_BLACKLIST.getKey(accessToken);
        return GlobalCache.key().hasKey(key);
    }

    @Override
    public void bindAccessTokenIp(String accessToken, String clientId, String ip, long ttlMillis) {
        String key = OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_IP_BIND.getKey(accessToken);
        Map<String, Object> data = Map.of(
                "client_id", clientId,
                "ip", ip != null ? ip : ""
        );
        GlobalCache.struct().set(key, data, ttlMillis);
    }

    @Override
    public void removeAccessTokenIpBinding(String accessToken) {
        String key = OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_IP_BIND.getKey(accessToken);
        GlobalCache.key().removeCache(key);
    }

    @Override
    public void storeClientRefreshTokenIndex(String clientId, String refreshToken, long ttlMillis) {
        String key = OAuth2RedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey(clientId);
        GlobalCache.value().set(key, refreshToken, ttlMillis);
    }

    @Override
    public String getClientRefreshTokenIndex(String clientId) {
        String key = OAuth2RedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey(clientId);
        return GlobalCache.value().get(key, String.class);
    }

    @Override
    public void removeClientRefreshTokenIndex(String clientId) {
        String key = OAuth2RedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey(clientId);
        GlobalCache.key().removeCache(key);
    }

    @Override
    public long incrementDailyIssueCount(String clientId, String date, long ttlMillis) {
        String key = OAuth2RedisKey.OAUTH2_DAILY_TOKEN_ISSUE_COUNT.getKey(clientId + ":" + date);
        return GlobalCache.value().increment(key, 1L);
    }

    @Override
    public long incrementAnomalyRefreshCount(String clientId) {
        String key = OAuth2RedisKey.OAUTH2_ANOMALY_REFRESH_COUNT.getKey(clientId);
        return GlobalCache.value().increment(key, 1L);
    }

    @Override
    public long incrementAnomalyRateLimit(String clientId) {
        String key = OAuth2RedisKey.OAUTH2_ANOMALY_RATELIMIT.getKey(clientId);
        return GlobalCache.value().increment(key, 1L);
    }
}
