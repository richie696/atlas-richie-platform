package com.richie.component.cache.redis.manage;

import com.richie.component.cache.function.LockFunction;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁管理器，封装基于 Redisson 的分布式锁能力。
 * <p>
 * 按「本地锁 → 可重入 → Redisson 获取」三层统一处理；锁类型分乐观（试一次）与悲观（阻塞直到获取或中断），
 * 后端仅支持 Redisson 实现（FencedLock + Lua 原子解锁），可重入由 Redisson 内部维护。
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

    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    private final RedissonClient redissonClient;

    private final AtlasRedisProperties redisProperties;

    private final RedisPerfGuard redisPerfGuard;

    /** 悲观锁阻塞获取时每次重试前的等待时间（毫秒），避免忙等 */
    private static final long PESSIMISTIC_LOCK_WAIT_MS = 20L;

    private static final ScheduledExecutorService renewalPool =
            Executors.newScheduledThreadPool(
                    Runtime.getRuntime().availableProcessors(),
                    Thread.ofVirtual().name("RedisLock-Renewal-", 0).factory()
            );

    // --------------- LockFunction 公开接口 ---------------

    @Override
    public CacheLock optimisticLock(String key, long time) {
        return redisPerfGuard.<CacheLock>execute("RedisLockManager", "optimisticLock", RedisOperationCatalog.LOCK_TRY,
                () -> acquireOptimistic(key, time, UUID.randomUUID().toString(), true));
    }

    @Override
    public CacheLock pessimisticLock(String key, long time) {
        return redisPerfGuard.<CacheLock>execute("RedisLockManager", "pessimisticLock", RedisOperationCatalog.LOCK_TRY,
                () -> acquirePessimistic(key, time, UUID.randomUUID().toString(), true));
    }

    @Override
    public CacheLock lockWithRenewal(String key, long seconds, boolean optimistic) {
        return redisPerfGuard.<CacheLock>execute("RedisLockManager", "lockWithRenewal", RedisOperationCatalog.LOCK_TRY, () -> {
            if (seconds < 3) {
                throw new IllegalArgumentException("锁续期时间不能小于3秒");
            }
            CacheLock lock = optimistic
                    ? acquireOptimistic(key, seconds, UUID.randomUUID().toString(), true)
                    : acquirePessimistic(key, seconds, UUID.randomUUID().toString(), true);
            if (lock.isSuccess()) {
                final int INITIAL_DELAY = 1;
                long renewalInterval = seconds - INITIAL_DELAY;
                ScheduledFuture<?> future = renewalPool.scheduleAtFixedRate(() -> {
                    try {
                        if (CacheLockManager.getLock(lock.getKey()) == lock) {
                            redisTemplate.expire(
                                    getLockKeyString(lock.getKey()),
                                    seconds,
                                    TimeUnit.SECONDS
                            );
                        }
                    } catch (Exception e) {
                        log.warn("锁续期失败：{}", lock.getKey(), e);
                    }
                }, INITIAL_DELAY, renewalInterval, TimeUnit.SECONDS);
                lock.setRenewalFuture(future);
            }
            return lock;
        });
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
