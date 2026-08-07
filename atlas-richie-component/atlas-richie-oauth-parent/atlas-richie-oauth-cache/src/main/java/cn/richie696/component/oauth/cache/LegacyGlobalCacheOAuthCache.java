package cn.richie696.component.oauth.cache;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.cache.redis.manage.CacheLock;

import java.util.Map;

/**
 * 兼容旧 Hash / Struct 混用 Key 的平台缓存适配器, 仅承担过渡期对旧 API 读取语义的对齐职责。
 * <p>
 * 处于 OAuth 缓存适配层的 "遗留兼容" 一环, 与 {@link GlobalCacheOAuthCache} 并存但优先级靠后; 主要承接那些已落地的 OAuth 子模块在切到新适配器前必须保留的旧 Key 形态 (包括按 hash field 取值的旧用法)。
 * 解决"OAuth 抽象升级时, 旧 Key 形态的存量缓存与旧测试 mock 仍期望被读到"的问题, 通过并行实现避免一次性迁移风险, 待旧 Key 自然淘汰后即可移除。
 *
 * @author richie696
 * @since 2026-08-07
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
