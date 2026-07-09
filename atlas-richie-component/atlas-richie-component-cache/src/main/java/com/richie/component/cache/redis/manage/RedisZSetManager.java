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
package com.richie.component.cache.redis.manage;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.bloom.BloomFilter;
import com.richie.component.cache.config.CacheProperties;
import com.richie.component.cache.function.ZSetFunction;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import tools.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.AtomicDouble;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ZSetOperations;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ZSet类型缓存管理器
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15 16:51:03
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisZSetManager implements ZSetFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** 缓存配置 */
    private final CacheProperties cacheProperties;

    /** 布隆过滤器门面 */
    private final BloomFilter bloomFilter;

    /** Redis 性能守卫（可选启用） */
    private final RedisPerfGuard redisPerfGuard;

    /**
     * 批量添加缓存到Redis Set的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    @Override
    public void batchAddToZSet(Map<String, TreeSet<?>> map) {
        redisPerfGuard.execute("RedisZSetManager", "batchAddToZSet", RedisOperationCatalog.ZSET_BATCH, () -> {
            redisTemplate.executePipelined(new SessionCallback<String>() {
                @SuppressWarnings("unchecked")
                @Override
                public <K, V> String execute(@Nonnull RedisOperations<K, V> redisOperations) throws DataAccessException {
                    for (Map.Entry<String, TreeSet<?>> entry : map.entrySet()) {
                        Set<ZSetOperations.TypedTuple<V>> tuples = getTuples(entry.getValue());
                        redisOperations.opsForZSet().add((K) entry.getKey(), tuples);
                    }
                    return null;
                }
            });
            // 布隆过滤器同步
            if (cacheProperties.getBloomFilter().isEnable()) {
                for (String key : map.keySet()) {
                    String bloomZSetKey = "bloom:%s:zset".formatted(key);
                    bloomFilter.put(bloomZSetKey);
                }
            }
        });
    }

    /**
     * 弹出ZSet队首元素的方法
     *
     * @param key       列表名称
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     */
    @Override
    public <T> T popMinFromZSet(String key, TypeReference<T> reference) {
        ZSetOperations.TypedTuple<Object> objectTypedTuple = redisTemplate.opsForZSet().popMin(key);
        if (objectTypedTuple == null || objectTypedTuple.getValue() == null) {
            return null;
        }
        return JsonUtils.getInstance().convertObject(objectTypedTuple.getValue(), reference);
    }

    /**
     * 弹出ZSet队首元素的方法
     *
     * @param key       列表名称
     * @param count     弹出元素的数量
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     */
    @Override
    public <T> Set<T> popMinFromZSet(String key, long count, TypeReference<T> reference) {
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet().popMin(key, count);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Set.of();
        }
        return typedTuples.stream().map(o -> JsonUtils.getInstance().convertObject(o.getValue(), reference)).collect(Collectors.toSet());
    }

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key      列表名称
     * @param orderSet 列表值
     */
    @Override
    public void addZSet(String key, TreeSet<?> orderSet) {
        Set<ZSetOperations.TypedTuple<Object>> tuples = getTuples(orderSet);
        redisTemplate.opsForZSet().add(key, tuples);
        // 布隆过滤器同步
        if (cacheProperties.getBloomFilter().isEnable()) {
            String bloomZSetKey = "bloom:%s:zset".formatted(key);
            bloomFilter.put(bloomZSetKey);
        }
    }

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     * @param score 列表排序号
     */
    @Override
    public void addZSetItem(String key, Object value, double score) {
        redisTemplate.opsForZSet().add(key, value, score);
        // 布隆过滤器同步
        if (cacheProperties.getBloomFilter().isEnable()) {
            String bloomZSetKey = "bloom:%s:zset".formatted(key);
            bloomFilter.put(bloomZSetKey);
        }
    }

    /**
     * 批量删除Set集合元素的方法
     *
     * @param key    列表名称
     * @param values 要移除的值
     */
    @Override
    public void removeZSetItem(String key, Object... values) {
        redisTemplate.opsForZSet().remove(key, values);
    }

    /**
     * 删除指定范围的元素
     *
     * @param key   列表名称
     * @param start 要移除的元素起始位置
     * @param end   要移除的元素结束位置
     */
    @Override
    public void removeZSetItem(String key, long start, long end) {
        redisTemplate.opsForZSet().removeRange(key, start, end);
    }

    /**
     * 根据元素排序分数删除指定范围元素的方法
     *
     * @param key 缓存KEY
     * @param min 最小分数
     * @param max 最大分数
     */
    @Override
    public void removeZSetItem(String key, double min, double max) {
        redisTemplate.opsForZSet().removeRangeByScore(key, min, max);
    }

    /**
     * 以Score值降序排列获取指定Rank范围元素的方法
     *
     * @param key       资源KEY
     * @param start     起始索引位置
     * @param end       结束索引位置
     * @param reference 目标元素类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回指定范围内的元素列表
     */
    @Override
    public <T> Set<T> reverseRangeWithScores(String key, long start, long end, TypeReference<T> reference) {
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Set.of();
        }
        return typedTuples.stream().map(o -> JsonUtils.getInstance().convertObject(o.getValue(), reference)).collect(Collectors.toSet());
    }

    /**
     * 以Score值降序排列获取指定范围元素的方法
     *
     * @param key      资源KEY
     * @param minScore 最小排序值
     * @param maxScore 最大排序值
     * @param <T>      接收返回值使用的泛型类型
     * @return 返回指定范围内的元素列表
     */
    @Override
    public <T> Set<T> reverseRangeByScore(String key, double minScore, double maxScore, TypeReference<T> reference) {
        var objects = redisTemplate.opsForZSet().reverseRangeByScore(key, minScore, maxScore);
        if (objects == null || objects.isEmpty()) {
            return Set.of();
        }
        return objects.stream().map(o -> JsonUtils.getInstance().convertObject(o, reference)).collect(Collectors.toSet());
    }


    @SuppressWarnings("unchecked")
    private <V> Set<ZSetOperations.TypedTuple<V>> getTuples(TreeSet<?> set) {
        AtomicDouble index = new AtomicDouble(1D);
        return set.stream()
                .map(obj -> (ZSetOperations.TypedTuple<V>) new DefaultTypedTuple<>((V) obj, index.getAndAdd(1D)))
                .collect(Collectors.toSet());
    }

    /**
     * 获取ZSet集合元素数量的方法
     *
     * @param key 元素KEY
     * @return 返回执行结果
     */
    @Override
    public Long getZSetSize(String key) {
        return redisTemplate.opsForZSet().size(key);
    }

    /**
     * 增加ZSet集合元素的分数的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @param delta 增量
     * @return 返回增加后的分数
     */
    @Override
    public Double incrementScore(String key, Object value, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, value, delta);
    }

    /**
     * 获取ZSet集合元素的排名的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回元素的排名
     */
    @Override
    public Long getZSetRank(String key, Object value) {
        return redisTemplate.opsForZSet().rank(key, value);
    }

    /**
     * 获取ZSet集合元素在反转排序后排名的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回元素的排名
     */
    @Override
    public Long getZSetReverseRank(String key, Object value) {
        return redisTemplate.opsForZSet().reverseRank(key, value);
    }

    /**
     * 获取ZSet集合全部元素的的方法
     *
     * @param key       元素KEY
     * @param start     元素起始位置
     * @param end       元素结束位置
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回全部的元素
     */
    @Override
    public <T> Map<Double, T> getZSetData(String key, long start, long end, TypeReference<T> reference) {
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet().rangeWithScores(key, start, end);
        if (CollectionUtils.isEmpty(typedTuples)) {
            return Map.of();
        }
        return typedTuples.stream().collect(Collectors.toMap(ZSetOperations.TypedTuple::getScore, o -> {
            T t = JsonUtils.getInstance().convertObject(o.getValue(), reference);
            Objects.requireNonNull(t);
            return t;
        }));
    }

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的交集并返回的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param clazz     目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返会并集的元素数量
     */
    @Override
    public <T> NavigableSet<T> intersectFromZSet(String key, Collection<String> otherKeys, Class<T> clazz) {
        return redisPerfGuard.<NavigableSet<T>>execute("RedisZSetManager", "intersectFromZSet", RedisOperationCatalog.ZSET_MULTI_KEY, () -> {
            var members = redisTemplate.opsForZSet().intersect(key, otherKeys);
            if (CollectionUtils.isEmpty(members)) {
                return Collections.emptyNavigableSet();
            }
            return members.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).collect(Collectors.toCollection(TreeSet::new));
        });
    }

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的并集并存储到目标 Key 的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param clazz     目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返会并集的元素数量
     */
    @Override
    public <T> NavigableSet<T> unionFromZSet(String key, Collection<String> otherKeys, Class<T> clazz) {
        return redisPerfGuard.<NavigableSet<T>>execute("RedisZSetManager", "unionFromZSet", RedisOperationCatalog.ZSET_MULTI_KEY, () -> {
            var members = redisTemplate.opsForZSet().union(key, otherKeys);
            if (CollectionUtils.isEmpty(members)) {
                return Collections.emptyNavigableSet();
            }
            return members.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).collect(Collectors.toCollection(TreeSet::new));
        });
    }

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的并集并存储到目标 Key 的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param destKey   目标元素KEY
     * @return 返会并集的元素数量
     */
    @Override
    public Long unionAndStoreFromZSet(String key, Collection<String> otherKeys, String destKey) {
        return redisPerfGuard.<Long>execute("RedisZSetManager", "unionAndStoreFromZSet", RedisOperationCatalog.ZSET_MULTI_KEY,
                () -> redisTemplate.opsForZSet().unionAndStore(key, otherKeys, destKey));
    }

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的交集并存储到目标 Key 的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param destKey   目标元素KEY
     * @return 返会交集的元素数量
     */
    @Override
    public Long intersectAndStoreFromZSet(String key, Collection<String> otherKeys, String destKey) {
        return redisPerfGuard.<Long>execute("RedisZSetManager", "intersectAndStoreFromZSet", RedisOperationCatalog.ZSET_MULTI_KEY,
                () -> redisTemplate.opsForZSet().intersectAndStore(key, otherKeys, destKey));
    }

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的差集并存储到目标 Key 的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param destKey   目标元素KEY
     * @return 返会交集的元素数量
     */
    @Override
    public Long differenceAndStoreFromZSet(String key, Collection<String> otherKeys, String destKey) {
        return redisPerfGuard.<Long>execute("RedisZSetManager", "differenceAndStoreFromZSet", RedisOperationCatalog.ZSET_MULTI_KEY,
                () -> redisTemplate.opsForZSet().differenceAndStore(key, otherKeys, destKey));
    }

    /**
     * 获取指定 Key 和用于比较的 OtherKeys 的差集的方法
     *
     * @param key       元素KEY
     * @param otherKeys 其他元素KEY列表
     * @param clazz     目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返会交集的元素数量
     */
    @Override
    public <T> NavigableSet<T> differenceFromZSet(String key, Collection<String> otherKeys, Class<T> clazz) {
        return redisPerfGuard.<NavigableSet<T>>execute("RedisZSetManager", "differenceFromZSet", RedisOperationCatalog.ZSET_MULTI_KEY, () -> {
            var members = redisTemplate.opsForZSet().difference(key, otherKeys);
            if (CollectionUtils.isEmpty(members)) {
                return Collections.emptyNavigableSet();
            }
            return members.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).collect(Collectors.toCollection(TreeSet::new));
        });
    }

}
