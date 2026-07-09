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
