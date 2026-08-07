package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.cache.LegacyGlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;

/**
 * @deprecated 使用 {@link CacheBackedTokenStore}，该类只保留旧版本构造 API。
 */
@Deprecated(forRemoval = false)
public class DefaultTokenStore extends CacheBackedTokenStore {

    public DefaultTokenStore() {
        super(new LegacyGlobalCacheOAuthCache());
    }

    public DefaultTokenStore(OAuthCache cache) {
        super(cache);
    }
}
