/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.redis.manage;

import com.richie.component.cache.ops.BoundedStackOps;
import com.richie.component.cache.operations.BoundedListCapacityLimits;
import com.richie.component.cache.operations.BoundedListRedisScripts;
import com.richie.component.cache.operations.BoundedStack;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 有界栈（Bounded LIFO Stack）Redis 实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisBoundedStackManager implements BoundedStackOps {

    private static final String KIND = "BoundedStack";

    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    @Override
    public <T> BoundedStack<T> create(String key, long maxLen, Class<T> clazz) {
        BoundedListCapacityLimits.validateMaxLen(maxLen);
        BoundedListRedisSupport.assertListKeyCompatible(redisTemplate, key, KIND);
        if (!BoundedListRedisSupport.setMetaIfAbsent(redisTemplate, BoundedListCapacityLimits.metaKey(key), maxLen)) {
            throw new IllegalStateException("BoundedStack already exists: " + key);
        }
        return new BoundedStack<>(key, maxLen, clazz, redisTemplate);
    }

    @Override
    public <T> BoundedStack<T> get(String key, Class<T> clazz) {
        Object raw = redisTemplate.opsForValue().get(BoundedListCapacityLimits.metaKey(key));
        if (raw == null) {
            return null;
        }
        long maxLen = BoundedListCapacityLimits.parseMetaMaxLen(key, raw);
        return new BoundedStack<>(key, maxLen, clazz, redisTemplate);
    }

    @Override
    public <T> BoundedStack<T> getOrCreate(String key, long maxLen, Class<T> clazz) {
        BoundedListCapacityLimits.validateMaxLen(maxLen);
        BoundedStack<T> existing = get(key, clazz);
        if (existing != null) {
            BoundedListCapacityLimits.assertMaxLenMatches(key, maxLen, existing.getMaxLen());
            return existing;
        }
        BoundedListRedisSupport.assertListKeyCompatible(redisTemplate, key, KIND);
        if (BoundedListRedisSupport.setMetaIfAbsent(redisTemplate, BoundedListCapacityLimits.metaKey(key), maxLen)) {
            return new BoundedStack<>(key, maxLen, clazz, redisTemplate);
        }
        BoundedStack<T> raced = get(key, clazz);
        if (raced == null) {
            throw new IllegalStateException("BoundedStack meta exists but unreadable: " + key);
        }
        BoundedListCapacityLimits.assertMaxLenMatches(key, maxLen, raced.getMaxLen());
        return raced;
    }

    @Override
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BoundedListCapacityLimits.metaKey(key)));
    }

    @Override
    public boolean destroy(String key) {
        Long deleted = redisTemplate.delete(List.of(key, BoundedListCapacityLimits.metaKey(key)));
        return deleted != null && deleted > 0;
    }

    @Override
    public boolean expire(String key, long timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive, got " + timeout);
        }
        if (!exists(key)) {
            return false;
        }
        redisTemplate.execute(
                BoundedListRedisScripts.PEXPIRE_BOTH_SCRIPT,
                List.of(key, BoundedListCapacityLimits.metaKey(key)),
                String.valueOf(timeout));
        return true;
    }

    @Override
    public boolean grow(String key) {
        BoundedStack<Object> stack = get(key, Object.class);
        if (stack == null) {
            throw new IllegalArgumentException("BoundedStack does not exist: " + key);
        }
        return stack.grow();
    }
}
