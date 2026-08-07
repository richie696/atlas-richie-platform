package cn.richie696.component.oauth.cache;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.cache.redis.manage.CacheLock;

/**
 * {@link OAuthCache} 在 atlas-richie-component-cache 上的分布式实现, 把 OAuth 缓存抽象翻译为平台 GlobalCache 的 value / struct / lock 三类操作。
 * <p>
 * 处于 OAuth 缓存适配层的 "默认实现" 一环, 与 {@link LegacyGlobalCacheOAuthCache} 共同构成对平台缓存的两种映射策略; 下游业务模块默认按 Java 类型走 value 或 struct 分支, 并通过乐观续约锁支撑互斥场景。
 * 解决"OAuth 抽象定义后, 缺少一个落地生产可用 Redis 行为的具体实现"的问题, 让部署方在不写一行 Redis 代码的情况下获得 TTL、原子计数、自动续约锁等能力。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
        switch (value) {
            case String string -> GlobalCache.value().set(key, string, ttlMillis);
            case Integer integer -> GlobalCache.value().set(key, integer, ttlMillis);
            case Long number -> GlobalCache.value().set(key, number, ttlMillis);
            case Boolean bool -> GlobalCache.value().set(key, bool, ttlMillis);
            case null, default -> GlobalCache.struct().set(key, value, ttlMillis);
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
