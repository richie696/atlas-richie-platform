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
package com.richie.component.cache.redis.bean;

import com.richie.contract.exception.PlatformRuntimeException;
import com.richie.component.cache.commons.CacheKeyUtils;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.SortParameters;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.query.SortQuery;
import org.springframework.data.redis.core.query.SortQueryBuilder;
import jakarta.annotation.Nonnull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * 多数据源 RedisTemplate
 *
 * <p>在单应用内路由到不同的 RedisTemplate 实例，支持按 key 前缀选择从库/分库。
 *
 * <p>主要功能：
 * <ul>
 *   <li>通过约定的 key 规则选择目标模板（形如 prefix@@key）</li>
 *   <li>对常用操作进行代理转发，自动处理真实 key（去装饰前缀）</li>
 *   <li>兼容原生 {@code RedisTemplate} 的各类操作</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2023-07-05 17:47:47
 * @param <V> value类型
 */
@Getter
public class MultiRedisTemplate<V> extends RedisTemplate<String, V> {

    /** 从库/分库前缀到 RedisTemplate 的映射（prefix -> template） */
    private final Map<String, MultiRedisTemplate<V>> slaveTemplateMap = new ConcurrentHashMap<>();

    /**
     * 设置子节点template
     * @param slaveTemplateMap 子节点template
     */
    public void setSlaveTemplateMap(Map<String, MultiRedisTemplate<V>> slaveTemplateMap) {
        this.slaveTemplateMap.putAll(slaveTemplateMap);
    }

