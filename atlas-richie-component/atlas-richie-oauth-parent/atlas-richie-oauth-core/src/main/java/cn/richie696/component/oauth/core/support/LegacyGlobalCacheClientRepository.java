package cn.richie696.component.oauth.core.support;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.spi.ClientRepository;

import java.util.List;
import java.util.Map;

/**
 * 旧版 Redis Hash 客户端存储的兼容适配器。
 * <p>
 * 通过平台 {@code GlobalCache} 的 field/struct/key 三类操作读取字段式客户端配置,与新版的
 * {@link CacheBackedClientRepository} 不同,本类按字段逐个读取后组装;同时保留对只提供 key/struct
 * 操作的测试/适配环境的容错。
 * </p>
 * <p>
 * 处于 oauth-core 的兼容适配位置:仅由 {@link ClientRegistry} 的无参兼容构造方法使用,新的服务
 * 应注入 ClientRepository 或 OAuthCache,而非直接依赖本类。
 * </p>
 * <p>
 * 解决的问题:让既有的"按字段分散存储"的客户端元数据无需一次性迁移,即可被新版 ClientRegistry 消费;
 * 同时把兼容边界封装在一个独立类里,避免污染正式实现。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class LegacyGlobalCacheClientRepository implements ClientRepository {

    @Override
    public ClientConfig find(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        String key = OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId);
        Map<String, Object> values = new java.util.HashMap<>();
        try {
            if (GlobalCache.field() != null) {
                for (ClientConfig.Field field : ClientConfig.Field.values()) {
                    Object value = GlobalCache.field().get(key, field.getName(), Object.class);
                    if (value != null) {
                        values.put(field.getName(), value);
                    }
                }
            }
        } catch (NullPointerException ignored) {
            // 旧版测试/适配环境可能只提供 key/struct 操作。
        }
        if (values.isEmpty()) {
            try {
                if (GlobalCache.key() != null && !GlobalCache.key().hasKey(key)) {
                    return null;
                }
            } catch (NullPointerException ignored) {
                return null;
            }
            if (GlobalCache.struct() != null) {
                ClientConfig structValue = GlobalCache.struct().get(key, ClientConfig.class);
                if (structValue != null) {
                    return structValue;
                }
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        return ClientConfig.builder()
                .clientId(value(values, ClientConfig.Field.CLIENT_ID))
                .clientSecret(value(values, ClientConfig.Field.CLIENT_SECRET))
                .clientName(value(values, ClientConfig.Field.CLIENT_NAME))
                .enabled(value(values, ClientConfig.Field.ENABLED))
                .scopes(list(values, ClientConfig.Field.SCOPES))
                .redirectUris(list(values, ClientConfig.Field.REDIRECT_URIS))
                .grantTypes(list(values, ClientConfig.Field.GRANT_TYPES))
                .tokenEndpointAuthMethod(value(values, ClientConfig.Field.TOKEN_ENDPOINT_AUTH_METHOD))
                .resource(value(values, ClientConfig.Field.RESOURCE))
                .ipWhitelist(list(values, ClientConfig.Field.IP_WHITELIST))
                .tokenValidDuration(value(values, ClientConfig.Field.TOKEN_VALID_DURATION))
                .refreshTokenValidDuration(value(values, ClientConfig.Field.REFRESH_TOKEN_VALID_DURATION))
                .rateLimit(value(values, ClientConfig.Field.RATE_LIMIT))
                .build();
    }

    @Override
    public void save(ClientConfig client) {
        GlobalCache.struct().set(OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(client.getClientId()),
                client, 365L * 24 * 60 * 60 * 1000);
    }

    @Override
    public void delete(String clientId) {
        GlobalCache.key().removeCache(OAuth2RedisKey.OAUTH2_CLIENT_CONFIG.getKey(clientId));
    }

    @SuppressWarnings("unchecked")
    private <T> T value(Map<String, Object> values, ClientConfig.Field field) {
        return (T) values.get(field.getName());
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Map<String, Object> values, ClientConfig.Field field) {
        return (List<String>) values.get(field.getName());
    }
}
