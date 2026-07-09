/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.redis.manage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 缓存锁管理器
 * <p>
 * 负责管理本地JVM内的锁池（LOCK_POOL），实现本地二级锁加速、可重入锁判断等功能。
 * 通过key维度维护CacheLock对象，支持可重入与不可重入两种模式。
 * <ul>
 *   <li>existLock：判断当前线程是否持有指定key的锁（可重入判断）</li>
 *   <li>getLock：获取指定key的锁对象</li>
 *   <li>addLock/removeLock：注册与释放锁对象</li>
 *   <li>clear：清空所有本地锁</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 16:04:26
 */
public final class CacheLockManager {

    /**
     * 本地锁池，key为业务锁名，value为CacheLock对象。
     * 仅在本JVM实例内生效，实现本地二级锁加速。
     */
    private static final ConcurrentMap<String, CacheLock> LOCK_POOL = new ConcurrentHashMap<>(50);

    private CacheLockManager() {
    }

    /**
     * 判断当前线程是否持有指定key的锁（可重入判断）。
     * <p>仅适用于可重入锁场景。不可重入锁请勿直接依赖此方法。
     *
     * @param key 业务锁key
     * @return true：当前线程持有该锁；false：未持有
     */
    public static boolean existLock(String key) {
        CacheLock lock;
        // 只有当前线程持有该key的锁时才返回true（可重入）
        return LOCK_POOL.containsKey(key)
                && (lock = LOCK_POOL.get(key)) != null
                && lock.getHoldThreadId() == Thread.currentThread().threadId();
    }

    /**
     * 获取指定key的锁对象
     *
     * @param key 业务锁key
     * @return CacheLock对象，可能为null
     */
    public static CacheLock getLock(String key) {
        return LOCK_POOL.get(key);
    }

    /**
     * 注册锁对象到本地锁池
     *
     * @param key 业务锁key
     * @param lock CacheLock对象
     */
    public static void addLock(String key, CacheLock lock) {
        LOCK_POOL.put(key, lock);
    }

    /**
     * 从本地锁池移除锁对象
     *
     * @param key 业务锁key
     * @param lock CacheLock对象
     */
    public static void removeLock(String key, CacheLock lock) {
        LOCK_POOL.remove(key, lock);
    }

    /**
     * 清空所有本地锁（一般用于测试或服务关闭时）
     */
    public static void clear() {
        LOCK_POOL.clear();
    }
}
