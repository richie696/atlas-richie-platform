package com.richie.component.cache.redis.manage;

import java.io.Closeable;
import java.util.Set;

/**
 * 缓存批量锁
 *
 * <p>用于聚合多个 {@code CacheLock}，以便统一判定是否加锁成功并在关闭时批量释放。
 *
 * <p>主要功能：
 * <ul>
 *   <li>封装多把锁的生命周期，统一释放</li>
 *   <li>提供便捷方法判断加锁结果</li>
 * </ul>
 *
 * @author richie696
 * @version 5.0.0
 * @since 2023-11-01 14:05:20
 */
public class CacheBatchLock implements Closeable {

    /** 本批锁包含的 CacheLock 集合 */
    private final Set<CacheLock> cacheLocks;

    /**
     * 构造批量锁。
     *
     * @param cacheLocks 多把 CacheLock
     */
    public CacheBatchLock(Set<CacheLock> cacheLocks) {
        this.cacheLocks = cacheLocks;
    }

    /**
     * 检查是否上锁成功的方法
     *
     * @return 返回检查结果（true：上锁成功，false：上锁失败）
     */
    public boolean isSuccess() {
        return cacheLocks != null && !cacheLocks.isEmpty();
    }

    /**
     * 释放锁
     */
    @Override
    public void close() {
        cacheLocks.forEach(CacheLock::close);
    }
}
