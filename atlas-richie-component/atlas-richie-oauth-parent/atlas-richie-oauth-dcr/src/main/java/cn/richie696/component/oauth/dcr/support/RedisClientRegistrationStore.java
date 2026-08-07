package cn.richie696.component.oauth.dcr.support;

import cn.richie696.component.oauth.cache.LegacyGlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.dcr.model.ClientIdMetadataDocument;
import cn.richie696.component.oauth.dcr.spi.ClientRegistrationStore;

/** 兼容现有 Redis Key 的 DCR 存储实现。 */
public class RedisClientRegistrationStore implements ClientRegistrationStore {

    private final ClientRegistry clientRegistry;
    private final OAuthCache cache;

    public RedisClientRegistrationStore(ClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
        this.cache = new LegacyGlobalCacheOAuthCache();
    }

    public RedisClientRegistrationStore(OAuthCache cache) {
        this.clientRegistry = null;
        this.cache = cache;
    }

    @Override
    public void save(ClientIdMetadataDocument metadata, ClientConfig client,
                     String registrationAccessToken, long ttlMillis) {
        cache.put(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(metadata.getClientId()), metadata, ttlMillis);
        cache.put(OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(metadata.getClientId()), client, ttlMillis);
        cache.put(OAuth2RedisKey.OAUTH2_REGISTRATION_TOKEN.getKey(metadata.getClientId()), registrationAccessToken, ttlMillis);
    }

    @Override
    public void update(ClientIdMetadataDocument metadata, long ttlMillis) {
        cache.put(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(metadata.getClientId()), metadata, ttlMillis);
    }

    @Override
    public boolean exists(String clientId) {
        return cache.exists(OAuth2RedisKey.OAUTH2_CLIENT_META.getKey(clientId));
    }
}
