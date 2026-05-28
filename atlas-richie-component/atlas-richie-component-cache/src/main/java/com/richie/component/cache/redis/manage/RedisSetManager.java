package com.richie.component.cache.redis.manage;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.cache.bloom.BloomFilterFacade;
import com.richie.component.cache.config.CacheProperties;
import com.richie.component.cache.enums.L2CachingRegion;
import com.richie.component.cache.function.CacheFunction;
import com.richie.component.cache.function.KeyFunction;
import com.richie.component.cache.function.SetFunction;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Set类型缓存管理器
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15 16:50:35
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisSetManager implements SetFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** 缓存配置 */
    private final CacheProperties cacheProperties;

    /** 布隆过滤器门面 */
    private final BloomFilterFacade bloomFilter;

    /** 分布式锁管理器 */
    private final RedisLockManager lockManager;

    /** 缓存键生成函数 */
    private final KeyFunction keyFunction;

    /** Redis 性能守卫（可选启用） */
    private final RedisPerfGuard redisPerfGuard;

    /**
     * 防缓存击穿：Set
     */
    @Override
    public <T> Set<T> getFromSetWithLock(String key, Class<T> clazz, Supplier<Set<T>> dbLoader, long timeout) {
        // 注意：本地缓存检查已由 GlobalCache 统一处理，此处不再检查
        var config = cacheProperties.getBloomFilter();
        String bloomSetKey = "bloom:%s:set".formatted(key);
        // 1. 查布隆过滤器
        if (!config.isEnable() || bloomFilter.contains(bloomSetKey)) {
            Set<T> value = getFromSet(key, clazz);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            String lockKey = "lock:%s:set".formatted(key);
            try (CacheLock lock = lockManager.optimisticLock(lockKey, DB_LOADER_TIME_OUT)) {
                if (!lock.isSuccess()) {
                    Thread.sleep(50);
                    // 2. 加锁失败，再次查本地缓存，防止其他线程已写入
                    if (keyFunction.enableL2Caching()) {
                        value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, key);
                        if (value != null && !value.isEmpty()) {
                            return value;
                        }
                    }
                    value = getFromSet(key, clazz);
                    // 注意：本地缓存写入由 GlobalCache 统一处理
                    return value;
                }
                // 3. 加锁成功后再查一次本地缓存，防止极端并发
                if (keyFunction.enableL2Caching()) {
                    value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, key);
                    if (value != null && !value.isEmpty()) {
                        return value;
                    }
                }
                value = getFromSet(key, clazz);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
                value = dbLoader.get();
                if (value != null && !value.isEmpty()) {
                    addSet(key, value);
                    long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                    redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
                    if (config.isEnable()) {
                        bloomFilter.add(bloomSetKey);
                    }
                }
                return value;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Set.of();
            }
        } else {
            // 4. 布隆过滤器判定不存在，直接查dbLoader
            Set<T> value = dbLoader.get();
            if (value != null && !value.isEmpty()) {
                addSet(key, value);
                long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
                bloomFilter.add(bloomSetKey);
            }
            return value == null ? Set.of() : value;
        }
    }

    /**
     * 根据资源键获取资源值集合的方法
     *
     * @param key   资源键
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     */
    @Override
    public <T> Set<T> getFromSet(String key, Class<T> clazz) {
        return redisPerfGuard.<Set<T>>execute("RedisSetManager", "getFromSet", RedisOperationCatalog.SET_MEMBERS, () -> {
            var config = cacheProperties.getBloomFilter();
            String bloomSetKey = "bloom:%s:set".formatted(key);
            if (config.isEnable() && !bloomFilter.contains(bloomSetKey)) {
                return Set.of();
            }
            var members = redisTemplate.opsForSet().members(key);
            if (members == null) {
                return Set.of();
            }
            return members.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).filter(Objects::nonNull).collect(Collectors.toSet());
        });
    }

    /**
     * 从Set中弹出一个元素的方法
     *
     * @param key   资源键
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     */
    @Override
    public <T> T popDataFromSet(String key, Class<T> clazz) {
        var member = redisTemplate.opsForSet().pop(key);
        if (member == null) {
            return null;
        }
        return JsonUtils.getInstance().convertObject(member, clazz);
    }

    /**
     * 从Set中弹出指定数量元素的方法
     *
     * @param key   资源键
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值集合
     */
    @Override
    public <T> Set<T> popMembersFromSet(String key, long count, Class<T> clazz) {
        if (count > BATCH_SIZE) {
            throw new IllegalArgumentException("一次性获取的数据量过大，不允许超过20条。");
        }
        var members = redisTemplate.opsForSet().pop(key, count);
        if (members == null) {
            return null;
        }
        return members.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 查询给定的Set集合的差集并返回差集的方法
     *
     * @param keys 待比较的资源键列表
     * @return 返回资源值集合
     */
    @Override
    public <T> Set<T> differenceFromSet(Collection<String> keys, Class<T> clazz) {
        return redisPerfGuard.<Set<T>>execute("RedisSetManager", "differenceFromSet", RedisOperationCatalog.SET_COMBINE, () -> {
            var members = redisTemplate.opsForSet().difference(keys);
            if (members == null) {
                return Set.of();
            }
            return members.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).filter(Objects::nonNull).collect(Collectors.toSet());
        });
    }

    /**
     * 查询给定的Set集合的差分结果并返回的方法
     *
     * @param key       用于比较的主键
     * @param otherKeys 待比较的资源键列表
     * @return 返回资源值集合
     */
    @Override
    public <T> Set<T> differenceFromSet(String key, Collection<String> otherKeys, Class<T> clazz) {
        return redisPerfGuard.<Set<T>>execute("RedisSetManager", "differenceFromSet", RedisOperationCatalog.SET_COMBINE, () -> {
            var members = redisTemplate.opsForSet().difference(key, otherKeys);
            if (members == null) {
                return Set.of();
            }
            return members.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).filter(Objects::nonNull).collect(Collectors.toSet());
        });
    }

    /**
     * 查询给定的Set集合的差集并保存到目标键位置的方法
     *
     * @param compareKeys 待比较的资源键列表
     * @param destKey     目标资源键
     * @return 返回目标资源键内的元素数量
     */
    @Override
    public Long differenceAndStoreFromSet(Collection<String> compareKeys, String destKey) {
        return redisPerfGuard.<Long>execute("RedisSetManager", "differenceAndStoreFromSet", RedisOperationCatalog.SET_COMBINE,
                () -> redisTemplate.opsForSet().differenceAndStore(compareKeys, destKey));
    }

    /**
     * 检查指定的Key是否存在的方法
     *
     * @param key   资源键
     * @param value 资源值
     * @return 返回检查结果（true：存在，false：不存在）
     */
    @Override
    public boolean existsInSet(String key, Object value) {
        Boolean isMember = redisTemplate.opsForSet().isMember(key, value);
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 批量添加缓存到Redis Set的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    @Override
    public void batchAddToSet(Map<String, Set<?>> map) {
        redisPerfGuard.execute("RedisSetManager", "batchAddToSet", RedisOperationCatalog.SET_COMBINE, () -> {
            redisTemplate.executePipelined(new SessionCallback<String>() {
                @SuppressWarnings("unchecked")
                @Override
                public <K, V> String execute(@Nonnull RedisOperations<K, V> redisOperations) throws DataAccessException {
                    for (Map.Entry<String, Set<?>> entry : map.entrySet()) {
                        redisOperations.opsForSet().add((K) entry.getKey(), (V) entry.getValue().toArray());
                    }
                    return null;
                }
            });
            // 布隆过滤器同步
            var config = cacheProperties.getBloomFilter();
            if (config.isEnable()) {
                for (String key : map.keySet()) {
                    String bloomSetKey = "bloom:%s:set".formatted(key);
                    bloomFilter.add(bloomSetKey);
                }
            }
        });
    }

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key 列表名称
     * @param set 列表值
     */
    @Override
    public void addSet(String key, Set<?> set) {
        redisTemplate.opsForSet().add(key, set.toArray());
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            String bloomSetKey = "bloom:%s:set".formatted(key);
            bloomFilter.add(bloomSetKey);
        }
    }

    /**
     * 批量添加元素到指定列表的方法（本方法不会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     */
    @Override
    public void addSetItem(String key, Object... value) {
        redisTemplate.opsForSet().add(key, value);
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            String bloomSetKey = "bloom:%s:set".formatted(key);
            bloomFilter.add(bloomSetKey);
        }
    }

    /**
     * 批量删除Set集合元素的方法
     *
     * @param key    列表名称
     * @param values 要移除的值
     */
    @Override
    public void removeSetItem(String key, Object... values) {
        redisTemplate.opsForSet().remove(key, values);
    }

    /**
     * 获取Set集合元素数量的方法
     *
     * @param key 元素KEY
     * @return 返回执行结果
     */
    @Override
    public Long getSetSize(String key) {
        return redisTemplate.opsForSet().size(key);
    }
}
