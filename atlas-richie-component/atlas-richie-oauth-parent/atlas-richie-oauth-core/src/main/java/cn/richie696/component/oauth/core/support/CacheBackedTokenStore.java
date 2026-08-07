package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.TokenStore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 通过 oauth-cache 抽象访问 atlas-richie-component-cache 的 TokenStore。 */
public class CacheBackedTokenStore implements TokenStore {

    private final OAuthCache cache;

    public CacheBackedTokenStore(OAuthCache cache) {
        this.cache = cache;
    }

    @Override
    public void storeRefreshToken(String refreshToken, String clientId, String ip, ClientConfig config) {
        storeRefreshToken(refreshToken, clientId, ip, config, null);
    }

    @Override
    public void storeRefreshToken(String refreshToken, String clientId, String ip,
                                  ClientConfig config, String resource) {
        long ttlHours = config.getRefreshTokenValidDuration() == null ? 720 : config.getRefreshTokenValidDuration();
        long ttlMillis = ttlHours * 3600_000L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("client_id", clientId);
        data.put("ip", ip == null ? "" : ip);
        data.put("grant_type", "client_credentials");
        data.put("created_at", String.valueOf(System.currentTimeMillis()));
        data.put("expires_at", String.valueOf(System.currentTimeMillis() + ttlMillis));
        if (resource != null && !resource.isBlank()) {
            data.put("resource", resource);
        }
        cache.put(OAuth2RedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken), data, ttlMillis);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, String> loadRefreshToken(String refreshToken) {
        Map<?, ?> value = cache.get(OAuth2RedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken), Map.class);
        if (value == null) {
            return Collections.emptyMap();
        }
        return (Map<String, String>) value;
    }

    @Override
    public RefreshTokenConsumeResult consumeRefreshToken(String refreshToken) {
        String tokenKey = OAuth2RedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken);
        Map<String, String> data = loadRefreshToken(refreshToken);
        if (data == null || data.isEmpty()) {
            Map<String, String> used = cache.get(
                    OAuth2RedisKey.OAUTH2_REFRESH_TOKEN_USED.getKey(refreshToken), Map.class);
            return used == null || used.isEmpty()
                    ? RefreshTokenConsumeResult.notFound()
                    : RefreshTokenConsumeResult.replayed(used);
        }
        cache.remove(tokenKey);
        long remaining = remainingTtl(data.get("expires_at"));
        cache.put(OAuth2RedisKey.OAUTH2_REFRESH_TOKEN_USED.getKey(refreshToken),
                Map.of("client_id", data.getOrDefault("client_id", "")), remaining);
        return RefreshTokenConsumeResult.consumed(data);
    }

    @Override
    public void removeRefreshToken(String refreshToken) {
        cache.remove(OAuth2RedisKey.OAUTH2_REFRESH_TOKEN.getKey(refreshToken));
    }

    @Override
    public void addToBlacklist(String accessToken, long ttlMillis) {
        cache.put(OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_BLACKLIST.getKey(accessToken), "1", ttlMillis);
    }

    @Override
    public boolean isBlacklisted(String accessToken) {
        return cache.exists(OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_BLACKLIST.getKey(accessToken));
    }

    @Override
    public void bindAccessTokenIp(String accessToken, String clientId, String ip, long ttlMillis) {
        cache.put(OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_IP_BIND.getKey(accessToken),
                Map.of("client_id", clientId, "ip", ip == null ? "" : ip), ttlMillis);
    }

    @Override
    public void removeAccessTokenIpBinding(String accessToken) {
        cache.remove(OAuth2RedisKey.OAUTH2_ACCESS_TOKEN_IP_BIND.getKey(accessToken));
    }

    @Override
    public void storeClientRefreshTokenIndex(String clientId, String refreshToken, long ttlMillis) {
        cache.put(OAuth2RedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey(clientId), refreshToken, ttlMillis);
    }

    @Override
    public String getClientRefreshTokenIndex(String clientId) {
        return cache.get(OAuth2RedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey(clientId), String.class);
    }

    @Override
    public void removeClientRefreshTokenIndex(String clientId) {
        cache.remove(OAuth2RedisKey.OAUTH2_CLIENT_REFRESH_TOKEN_INDEX.getKey(clientId));
    }

    @Override
    public long incrementDailyIssueCount(String clientId, String date, long ttlMillis) {
        return cache.increment(OAuth2RedisKey.OAUTH2_DAILY_TOKEN_ISSUE_COUNT.getKey(clientId + ":" + date), 1, ttlMillis);
    }

    @Override
    public long incrementAnomalyRefreshCount(String clientId) {
        return cache.increment(OAuth2RedisKey.OAUTH2_ANOMALY_REFRESH_COUNT.getKey(clientId), 1, 86_400_000L);
    }

    @Override
    public long incrementAnomalyRateLimit(String clientId) {
        return cache.increment(OAuth2RedisKey.OAUTH2_ANOMALY_RATELIMIT.getKey(clientId), 1, 60_000L);
    }

    private long remainingTtl(String expiresAt) {
        try {
            return Math.max(1L, Long.parseLong(expiresAt) - System.currentTimeMillis());
        } catch (Exception ignored) {
            return 86_400_000L;
        }
    }
}
