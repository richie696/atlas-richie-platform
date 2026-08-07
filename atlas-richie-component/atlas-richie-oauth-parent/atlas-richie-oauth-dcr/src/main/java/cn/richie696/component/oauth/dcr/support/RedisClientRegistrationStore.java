package cn.richie696.component.oauth.dcr.support;

import cn.richie696.component.oauth.cache.LegacyGlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.dcr.model.ClientIdMetadataDocument;
import cn.richie696.component.oauth.dcr.spi.ClientRegistrationStore;

/**
 * 基于 Redis 的 {@link ClientRegistrationStore} 默认实现。
 * <p>
 * 通过 {@link OAuthCache} 同时维护三个 Key:{@code OAUTH2_CLIENT_META} 存 RFC 7591 完整元数据
 * 文档,{@code OAUTH2_CLIENT_CONFIG} 存 OAuth-core 的 ClientConfig,供 ClientRegistry 统一读取;
 * {@code OAUTH2_REGISTRATION_TOKEN} 保存 registration_access_token,用于后续更新/删除。
 * </p>
 * <p>
 * 处于 oauth-dcr 的默认存储实现位置:由
 * {@link cn.richie696.component.oauth.dcr.config.OAuth2DCRAutoConfiguration} 在缺省 Bean 时
 * 注册;同时支持无参构造(走平台 {@code GlobalCache} 适配器)与单参构造(走新版 OAuthCache)。
 * </p>
 * <p>
 * 解决的问题:让 DCR 元数据与 oauth-core 的 ClientConfig 在 Redis 内自动保持一致,业务方替换存储
 * 后端时无需关心两套 Key 的对齐问题;同时保留对旧版 GlobalCache 适配器的兼容入口。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
