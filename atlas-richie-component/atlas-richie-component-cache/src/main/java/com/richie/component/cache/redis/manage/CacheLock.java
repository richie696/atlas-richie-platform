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

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.redisson.api.RFencedLock;

import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

/**
 * 缓存分布式锁
 *
 * <p>基于 Redisson FencedLock 的可重入分布式锁，支持可重入计数。
 *
 * <p>主要功能：
 * <ul>
 *   <li>可重入计数：同线程重复加锁计数管理</li>
 *   <li>Redisson 释放：通过 FencedLock 释放</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2022-06-14 21:40:13
 */
@Data
@Accessors(chain = true)
public class CacheLock implements Closeable {

    /** 是否加锁成功 */
    private final boolean success;

    /** 锁对应的 Redis key */
    private String key;

    /** 请求唯一标识，用于原子解锁校验 */
    private final String requestId;

    /** 持有锁的线程 ID */
    private long holdThreadId;

    /** Redisson 栅栏锁 */
    private RFencedLock redissonFencedLock;

    /** 可重入计数 */
    private int nestTransaction = 1;

    /** 是否支持可重入 */
    @Getter
    private final boolean reentrant;

    {
        holdThreadId = Thread.currentThread().threadId();
    }

    /**
     * 构造仅表示加锁结果的锁对象（无 Redis 关联）。
     *
     * @param success   是否加锁成功
     * @param requestId 请求唯一标识
     */
    public CacheLock(boolean success, String requestId) {
        this(success, requestId, true);
    }

    /**
     * 构造仅表示加锁结果的锁对象（无 Redis 关联）。
     *
     * @param success   是否加锁成功
     * @param requestId 请求唯一标识
     * @param reentrant 是否可重入
     */
    public CacheLock(boolean success, String requestId, boolean reentrant) {
        this.success = success;
        this.requestId = requestId;
        this.reentrant = reentrant;
    }

    /**
     * 构造基于 Redisson FencedLock 的锁对象。
     *
     * @param success          是否加锁成功
     * @param key              锁 key
     * @param requestId        请求唯一标识
     * @param redissonFencedLock Redisson 栅栏锁
     * @param reentrant        是否可重入
     */
    public CacheLock(boolean success, String key, String requestId,
              RFencedLock redissonFencedLock, boolean reentrant) {
        this.success = success;
        this.key = key;
        this.requestId = requestId;
        this.redissonFencedLock = redissonFencedLock;
        this.reentrant = reentrant;
    }

    @Override
    public void close() {
        destroy();
    }

    void destroy() {
        if (nestTransaction > 1) {
            subCounter();
            return;
        }
        if (!success) {
            return;
        }
        Optional.ofNullable(redissonFencedLock)
                .ifPresent(Lock::unlock);
        CacheLockManager.removeLock(key, this);
    }

    /**
     * 增加嵌套事务数
     * @return CacheLock
     */
    public CacheLock addCounter() {
        nestTransaction++;
        return this;
    }

    /**
     * 减少嵌套事务数
     */
    public void subCounter() {
        nestTransaction = --nestTransaction < 1 ? 1 : nestTransaction;
    }

    /**
     * 设置嵌套事务数
     * @param nestTransaction 嵌套事务数
     */
    public void setNestTransaction(int nestTransaction) {
        throw new UnsupportedOperationException("This operation is not supported.");
    }

}
