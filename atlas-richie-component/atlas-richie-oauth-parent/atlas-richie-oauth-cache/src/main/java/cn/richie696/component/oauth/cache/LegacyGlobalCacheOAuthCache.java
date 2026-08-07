package cn.richie696.component.oauth.cache;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.cache.redis.manage.CacheLock;

import java.util.Map;

/**
 * 兼容旧 Hash/Struct 混用 Key 的平台缓存适配器。
 * <p>新实现应使用 {@link GlobalCacheOAuthCache}；该类仅保留旧 API 的读取语义。</p>
 */
public final class LegacyGlobalCacheOAuthCache implements OAuthCache {

    @Override
    public <T> T get(String key, Class<T> type) {
        if (Map.class.isAssignableFrom(type)) {
            return type.cast(GlobalCache.field().getAll(key, String.class));
        }
        if (type == String.class || type == Long.class || type == Integer.class || type == Boolean.class) {
            return GlobalCache.value().get(key, type);
        }
        return GlobalCache.struct().get(key, type);
    }

    @Override
    public void put(String key, Object value, long ttlMillis) {
        if (value instanceof String string) {
            GlobalCache.value().set(key, string, ttlMillis);
        } else {
            GlobalCache.struct().set(key, value, ttlMillis);
        }
    }

    @Override
    public boolean putIfAbsent(String key, String value, long ttlMillis) {
        return GlobalCache.value().setIfAbsent(key, value, ttlMillis);
    }

    @Override
    public long increment(String key, long delta, long ttlMillis) {
        // 保持旧版 ValueOps 的调用形态，兼容已有平台实现和旧测试中的 mock。
        return GlobalCache.value().increment(key, delta);
    }

    /** 兼容旧版以 Hash field 保存的值。 */
    public <T> T getField(String key, String field, Class<T> type) {
        return GlobalCache.field().get(key, field, type);
    }

    @Override
    public void remove(String key) {
        GlobalCache.key().removeCache(key);
    }

    @Override
    public boolean exists(String key) {
        return GlobalCache.key().hasKey(key);
    }

    @Override
    public OAuthLock tryLock(String key, long leaseSeconds) {
        CacheLock lock = GlobalCache.lock().optimisticWithRenewal(key, leaseSeconds);
        return new OAuthLock() {
            @Override public boolean acquired() { return lock.isSuccess(); }
            @Override public void close() { lock.close(); }
        };
    }
}
