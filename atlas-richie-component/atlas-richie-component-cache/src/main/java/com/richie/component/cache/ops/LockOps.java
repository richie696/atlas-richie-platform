package com.richie.component.cache.ops;

import com.richie.component.cache.redis.manage.CacheBatchLock;
import com.richie.component.cache.redis.manage.CacheLock;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁操作接口。
 * <p>提供乐观锁、悲观锁、自动续期锁及批量锁能力。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
public interface LockOps {

    CacheLock optimistic(String key);

    CacheLock optimistic(String key, long seconds);

    CacheLock optimisticWithRenewal(String key, long seconds);

    CacheLock pessimistic(String key);

    CacheLock pessimistic(String key, long seconds);

    CacheLock pessimisticWithRenewal(String key, long seconds);

    CacheBatchLock batch(Collection<String> keys, long timeout, TimeUnit unit);
}
