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
package com.richie.component.cache.operations;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Objects;

/**
 * 有界分布式队列（FIFO）操作对象，参考 JDK {@link java.util.Queue} API 设计。
 * <p>
 * <strong>主动拉</strong>消费：须由业务 {@link #poll()} / {@link #drain(int)} 拉取，无推送、无消费组。
 * 定位为削峰缓冲与轻量异步，非 Redis Stream 消息队列；见 {@code GlobalCache#queue()} 文档。
 * <p>
 * 创建后默认<strong>不可变</strong>容量；仅可通过 {@link #grow()} 在平台约束下单次翻倍放大。
 * 入队超限时自动淘汰队首；写路径 Lua 从 {@code :meta} 读取 maxLen。
 *
 * @param <T> 队列元素类型
 * @author richie696
 * @since 2026-06-04
 */
public class BoundedQueue<T> {

    /** RPUSH + LTRIM（maxLen 从 meta 读取，原子）。 */
    private static final RedisScript<Long> PUSH_SCRIPT = RedisScript.of(
            "local maxLen = tonumber(redis.call('GET', KEYS[2]));" +
            "if maxLen == nil then return -1 end;" +
            "redis.call('RPUSH', KEYS[1], ARGV[1]);" +
            "redis.call('LTRIM', KEYS[1], -maxLen, -1);" +
            "return 1;",
            Long.class);

    private final String key;
    private final String metaKey;
    @Getter
    private volatile long maxLen;
    private final Class<T> clazz;
    private final MultiRedisTemplate<Object> redisTemplate;
    private volatile boolean destroyed;

    public BoundedQueue(String key, long maxLen, Class<T> clazz,
                        MultiRedisTemplate<Object> redisTemplate) {
        this.key = Objects.requireNonNull(key);
        BoundedListCapacityLimits.validateMaxLen(maxLen);
        this.maxLen = maxLen;
        this.metaKey = BoundedListCapacityLimits.metaKey(key);
        this.clazz = Objects.requireNonNull(clazz);
        this.redisTemplate = Objects.requireNonNull(redisTemplate);
    }

    private void assertAlive() {
        if (destroyed) {
            throw new IllegalStateException(this + " has been destroyed");
        }
    }

    private void assertMetaPresent() {
        assertAlive();
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(metaKey))) {
            destroyed = true;
            throw new IllegalStateException(this + " has been destroyed");
        }
    }

    /**
     * 将容量上限翻倍一次；已达封顶时返回 {@code false}。
     */
    public boolean grow() {
        assertAlive();
        Long result = BoundedListRedisScripts.evalLong(
                redisTemplate,
                BoundedListRedisScripts.GROW_MAX_LEN_SCRIPT,
                List.of(metaKey),
                String.valueOf(BoundedListCapacityLimits.BOUNDED_MAX_LEN_CEILING));
        if (applyGrowResult(result)) {
            trimListToMeta();
            return true;
        }
        return false;
    }

    private boolean applyGrowResult(Long result) {
        if (result == null || result == -1L) {
            destroyed = true;
            throw new IllegalStateException(this + " has been destroyed");
        }
        if (result == -2L) {
            throw new IllegalStateException(this + " has invalid meta maxLen");
        }
        if (result == 0L) {
            return false;
        }
        this.maxLen = result;
        return true;
    }

    private void trimListToMeta() {
        Long trim = redisTemplate.execute(
                BoundedListRedisScripts.TRIM_LIST_TO_META_SCRIPT,
                List.of(key, metaKey));
        if (trim == null || trim == -1L) {
            destroyed = true;
            throw new IllegalStateException(this + " has been destroyed");
        }
    }

    public boolean offer(T item) {
        assertMetaPresent();
        Long result = redisTemplate.execute(PUSH_SCRIPT, List.of(key, metaKey), item);
        if (result == null || result == -1L) {
            destroyed = true;
            throw new IllegalStateException(this + " has been destroyed");
        }
        return true;
    }

    public T poll() {
        assertMetaPresent();
        var origin = redisTemplate.opsForList().leftPop(key);
        return BoundedListElementConverter.convertOne(origin, key, clazz, "poll");
    }

    public T peek() {
        assertMetaPresent();
        var origin = redisTemplate.opsForList().index(key, 0);
        return BoundedListElementConverter.convertOne(origin, key, clazz, "peek");
    }

    public T peekTail() {
        assertMetaPresent();
        var origin = redisTemplate.opsForList().index(key, -1);
        return BoundedListElementConverter.convertOne(origin, key, clazz, "peekTail");
    }

    public List<T> drain(int count) {
        assertMetaPresent();
        BoundedListCapacityLimits.validateBatchCount(count);
        var objects = redisTemplate.opsForList().leftPop(key, count);
        if (CollectionUtils.isEmpty(objects)) {
            return List.of();
        }
        return BoundedListElementConverter.convertAll(objects, key, clazz, "drain");
    }

    public long size() {
        assertMetaPresent();
        Long s = redisTemplate.opsForList().size(key);
        return s == null ? 0L : s;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean destroy() {
        if (destroyed) {
            return false;
        }
        Long deleted = redisTemplate.unlink(List.of(key, metaKey));
        boolean success = deleted != null && deleted > 0;
        if (success) {
            destroyed = true;
        }
        return success;
    }

    public boolean expire(long timeout) {
        assertMetaPresent();
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got " + timeout);
        }
        BoundedListRedisScripts.evalLong(
                redisTemplate,
                BoundedListRedisScripts.PEXPIRE_BOTH_SCRIPT,
                List.of(key, metaKey),
                String.valueOf(timeout));
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BoundedQueue<?> that)) {
            return false;
        }
        return key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "BoundedQueue{key='%s', maxLen=%d}".formatted(key, (Long) maxLen);
    }
}
