package cn.richie696.component.oauth.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Semaphore;

/**
 * {@link OAuthCache} 的单进程内存实现, 基于 ConcurrentHashMap 与 Semaphore 提供 TTL 与互斥锁语义。
 * <p>
 * 处于 OAuth 缓存适配层的 "测试 / 单节点" 一环, 与 {@link GlobalCacheOAuthCache} 通过自动装配条件互斥; 在没有平台缓存基础设施时, 由 {@link cn.richie696.component.oauth.cache.config.OAuthCacheAutoConfiguration} 装配为安全默认值。
 * 解决"单元测试和本地启动时 Redis 不可用, 又要保留 OAuth 缓存契约语义"的问题, 用进程内实现让测试不依赖外部存储, 同时保持对外接口形状一致。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class InMemoryOAuthCache implements OAuthCache {

    private final Map<String, Entry> values = new ConcurrentHashMap<>();
    /** Semaphore 避免同一线程通过 ReentrantLock 重入，保持分布式锁的互斥语义。 */
    private final Map<String, Semaphore> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T get(String key, Class<T> type) {
        Entry entry = values.get(key);
        if (entry == null || entry.expired()) {
            values.remove(key, entry);
            return null;
        }
        return type.cast(entry.value());
    }

    @Override
    public void put(String key, Object value, long ttlMillis) {
        values.put(key, new Entry(value, expiry(ttlMillis)));
    }

    @Override
    public boolean putIfAbsent(String key, String value, long ttlMillis) {
        purge(key);
        return values.putIfAbsent(key, new Entry(value, expiry(ttlMillis))) == null;
    }

    @Override
    public long increment(String key, long delta, long ttlMillis) {
        AtomicLong result = new AtomicLong(delta);
        values.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expired()) {
                return new Entry(result, expiry(ttlMillis));
            }
            if (!(existing.value() instanceof AtomicLong counter)) {
                throw new IllegalStateException("缓存值不是计数器: " + key);
            }
            counter.addAndGet(delta);
            result.set(counter.get());
            return existing;
        });
        return result.get();
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public boolean exists(String key) {
        return get(key, Object.class) != null;
    }

    @Override
    public OAuthLock tryLock(String key, long leaseSeconds) {
        Semaphore lock = locks.computeIfAbsent(key, ignored -> new Semaphore(1));
        boolean acquired = lock.tryAcquire();
        return new OAuthLock() {
            @Override
            public boolean acquired() {
                return acquired;
            }

            @Override
            public void close() {
                if (acquired) {
                    lock.release();
                }
            }
        };
    }

    private void purge(String key) {
        Entry entry = values.get(key);
        if (entry != null && entry.expired()) {
            values.remove(key, entry);
        }
    }

    private long expiry(long ttlMillis) {
        return ttlMillis <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + ttlMillis;
    }

    private record Entry(Object value, long expiresAt) {
        private boolean expired() {
            return expiresAt != Long.MAX_VALUE && expiresAt <= System.currentTimeMillis();
        }
    }
}
