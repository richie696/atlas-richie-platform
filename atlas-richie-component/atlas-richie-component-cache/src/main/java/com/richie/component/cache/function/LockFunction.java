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
package com.richie.component.cache.function;

import com.richie.component.cache.redis.manage.CacheLock;

/**
 * 分布式锁 API：仅暴露乐观锁、悲观锁及带续期获取。
 * <p>
 * 业务侧统一使用 {@link CacheLock} 包装，通过 {@link #optimisticLock(String, long)} / {@link #pessimisticLock(String, long)} 获取，
 * 可选 {@link #lockWithRenewal(String, long, boolean)} 带续期。
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-25 18:28:56
 */
public interface LockFunction extends CacheFunction {

    /**
     * 乐观锁（试一次，失败即返回）
     *
     * @param key  资源键
     * @param time 占锁时间（秒）
     * @return 获取结果
     */
    CacheLock optimisticLock(String key, long time);

    /**
     * 悲观锁（阻塞直到获取或中断）
     *
     * @param key  资源键
     * @param time 占锁时间（秒），-1 表示不设过期（若底层支持）
     * @return 获取结果
     */
    CacheLock pessimisticLock(String key, long time);

    /**
     * 带续期能力的加锁（先按乐观/悲观获取，成功则启动续期任务）
     *
     * @param key       资源键
     * @param seconds   锁过期时间（秒），不能小于 3
     * @param optimistic true 乐观锁获取，false 悲观锁获取
     * @return 获取结果
     */
    CacheLock lockWithRenewal(String key, long seconds, boolean optimistic);
}
