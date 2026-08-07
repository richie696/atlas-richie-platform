package cn.richie696.component.oauth.cache;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.cache.redis.manage.CacheLock;

/** 基于 atlas-richie-component-cache 的分布式实现。 */
public class GlobalCacheOAuthCache implements OAuthCache {

    @Override
    public <T> T get(String key, Class<T> type) {
        if (type == String.class || type == Long.class || type == Integer.class || type == Boolean.class) {
            return GlobalCache.value().get(key, type);
        }
        return GlobalCache.struct().get(key, type);
    }

    @Override
    public void put(String key, Object value, long ttlMillis) {
        if (value instanceof String string) {
            GlobalCache.value().set(key, string, ttlMillis);
        } else if (value instanceof Integer integer) {
            GlobalCache.value().set(key, integer, ttlMillis);
        } else if (value instanceof Long number) {
            GlobalCache.value().set(key, number, ttlMillis);
        } else if (value instanceof Boolean bool) {
            GlobalCache.value().set(key, bool, ttlMillis);
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
        return GlobalCache.value().increment(key, delta, ttlMillis);
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
            @Override
            public boolean acquired() {
                return lock.isSuccess();
            }

            @Override
            public void close() {
                lock.close();
            }
        };
    }
}
