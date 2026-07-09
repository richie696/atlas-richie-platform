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

import com.richie.component.cache.function.LockFunction;
import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁管理器，基于 Redisson FencedLock 实现。
 * <p>
 * 按「本地锁 → 可重入 → Redisson 获取」三层统一处理；锁类型分乐观（试一次）与悲观（阻塞直到获取或中断）。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-25 18:28:56
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisLockManager implements LockFunction {

    private final RedissonClient redissonClient;

    private final AtlasRedisProperties redisProperties;

    /** 本地锁阻塞等待时每次重试前的等待时间（毫秒），避免忙等 */
    private static final long PESSIMISTIC_LOCK_WAIT_MS = 20L;

    // --------------- LockFunction 公开接口 ---------------

    @Override
    public CacheLock optimisticLock(String key, long time) {
        return acquireOptimistic(key, time, UUID.randomUUID().toString(), true);
    }

    @Override
    public CacheLock pessimisticLock(String key, long time) {
        return acquirePessimistic(key, time, UUID.randomUUID().toString(), true);
    }

    @Override
    public CacheLock lockWithRenewal(String key, long seconds, boolean optimistic) {
        if (seconds < 3) {
            throw new IllegalArgumentException("锁续期时间不能小于3秒");
        }
        return optimistic ? optimisticLock(key, seconds) : pessimisticLock(key, seconds);
    }

    // --------------- 本地锁阶段 ---------------

    /**
     * 本地锁「试一次」：若启用本地锁且已被占且不可放行则返回 false，否则返回 true。
     */
    private boolean tryLocalLock(String key, boolean reentrant) {
        if (!redisProperties.isEnableLocalLock()) {
            return true;
        }
        CacheLock existing = CacheLockManager.getLock(key);
        return canPassLocal(existing, reentrant);
    }

    /**
     * 本地锁「阻塞」：循环检查并 sleep，直到可放行或 InterruptedException；中断时返回 false。
     */
    private boolean waitLocalLock(String key, boolean reentrant) {
        if (!redisProperties.isEnableLocalLock()) {
            return true;
        }
        while (true) {
            CacheLock existing = CacheLockManager.getLock(key);
            if (canPassLocal(existing, reentrant)) {
                return true;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(PESSIMISTIC_LOCK_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static boolean canPassLocal(CacheLock existing, boolean reentrant) {
        return existing == null
                || existing.isReentrant()
                || (reentrant && existing.getHoldThreadId() == Thread.currentThread().threadId());
    }

    /**
     * 可重入阶段：若当前线程已持锁则 addCounter 并返回该锁，否则返回 null。
     */
    private CacheLock tryReentrant(String key) {
        if (!CacheLockManager.existLock(key)) {
            return null;
        }
        CacheLock lock = CacheLockManager.getLock(key);
        if (lock != null) {
            return lock.addCounter();
        }
        return null;
    }

    // --------------- 统一获取：乐观 ---------------

    /**
     * 乐观获取：试一次即返回。流程：本地锁（试一次）→ 可重入 → Redisson 试一次。
     */
    private CacheLock acquireOptimistic(String key, long time, String requestId, boolean reentrant) {
        if (!tryLocalLock(key, reentrant)) {
            return new CacheLock(false, requestId, reentrant);
        }
        CacheLock reentrantLock = tryReentrant(key);
        if (reentrantLock != null) {
            return reentrantLock;
        }
        return acquireOptimisticRedisson(key, time, requestId, reentrant);
    }

    private CacheLock acquireOptimisticRedisson(String key, long time, String requestId, boolean reentrant) {
        String lockKey = getLockKeyString(key);
        var rLock = redissonClient.getFencedLock(lockKey);
        try {
            if (!rLock.tryLock(0, time, TimeUnit.SECONDS)) {
                return new CacheLock(false, requestId, reentrant);
            }
            CacheLock cacheLock = new CacheLock(true, key, requestId, rLock, reentrant);
            CacheLockManager.addLock(key, cacheLock);
            return cacheLock;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("获取 Redisson 锁失败。", e);
            return new CacheLock(false, requestId, reentrant);
        }
    }

    // --------------- 统一获取：悲观 ---------------

    /**
     * 悲观获取：阻塞直到获取或中断。流程：本地锁（阻塞等待）→ 可重入 → Redisson。
     */
    private CacheLock acquirePessimistic(String key, long time, String requestId, boolean reentrant) {
        if (!waitLocalLock(key, reentrant)) {
            return new CacheLock(false, requestId, reentrant);
        }
        CacheLock reentrantLock = tryReentrant(key);
        if (reentrantLock != null) {
            return reentrantLock;
        }
        return acquirePessimisticRedisson(key, time, requestId, reentrant);
    }

    private CacheLock acquirePessimisticRedisson(String key, long time, String requestId, boolean reentrant) {
        String lockKey = getLockKeyString(key);
        var rLock = redissonClient.getFencedLock(lockKey);
        if (time >= 0) {
            rLock.lock(time, TimeUnit.SECONDS);
        } else {
            rLock.lock();
        }
        CacheLock cacheLock = new CacheLock(true, key, requestId, rLock, reentrant);
        CacheLockManager.addLock(key, cacheLock);
        return cacheLock;
    }

    // --------------- 工具方法 ---------------

    /** 业务 key 转成 Redis 上锁用的 key（与数据 key 隔离，避免冲突） */
    private String getLockKeyString(String key) {
        return LOCK_KEY + key;
    }
}
