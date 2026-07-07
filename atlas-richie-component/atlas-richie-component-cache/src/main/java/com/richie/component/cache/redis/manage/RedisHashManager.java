package com.richie.component.cache.redis.manage;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.bloom.BloomFilter;
import com.richie.component.cache.commons.CacheKeyUtils;
import com.richie.component.cache.config.CacheProperties;
import com.richie.component.cache.enums.L2CachingRegion;
import com.richie.component.cache.function.CacheFunction;
import com.richie.component.cache.ops.CacheInfrastructure;
import com.richie.component.cache.function.HashFunction;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Hash类型缓存管理器
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-26 14:27:47
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisHashManager implements HashFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** 缓存配置 */
    private final CacheProperties cacheProperties;

    /** 布隆过滤器门面 */
    private final BloomFilter bloomFilter;

    /** 分布式锁管理器 */
    private final RedisLockManager lockManager;

    /** 缓存框架内部基础设施（L2 开关、key 类型等） */
    private final CacheInfrastructure infra;

    /** Redis 性能守卫（可选启用） */
    private final RedisPerfGuard redisPerfGuard;

    /**
     * 防缓存击穿：Hash对象（支持复杂类型）
     *
     * @param key      缓存key
     * @param reference 缓存对象类型
     * @param dbLoader 回源加载器
     * @param timeout  超时时间
     * @return 返回缓存对象
     */
    @Override
    public <T> T getObjectFromHashWithLock(String key, TypeReference<T> reference, Supplier<T> dbLoader, long timeout) {
        // 注意：本地缓存检查已由 GlobalCache 统一处理，此处不再检查
        // 1. 查布隆过滤器，判定是否有必要继续查Redis
        var config = cacheProperties.getBloomFilter();
        // 如果布隆过滤器未启用 或者 布隆过滤器已启用，且判定"可能存在" 则进入
        if (!config.isEnable() || bloomFilter.mightContain(key)) {
            // 2. 查Redis，防止本地缓存失效但Redis中已有数据
            T value = getObjectFromHash(key, reference);
            if (value != null) {
                return value;
            }
            // 3. Redis未命中，尝试加分布式锁，防止缓存击穿
            String lockKey = "lock:%s".formatted(key);
            try (CacheLock lock = lockManager.optimisticLock(lockKey, DB_LOADER_TIME_OUT)) {
                if (!lock.isSuccess()) {
                    Thread.sleep(50);
                    // 4. 加锁失败，说明有其他线程正在回源DB，等待一会后再次查本地缓存，防止第一个线程已写入缓存但本线程还没感知到
                    if (infra.enableL2Caching()) {
                        value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, key);
                        if (value != null) {
                            return value;
                        }
                    }
                    // 5. 再查Redis，防止第一个线程已写入Redis但本地缓存还没同步
                    value = getObjectFromHash(key, reference);
                    // 注意：本地缓存写入由 GlobalCache 统一处理
                    return value;
                }
                // 6. 加锁成功后再次查本地缓存，防止极端并发下第一个线程已写入缓存
                if (infra.enableL2Caching()) {
                    value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, key);
                    if (value != null) {
                        return value;
                    }
                }
                // 7. 再查Redis，防止第一个线程已写入Redis但本地缓存还没同步
                value = getObjectFromHash(key, reference);
                if (value != null) {
                    return value;
                }
                // 8. 还没有命中，说明确实需要回源DB
                value = dbLoader.get();
                if (value != null) {
                    addObject(key, value, timeout);
                    if (config.isEnable()) {
                        bloomFilter.put(key);
                    }
                }
                return value;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } else {
            // 9. 布隆过滤器判定不存在，直接回源DB并写入缓存
            T value = dbLoader.get();
            if (value != null) {
                addObject(key, value, timeout);
                bloomFilter.put(key);
            }
            return value;
        }
    }

    /**
     * 防缓存击穿：Hash单项
     *
     * @param key     资源键
     * @param hashKey HASH资源键
     * @param clazz   目标缓存类型
     * @param dbLoader 回源加载器
     * @param timeout 超时时间
     * @return 返回资源值
     */
    @Override
    public <T> T getFromHashWithLock(String key, String hashKey, Class<T> clazz, Supplier<T> dbLoader, long timeout) {
        // 注意：本地缓存检查已由 GlobalCache 统一处理，此处不再检查
        // Hash 子项使用特殊的缓存键格式：key:hashKey
        var config = cacheProperties.getBloomFilter();
        String bloomHashKey = "bloom:%s:%s".formatted(key, hashKey);
        // 1. 查布隆过滤器，判定是否有必要继续查Redis
        if (!config.isEnable() || bloomFilter.mightContain(bloomHashKey)) {
            // 2. 查Redis，防止本地缓存失效但Redis中已有数据
            T value = getFromHash(key, hashKey, clazz);
            if (value != null) {
                return value;
            }
            // 3. Redis未命中，尝试加分布式锁，防止缓存击穿
            String lockKey = "lock:%s:%s".formatted(key, hashKey);
            try (CacheLock lock = lockManager.optimisticLock(lockKey, DB_LOADER_TIME_OUT)) {
                if (!lock.isSuccess()) {
                    Thread.sleep(50);
                    // 4. 加锁失败，再次查本地缓存，防止其他线程已写入
                    // 注意：使用 key:hashKey 格式与 GlobalCache 保持一致
                    if (infra.enableL2Caching()) {
                        String cacheKey = key + ":" + hashKey;
                        value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, cacheKey);
                        if (value != null) {
                            return value;
                        }
                    }
                    // 5. 再查Redis，防止第一个线程已写入Redis但本地缓存还没同步
                    value = getFromHash(key, hashKey, clazz);
                    // 注意：本地缓存写入由 GlobalCache 统一处理
                    return value;
                }
                // 6. 加锁成功后再次查本地缓存，防止极端并发
                if (infra.enableL2Caching()) {
                    String cacheKey = key + ":" + hashKey;
                    value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, cacheKey);
                    if (value != null) {
                        return value;
                    }
                }
                // 7. 再查Redis，防止第一个线程已写入Redis但本地缓存还没同步
                value = getFromHash(key, hashKey, clazz);
                if (value != null) {
                    return value;
                }
                // 8. 还没有命中，说明确实需要回源DB
                value = dbLoader.get();
                if (value != null) {
                    addHash(key, hashKey, value);
                    long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                    redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
                    if (config.isEnable()) {
                        bloomFilter.put(bloomHashKey);
                    }
                }
                return value;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } else {
            // 9. 布隆过滤器判定不存在，直接回源DB并写入缓存
            T value = dbLoader.get();
            if (value != null) {
                addHash(key, hashKey, value);
                long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
                bloomFilter.put(bloomHashKey);
            }
            return value;
        }
    }

    /**
     * 防缓存击穿：Hash单项（支持复杂类型）
     *
     * @param key     资源键
     * @param hashKey HASH资源键
     * @param reference 目标缓存类型
     * @param dbLoader 回源加载器
     * @param timeout 超时时间
     * @return 返回资源值
     */
    @Override
    public <T> T getFromHashWithLock(String key, String hashKey, TypeReference<T> reference, Supplier<T> dbLoader, long timeout) {
        // 注意：本地缓存检查已由 GlobalCache 统一处理，此处不再检查
        // Hash 子项使用特殊的缓存键格式：key:hashKey
        var config = cacheProperties.getBloomFilter();
        String bloomHashKey = "bloom:%s:%s".formatted(key, hashKey);
        // 1. 查布隆过滤器，判定是否有必要继续查Redis
        if (!config.isEnable() || bloomFilter.mightContain(bloomHashKey)) {
            // 2. 查Redis，防止本地缓存失效但Redis中已有数据
            T value = getFromHash(key, hashKey, reference);
            if (value != null) {
                return value;
            }
            // 3. Redis未命中，尝试加分布式锁，防止缓存击穿
            String lockKey = "lock:%s:%s".formatted(key, hashKey);
            try (CacheLock lock = lockManager.optimisticLock(lockKey, DB_LOADER_TIME_OUT)) {
                if (!lock.isSuccess()) {
                    Thread.sleep(50);
                    // 4. 加锁失败，再次查本地缓存，防止其他线程已写入
                    // 注意：使用 key:hashKey 格式与 GlobalCache 保持一致
                    if (infra.enableL2Caching()) {
                        String cacheKey = key + ":" + hashKey;
                        value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, cacheKey);
                        if (value != null) {
                            return value;
                        }
                    }
                    // 5. 再查Redis，防止第一个线程已写入Redis但本地缓存还没同步
                    value = getFromHash(key, hashKey, reference);
                    // 注意：本地缓存写入由 GlobalCache 统一处理
                    return value;
                }
                // 6. 加锁成功后再次查本地缓存，防止极端并发
                if (infra.enableL2Caching()) {
                    String cacheKey = key + ":" + hashKey;
                    value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, cacheKey);
                    if (value != null) {
                        return value;
                    }
                }
                // 7. 再查Redis，防止第一个线程已写入Redis但本地缓存还没同步
                value = getFromHash(key, hashKey, reference);
                if (value != null) {
                    return value;
                }
                // 8. 还没有命中，说明确实需要回源DB
                value = dbLoader.get();
                if (value != null) {
                    addHash(key, hashKey, value);
                    long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                    redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
                    if (config.isEnable()) {
                        bloomFilter.put(bloomHashKey);
                    }
                }
                return value;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } else {
            // 9. 布隆过滤器判定不存在，直接回源DB并写入缓存
            T value = dbLoader.get();
            if (value != null) {
                addHash(key, hashKey, value);
                long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
                bloomFilter.put(bloomHashKey);
            }
            return value;
        }
    }

    /**
     * 防缓存击穿：Hash对象
     *
     * @param key      缓存key
     * @param clazz    缓存对象类型
     * @param dbLoader 回源加载器
     * @param timeout  超时时间
     * @return 返回缓存对象
     */
    @Override
    public <T> T getObjectFromHashWithLock(String key, Class<T> clazz, Supplier<T> dbLoader, long timeout) {
        // 注意：本地缓存检查已由 GlobalCache 统一处理，此处不再检查
        // 1. 查布隆过滤器，判定是否有必要继续查Redis
        var config = cacheProperties.getBloomFilter();
        if (!config.isEnable() || bloomFilter.mightContain(key)) {
            // 2. 查Redis，防止本地缓存失效但Redis中已有数据
            T value = getObjectFromHash(key, clazz);
            if (value != null) {
                return value;
            }
            // 3. Redis未命中，尝试加分布式锁，防止缓存击穿
            String lockKey = "lock:%s".formatted(key);
            try (CacheLock lock = lockManager.optimisticLock(lockKey, DB_LOADER_TIME_OUT)) {
                if (!lock.isSuccess()) {
                    Thread.sleep(50);
                    // 4. 加锁失败，说明有其他线程正在回源DB，等待一会后再次查本地缓存，防止第一个线程已写入缓存但本线程还没感知到
                    if (infra.enableL2Caching()) {
                        value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, key);
                        if (value != null) {
                            return value;
                        }
                    }
                    // 5. 再查Redis，防止第一个线程已写入Redis但本地缓存还没同步
                    value = getObjectFromHash(key, clazz);
                    // 注意：本地缓存写入由 GlobalCache 统一处理
                    return value;
                }
                // 6. 加锁成功后再次查本地缓存，防止极端并发下第一个线程已写入缓存
                if (infra.enableL2Caching()) {
                    value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, key);
                    if (value != null) {
                        return value;
                    }
                }
                // 7. 再查Redis，防止第一个线程已写入Redis但本地缓存还没同步
                value = getObjectFromHash(key, clazz);
                if (value != null) {
                    return value;
                }
                // 8. 还没有命中，说明确实需要回源DB
                value = dbLoader.get();
                if (value != null) {
                    addObject(key, value, timeout);
                    if (config.isEnable()) {
                        bloomFilter.put(key);
                    }
                }
                return value;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        } else {
            // 9. 布隆过滤器判定不存在，直接回源DB并写入缓存
            T value = dbLoader.get();
            if (value != null) {
                addObject(key, value, timeout);
                bloomFilter.put(key);
            }
            return value;
        }
    }

    /**
     * 检查指定的Key是否存在的方法
     *
     * @param key     资源键
     * @param hashKey HASH资源键
     * @return 返回检查结果（true：存在，false：不存在）
     */
    @Override
    public boolean existsInHash(String key, String hashKey) {
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    /**
     * 根据HASH资源键获取资源HASH_KEY集合的方法
     *
     * @param key 资源键
     * @return 返回资源值集合
     */
    @Override
    public Set<String> getHashKeyList(String key) {
        return redisPerfGuard.<Set<String>>execute("RedisHashManager", "getHashKeyList", RedisOperationCatalog.HASH_KEYS, () -> {
            var objKeys = redisTemplate.opsForHash().keys(key);
            return objKeys.stream().map(Object::toString).collect(Collectors.toSet());
        });
    }

    /**
     * 获取Hash对象（支持复杂类型）
     *
     * @param key       缓存key
     * @param reference 目标类型
     * @return 返回对象
     */
    @Override
    public <T> T getObjectFromHash(String key, TypeReference<T> reference) {
        return redisPerfGuard.<T>execute("RedisHashManager", "getObjectFromHash", RedisOperationCatalog.HASH_FULL, () -> {
            var bloomConfig = cacheProperties.getBloomFilter();
            // 1. 先用布隆过滤器判定
            if (!bloomConfig.isEnable() || bloomFilter.mightContain(key)) {
                // 2. 布隆过滤器判定可能存在，查询Redis
                var map = redisTemplate.opsForHash().entries(key);
                if (map.isEmpty()) {
                    return null;
                }
                return JsonUtils.getInstance().convertObject(map, reference);
            }
            // 3. 布隆过滤器判定不存在，直接返回null
            return null;
        });
    }

    /**
     * 获取Hash对象
     *
     * @param key       缓存key
     * @param valueClass 目标类型
     * @return 返回对象
     */
    @Override
    public <T> T getObjectFromHash(String key, Class<T> valueClass) {
        return redisPerfGuard.<T>execute("RedisHashManager", "getObjectFromHash", RedisOperationCatalog.HASH_FULL, () -> {
            var config = cacheProperties.getBloomFilter();
            // 1. 布隆过滤器判定
            if (config.isEnable() && !bloomFilter.mightContain(key)) {
                return null;
            }
            // 2. 查询Redis
            var map = redisTemplate.opsForHash().entries(key);
            if (map.isEmpty()) {
                return null;
            }
            return JsonUtils.getInstance().convertObject(map, valueClass);
        });
    }

    /**
     * 根据资源键获取资源值的方法
     *
     * @param key     资源键
     * @param hashKey HASH资源键
     * @param clazz   目标缓存类型
     * @param <T>     接收返回值使用的泛型类型
     * @return 返回资源值
     */
    @Override
    public <T> T getFromHash(String key, String hashKey, Class<T> clazz) {
        var config = cacheProperties.getBloomFilter();
        String bloomHashKey = "bloom:%s:%s".formatted(key, hashKey);
        if (config.isEnable() && !bloomFilter.mightContain(bloomHashKey)) {
            return null;
        }
        try {
            var origin = redisTemplate.opsForHash().get(key, hashKey);
            return JsonUtils.getInstance().convertObject(origin, clazz);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据资源键获取资源值的方法（支持复杂类型）
     *
     * @param key       资源键
     * @param hashKey   HASH资源键
     * @param reference 目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回资源值
     */
    @Override
    public <T> T getFromHash(String key, String hashKey, TypeReference<T> reference) {
        var config = cacheProperties.getBloomFilter();
        String bloomHashKey = "bloom:%s:%s".formatted(key, hashKey);
        if (config.isEnable() && !bloomFilter.mightContain(bloomHashKey)) {
            return null;
        }
        try {
            var origin = redisTemplate.opsForHash().get(key, hashKey);
            return JsonUtils.getInstance().convertObject(origin, reference);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据资源键获取资源值的方法（批量获取，支持复杂类型）
     *
     * @param key       资源键
     * @param hashKeys  HASH资源键集合
     * @param reference 目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回资源值
     */
    @Override
    public <T> List<T> getFromHash(String key, Collection<String> hashKeys, TypeReference<T> reference) {
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            List<String> filteredKeys = hashKeys.stream().filter(hk -> bloomFilter.mightContain("bloom:%s:%s".formatted(key, hk))).collect(Collectors.toList());
            if (filteredKeys.isEmpty()) {
                return Collections.emptyList();
            }
            hashKeys = filteredKeys;
        }
        try {
            if (hashKeys.size() > BATCH_SIZE) {
                throw new IllegalArgumentException("一次性获取的数据量过大，不允许超过20条。");
            }
            HashOperations<String, String, Object> operations = redisTemplate.opsForHash();
            var origin = operations.multiGet(key, hashKeys);
            return origin.stream().filter(Objects::nonNull).map(o -> {
                if (o instanceof String str) {
                    return JsonUtils.getInstance().deserialize(str, reference);
                }
                return JsonUtils.getInstance().convertObject(o, reference);
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取Hash表所有数据（Map形式）
     *
     * @param key   资源键
     * @param clazz 目标类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回key到对象的映射
     */
    @Override
    public <T> Map<String, T> getAllMapFromHash(String key, Class<T> clazz) {
        return redisPerfGuard.<Map<String, T>>execute("RedisHashManager", "getAllMapFromHash", RedisOperationCatalog.HASH_FULL, () -> {
            var config = cacheProperties.getBloomFilter();
            if (config.isEnable() && !bloomFilter.mightContain(key)) {
                return Collections.emptyMap();
            }
            try {
                Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
                Map<String, T> resultMap = new HashMap<>(map.size());
                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                    resultMap.put(entry.getKey().toString(), JsonUtils.getInstance().convertObject(entry.getValue(), clazz));
                }
                return resultMap;
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                return Collections.emptyMap();
            }
        });
    }

    /**
     * 添加对象到缓存中的方法（Hash）
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    @Override
    public void addObject(String key, Object value) {
        Map<String, Object> map = JsonUtils.getInstance().convertObjectToMap(value);
        redisTemplate.opsForHash().putAll(key, map);
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            bloomFilter.put(key);
        }
    }

    /**
     * 添加对象到缓存中的方法（Hash）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时间（单位：毫秒）
     */
    @Override
    public void addObject(String key, Object value, long timeout) {
        Map<String, Object> map = JsonUtils.getInstance().convertObjectToMap(value);
        redisTemplate.opsForHash().putAll(key, map);
        long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
        redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            bloomFilter.put(key);
        }
    }

    /**
     * 刷新对象数据的方法
     *
     * @param key  缓存键
     * @param func 刷新缓存值的回调函数
     * @param <T>  回调的类型
     * @return 返回刷新结果
     */
    @Override
    public <T> T refreshObject(String key, UnaryOperator<T> func) {
        try (var lock = lockManager.optimisticLock(key, TIME_OUT)) {
            if (!lock.isSuccess()) {
                return null;
            }
            T newObject = refresh(key, func);
            if (newObject == null) {
                return null;
            }
            Map<String, Object> map = JsonUtils.getInstance().convertObjectToMap(newObject);
            redisTemplate.opsForHash().putAll(key, map);
            // 布隆过滤器同步（刷新时确保布隆过滤器中有记录）
            var config = cacheProperties.getBloomFilter();
            if (config.isEnable()) {
                bloomFilter.put(key);
            }
            return newObject;
        }
    }

    /**
     * 批量添加缓存到Redis Hash的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    @Override
    public void batchAddToHash(Map<String, Map<String, ?>> map) {
        redisTemplate.executePipelined(new SessionCallback<String>() {
            @Override
            public <K, V> String execute(@Nonnull RedisOperations<K, V> redisOperations) throws DataAccessException {
                for (Map.Entry<String, Map<String, ?>> entry : map.entrySet()) {
                    redisTemplate.opsForHash().putAll(entry.getKey(), entry.getValue());
                }
                return null;
            }
        });
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            for (String key : map.keySet()) {
                bloomFilter.put(key);
            }
        }
    }

    /**
     * 批量添加集合数据到Redis hash表的方法
     *
     * @param key 集合key
     * @param map 批量添加的集合对象
     */
    @Override
    public void addHash(String key, Map<String, ?> map) {
        redisTemplate.opsForHash().putAll(key, map);
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            bloomFilter.put(key);
        }
    }

    /**
     * 添加键值对信息到Redis hash表的方法
     *
     * @param key       集合key
     * @param hashKey   hash表键
     * @param hashValue hash表值
     */
    @Override
    public void addHash(String key, String hashKey, Object hashValue) {
        redisTemplate.opsForHash().put(key, hashKey, hashValue);
        // 布隆过滤器同步（Hash 子项使用特殊的 bloom key 格式）
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            String bloomHashKey = "bloom:%s:%s".formatted(key, hashKey);
            bloomFilter.put(bloomHashKey);
        }
    }

    /**
     * 从指定Key中批量移除哈希表中指定键值对的方法
     *
     * @param key      元素KEY
     * @param hashKeys 批量移除的hashKey列表
     */
    @Override
    public void removeHashItem(String key, String... hashKeys) {
        redisTemplate.opsForHash().delete(key, (Object[]) hashKeys);
    }

    /**
     * 获取Hash表中元素数量的方法
     *
     * @param key 元素KEY
     * @return 返回执行结果
     */
    @Override
    public Long getHashSize(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    @SuppressWarnings("unchecked")
    private <T> T refresh(String key, UnaryOperator<T> func) {
        RedisCallback<byte[]> callback = connection -> connection.stringCommands().get(CacheKeyUtils.getRealKey(key).getBytes());
        byte[] bytes = redisTemplate.execute(callback);
        if (Objects.isNull(bytes)) {
            return null;
        }
        T originObject = (T) redisTemplate.getValueSerializer().deserialize(bytes);
        return func.apply(originObject);
    }

}
