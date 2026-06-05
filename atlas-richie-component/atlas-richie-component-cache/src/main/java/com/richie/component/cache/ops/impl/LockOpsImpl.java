package com.richie.component.cache.ops.impl;

import com.richie.component.cache.function.LockFunction;
import com.richie.component.cache.ops.LockOps;
import com.richie.component.cache.redis.manage.CacheBatchLock;
import com.richie.component.cache.redis.manage.CacheLock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class LockOpsImpl implements LockOps {

    private static final long DEFAULT_SECONDS = 3L;

    private final LockFunction fn;

    @Override
    public CacheLock optimistic(String key) {
        return fn.optimisticLock(key, DEFAULT_SECONDS);
    }

    @Override
    public CacheLock optimistic(String key, long seconds) {
        return fn.optimisticLock(key, seconds);
    }

    @Override
    public CacheLock optimisticWithRenewal(String key, long seconds) {
        return fn.lockWithRenewal(key, seconds, true);
    }

    @Override
    public CacheLock pessimistic(String key) {
        return fn.pessimisticLock(key, DEFAULT_SECONDS);
    }

    @Override
    public CacheLock pessimistic(String key, long seconds) {
        return fn.pessimisticLock(key, seconds);
    }

    @Override
    public CacheLock pessimisticWithRenewal(String key, long seconds) {
        return fn.lockWithRenewal(key, seconds, false);
    }

    @Override
    public CacheBatchLock batch(Collection<String> keys, long timeout, TimeUnit unit) {
        long seconds = unit.toSeconds(timeout);
        Set<CacheLock> locks = new HashSet<>(keys.size());
        for (String key : keys) {
            CacheLock lock = fn.optimisticLock(key, seconds);
            if (lock.isSuccess()) {
                locks.add(lock);
            } else {
                locks.forEach(CacheLock::close);
                return new CacheBatchLock(Set.of());
            }
        }
        return new CacheBatchLock(locks);
    }
}