    /**
     * 根据key获取对应的template
     * @param key must not be {@literal null}.
     * @return 返回删除结果
     */
    @Override
    public Boolean delete(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.delete(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.delete(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public Long delete(@Nonnull Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return 0L;
        }
        List<String> keyList = keys.stream().toList();
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(keyList.getFirst());
        if (targetTemplate == null) {
            return super.delete(CacheKeyUtils.getRealKeys(keyList));
        }
        return targetTemplate.delete(CacheKeyUtils.getRealKeys(keyList));
    }

    @Override
    public Boolean unlink(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.unlink(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.unlink(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public Long unlink(@Nonnull Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return 0L;
        }
        List<String> keyList = keys.stream().toList();
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(keyList.getFirst());
        if (targetTemplate == null) {
            return super.unlink(CacheKeyUtils.getRealKeys(keyList));
        }
        return targetTemplate.unlink(CacheKeyUtils.getRealKeys(keyList));
    }

    @Override
    public Boolean hasKey(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.hasKey(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.hasKey(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public Long countExistingKeys(Collection<String> keys) {
        List<String> keyList = keys.stream().toList();
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(keyList.getFirst());
        if (targetTemplate == null) {
            return super.countExistingKeys(CacheKeyUtils.getRealKeys(keyList));
        }
        return targetTemplate.countExistingKeys(CacheKeyUtils.getRealKeys(keyList));
    }

    @Override
    public Boolean expire(@Nonnull String key, long timeout, @Nonnull TimeUnit unit) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.expire(CacheKeyUtils.getRealKey(key), timeout, unit);
        }
        return targetTemplate.expire(CacheKeyUtils.getRealKey(key), timeout, unit);
    }

    @Override
    public Boolean expireAt(@Nonnull String key, @Nonnull Date date) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.expireAt(CacheKeyUtils.getRealKey(key), date);
        }
        return targetTemplate.expireAt(CacheKeyUtils.getRealKey(key), date);
    }

    @Override
    public Long getExpire(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.getExpire(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.getExpire(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public Long getExpire(@Nonnull String key, @Nonnull TimeUnit timeUnit) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.getExpire(CacheKeyUtils.getRealKey(key), timeUnit);
        }
        return targetTemplate.getExpire(CacheKeyUtils.getRealKey(key), timeUnit);
    }

    @Override
    public Set<String> keys(@Nonnull String pattern) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(pattern);
        if (targetTemplate == null) {
            return super.keys(CacheKeyUtils.getRealKey(pattern));
        }
        return targetTemplate.keys(CacheKeyUtils.getRealKey(pattern));
    }

    @Override
    public Boolean persist(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.persist(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.persist(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public Boolean move(@Nonnull String key, int dbIndex) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.move(CacheKeyUtils.getRealKey(key), dbIndex);
        }
        return targetTemplate.move(CacheKeyUtils.getRealKey(key), dbIndex);
    }

    @Override
    public void rename(@Nonnull String oldKey, @Nonnull String newKey) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(oldKey);
        if (targetTemplate == null) {
            super.rename(CacheKeyUtils.getRealKey(oldKey), newKey);
            return;
        }
        targetTemplate.rename(CacheKeyUtils.getRealKey(oldKey), newKey);
    }

    @Override
    public Boolean renameIfAbsent(@Nonnull String oldKey, @Nonnull String newKey) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(oldKey);
        if (targetTemplate == null) {
            return super.renameIfAbsent(CacheKeyUtils.getRealKey(oldKey), newKey);
        }
        return targetTemplate.renameIfAbsent(CacheKeyUtils.getRealKey(oldKey), newKey);
    }

    @Override
    public DataType type(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.type(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.type(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public byte[] dump(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.dump(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.dump(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public void restore(@Nonnull String key, @Nonnull byte[] value, long timeToLive, @Nonnull TimeUnit unit, boolean replace) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            super.restore(CacheKeyUtils.getRealKey(key), value, timeToLive, unit, replace);
            return;
        }
        targetTemplate.restore(CacheKeyUtils.getRealKey(key), value, timeToLive, unit, replace);
    }

    @Override
    public void watch(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            super.watch(CacheKeyUtils.getRealKey(key));
            return;
        }
        targetTemplate.watch(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public void watch(Collection<String> keys) {
        List<String> keyList = keys.stream().toList();
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(keyList.get(0));
        if (targetTemplate == null) {
            super.watch(CacheKeyUtils.getRealKeys(keyList));
            return;
        }
        targetTemplate.watch(CacheKeyUtils.getRealKeys(keyList));
    }

    @Override
    public Long sort(@Nonnull SortQuery<String> query, @Nonnull String storeKey) {
        String key = query.getKey();
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        key = CacheKeyUtils.getRealKey(key);
        SortQueryBuilder<String> sort = SortQueryBuilder.sort(key);
        if (query.getBy() != null) {
            sort.by(query.getBy());
        }
        SortParameters.Range limit = query.getLimit();
        if (limit != null) {
            sort.limit(limit.getStart(), limit.getCount());
        }
        Boolean alphabetic = query.isAlphabetic();
        if (Objects.nonNull(alphabetic)) {
            sort.alphabetical(alphabetic);
        }
        SortParameters.Order order = query.getOrder();
        if (Objects.nonNull(order)) {
            sort.order(order);
        }
        if (targetTemplate == null) {
            return super.sort(sort.build(), storeKey);
        }
        return targetTemplate.sort(sort.build(), storeKey);
    }

    @Override
    public BoundGeoOperations<String, V> boundGeoOps(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.boundGeoOps(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.boundGeoOps(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public <HK, HV> BoundHashOperations<String, HK, HV> boundHashOps(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.boundHashOps(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.boundHashOps(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public BoundListOperations<String, V> boundListOps(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.boundListOps(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.boundListOps(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public BoundSetOperations<String, V> boundSetOps(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.boundSetOps(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.boundSetOps(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public <HK, HV> BoundStreamOperations<String, HK, HV> boundStreamOps(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.boundStreamOps(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.boundStreamOps(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public BoundValueOperations<String, V> boundValueOps(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.boundValueOps(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.boundValueOps(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public BoundZSetOperations<String, V> boundZSetOps(@Nonnull String key) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.boundZSetOps(CacheKeyUtils.getRealKey(key));
        }
        return targetTemplate.boundZSetOps(CacheKeyUtils.getRealKey(key));
    }

    @Override
    public Boolean expire(@Nonnull String key, @Nonnull Duration timeout) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.expire(CacheKeyUtils.getRealKey(key), timeout);
        }
        return targetTemplate.expire(CacheKeyUtils.getRealKey(key), timeout);
    }

    @Override
    public Boolean expireAt(@Nonnull String key, @Nonnull Instant expireAt) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            return super.expireAt(CacheKeyUtils.getRealKey(key), expireAt);
        }
        return targetTemplate.expireAt(CacheKeyUtils.getRealKey(key), expireAt);
    }

    @Override
    public void restore(@Nonnull String key, @Nonnull byte[] value, long timeToLive, @Nonnull TimeUnit unit) {
        RedisTemplate<String, V> targetTemplate = getTargetTemplate(key);
        if (targetTemplate == null) {
            super.restore(CacheKeyUtils.getRealKey(key), value, timeToLive, unit);
            return;
        }
        targetTemplate.restore(CacheKeyUtils.getRealKey(key), value, timeToLive, unit);
    }

    private RedisTemplate<String, V> getTargetTemplate(@Nonnull String key) {
        if (key.contains("@@")) {
            String[] keys = key.split("@@");
            RedisTemplate<String, V> value = slaveTemplateMap.get(keys[0]);
            if (Objects.isNull(value)) {
                throw new PlatformRuntimeException("The key '%s' is not found in slave redis template".formatted(key));
            }
            return value;
        }
        return null;
    }
}
