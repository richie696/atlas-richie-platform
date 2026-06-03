package com.richie.component.cache.redis.manage;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.redisson.api.RFencedLock;

import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.Lock;

/**
 * 缓存分布式锁
 *
 * <p>封装基于 Redis 的轻量分布式锁（Lua 原子解锁）与 Redisson FencedLock 两种模式，
 * 支持可重入计数与看门狗续期的释放管理。
 *
 * <p>主要功能：
 * <ul>
 *   <li>可重入计数：同线程重复加锁计数管理</li>
 *   <li>原子解锁：Lua 脚本按 requestId 安全释放</li>
 *   <li>Redisson 模式：通过 FencedLock 释放</li>
 *   <li>续期清理：关闭时取消续期任务</li>
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

    /** 看门狗续期任务 */
    private ScheduledFuture<?> renewalFuture;

    /** 是否支持可重入 */
    @Getter
    private final boolean reentrant;

    {
        holdThreadId = Thread.currentThread().threadId();
    }

    /**
     * 构造仅表示加锁结果的锁对象（无 Redis 关联）。
     */
    public CacheLock(boolean success, String requestId) {
        this(success, requestId, true);
    }

    /**
     * 构造仅表示加锁结果的锁对象（无 Redis 关联）。
     */
    public CacheLock(boolean success, String requestId, boolean reentrant) {
        this.success = success;
        this.requestId = requestId;
        this.reentrant = reentrant;
    }

    /**
     * 构造基于 Redisson FencedLock 的锁对象。
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
        // 先取消续期，再解锁，避免续期任务误延长已释放的锁
        if (renewalFuture != null) {
            renewalFuture.cancel(true);
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
