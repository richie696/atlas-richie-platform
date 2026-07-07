package com.richie.component.web.core.protection;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nonce 重放缓存（README.md §4.8.2 ApiSignature）。
 * <p>
 * 线程安全（{@link ConcurrentHashMap}）。每次 {@link #putIfAbsent} 同时记录插入时间，
 * 过期 nonce 自动失效（{@link #contains} 时 lazy 清理）。
 *
 * <h2>适用</h2>
 * <p>单实例部署；多实例需换 Redis SETEX 或 BloomFilter。
 *
 * @author richie696
 * @since 2026-07
 */
public class NonceCache {

    private final long ttlMillis;
    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();

    public NonceCache(long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be > 0");
        }
        this.ttlMillis = ttlSeconds * 1000L;
    }

    /**
     * 若 nonce 不存在（且未过期）则放入并返回 {@code true}；否则返回 {@code false}。
     */
    public boolean putIfAbsent(String nonce) {
        Objects.requireNonNull(nonce, "nonce must not be null");
        long now = System.currentTimeMillis();
        Long previous = cache.putIfAbsent(nonce, now);
        if (previous == null) {
            return true;
        }
        if (now - previous > ttlMillis) {
            // 已过期，替换
            cache.put(nonce, now);
            return true;
        }
        return false;
    }

    public boolean contains(String nonce) {
        if (nonce == null) {
            return false;
        }
        Long ts = cache.get(nonce);
        if (ts == null) {
            return false;
        }
        if (System.currentTimeMillis() - ts > ttlMillis) {
            cache.remove(nonce, ts);
            return false;
        }
        return true;
    }

    public int size() {
        long now = System.currentTimeMillis();
        int live = 0;
        for (var entry : cache.entrySet()) {
            if (now - entry.getValue() <= ttlMillis) {
                live++;
            } else {
                cache.remove(entry.getKey(), entry.getValue());
            }
        }
        return live;
    }
}