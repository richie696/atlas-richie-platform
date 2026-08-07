package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.cache.OAuthCache;

/** 使用 oauth-cache 的分布式 DPoP jti 防重放实现。 */
public final class OAuthCacheDpopReplayStore implements DpopReplayStore {

    private final OAuthCache cache;

    public OAuthCacheDpopReplayStore(OAuthCache cache) {
        this.cache = cache;
    }

    @Override
    public boolean markIfUnseen(String jti, long ttlMillis) {
        return cache.putIfAbsent("oauth:dpop:jti:" + jti, "1", ttlMillis);
    }
}
