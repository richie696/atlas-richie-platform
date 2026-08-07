package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.ClientRepository;

/**
 * 基于 {@link OAuthCache} 的 {@link ClientRepository} 默认实现。
 * <p>
 * 通过 OAuthCache 抽象访问 atlas-richie-component-cache,把客户端配置写到 Redis Hash,所有 Key
 * 前缀走 {@link cn.richie696.component.oauth.core.config.OAuth2RedisKey} 统一管理。
 * </p>
 * <p>
 * 处于 oauth-core 的默认存储实现位置:由 {@link cn.richie696.component.oauth.core.config.OAuth2AutoConfiguration}
 * 在缺省 Bean 时注册,被 {@link cn.richie696.component.oauth.core.ClientRegistry} 持有;OAuth Service 可整体替换 ClientRepository,
 * 本实现仅作为开箱即用的轻量选项。
 * </p>
 * <p>
 * 解决的问题:在没有现成 ClientRepository 的场景下,提供一个零配置、跨节点一致的 Redis 实现,让
 * {@link cn.richie696.component.oauth.core.ClientRegistry} 直接可用,同时把 Key 命名收敛到 {@code OAuth2RedisKey} 防止散落。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
