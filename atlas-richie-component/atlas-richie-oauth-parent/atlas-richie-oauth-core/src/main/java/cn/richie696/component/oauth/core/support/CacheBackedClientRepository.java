package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.ClientRepository;

/** 使用 OAuthCache 保存客户端权威配置的默认仓储。 */
public final class CacheBackedClientRepository implements ClientRepository {

    private final OAuthCache cache;

    public CacheBackedClientRepository(OAuthCache cache) {
        this.cache = cache;
    }

    @Override
    public ClientConfig find(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        return cache.get(OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId), ClientConfig.class);
    }

    @Override
    public void save(ClientConfig client) {
        if (client == null || client.getClientId() == null || client.getClientId().isBlank()) {
            throw new IllegalArgumentException("client.clientId 不能为空");
        }
        cache.put(OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(client.getClientId()), client,
                365L * 24 * 60 * 60 * 1000);
    }

    @Override
    public void delete(String clientId) {
        cache.remove(OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId));
    }
}
