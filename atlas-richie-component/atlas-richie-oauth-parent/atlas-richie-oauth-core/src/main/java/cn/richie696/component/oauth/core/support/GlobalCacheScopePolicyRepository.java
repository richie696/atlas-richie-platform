package cn.richie696.component.oauth.core.support;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.spi.ScopePolicyRepository;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/** 兼容现有 Gateway Redis 策略数据的读取适配器。 */
public final class GlobalCacheScopePolicyRepository implements ScopePolicyRepository {

    @Override
    public Set<String> apiCodes() {
        return GlobalCache.collection().get(OAuth2RedisKey.GATEWAY_API_INDEX.getKey(), String.class);
    }

    @Override
    public Map<String, String> apiConfig(String apiCode) {
        Map<String, String> result = GlobalCache.field().getAll(
                OAuth2RedisKey.GATEWAY_API_CONFIG.getKey(apiCode), String.class);
        return result == null ? Collections.emptyMap() : result;
    }

    @Override
    public Set<String> requiredScopes(String apiCode) {
        Set<String> result = GlobalCache.collection().get(
                OAuth2RedisKey.GATEWAY_API_SCOPES.getKey(apiCode), String.class);
        return result == null ? Set.of() : result;
    }
}
