/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
 * 有界分布式栈（LIFO）操作对象，参考 JDK {@link java.util.Deque} API 设计。
 * <p>主动拉（{@link #pop()} / {@link #latest(int)}），满时拒绝压入；有界 List 工具，非消息队列。
 */
public class BoundedStack<T> {

    private static final RedisScript<Long> PUSH_SCRIPT = RedisScript.of(
            "local maxLen = tonumber(redis.call('GET', KEYS[2]));" +
            "if maxLen == nil then return -1 end;" +
            "if redis.call('LLEN', KEYS[1]) >= maxLen then return 0 end;" +
            "redis.call('RPUSH', KEYS[1], ARGV[1]);" +
            "return 1;",
            Long.class);

    private final String key;
    private final String metaKey;
    @Getter
    private volatile long maxLen;
    private final Class<T> clazz;
    private final MultiRedisTemplate<Object> redisTemplate;
    private volatile boolean destroyed;

    public BoundedStack(String key, long maxLen, Class<T> clazz,
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

    public boolean grow() {
        assertAlive();
        Long result = BoundedListRedisScripts.evalLong(
                redisTemplate,
                BoundedListRedisScripts.GROW_MAX_LEN_SCRIPT,
                List.of(metaKey),
                String.valueOf(BoundedListCapacityLimits.BOUNDED_MAX_LEN_CEILING));
        return applyGrowResult(result);
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

    public boolean push(T item) {
        assertMetaPresent();
        Long result = redisTemplate.execute(PUSH_SCRIPT, List.of(key, metaKey), item);
        if (result == null || result == -1L) {
            destroyed = true;
            throw new IllegalStateException(this + " has been destroyed");
        }
        return Long.valueOf(1L).equals(result);
    }

    public T pop() {
        assertMetaPresent();
        var origin = redisTemplate.opsForList().rightPop(key);
        return BoundedListElementConverter.convertOne(origin, key, clazz, "pop");
    }

    public T peek() {
        assertMetaPresent();
        var origin = redisTemplate.opsForList().index(key, -1);
        return BoundedListElementConverter.convertOne(origin, key, clazz, "peek");
    }

    public List<T> latest(int count) {
        assertMetaPresent();
        BoundedListCapacityLimits.validateBatchCount(count);
        var objects = redisTemplate.opsForList().range(key, -(long) count, -1);
        if (CollectionUtils.isEmpty(objects)) {
            return List.of();
        }
        return BoundedListElementConverter.convertAll(objects, key, clazz, "latest");
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
        if (!(o instanceof BoundedStack<?> that)) {
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
        return "BoundedStack{key='%s', maxLen=%d}".formatted(key, (Long) maxLen);
    }
}
