package com.richie.component.cache.redis.manage;

import com.richie.context.utils.data.JsonUtils;
import com.richie.context.bloom.BloomFilter;
import com.richie.component.cache.commons.CacheKeyUtils;
import com.richie.component.cache.config.CacheProperties;
import com.richie.component.cache.enums.L2CachingRegion;
import com.richie.component.cache.function.CacheFunction;
import com.richie.component.cache.function.StringFunction;
import com.richie.component.cache.ops.CacheInfrastructure;
import com.richie.component.cache.local.manage.LocalCache;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.bean.MultiStringRedisTemplate;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.RedisSerializer;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * String类型缓存管理器
 *
 * <p><b>String 值滥用提示</b>：请勿将集合、Map、数组或任意 JavaBean 直接作为 value 依赖 JSON 序列化塞进单 key（易 BIGKEY、阻塞序列化、难部分更新）。
 * 应优先使用 Hash/List 等结构；开启 {@code spring.data.redis.perf.enabled} 与 {@code warn-string-payload-anti-patterns}
 * 时由 {@link RedisPerfGuard#checkStringWritePayload} 在写入前检测并打 WARN/ERROR。
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-09-15 16:50:54
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisStringManager implements StringFunction {

    /** 多数据源 Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** 多数据源 String 模板 */
    private final MultiStringRedisTemplate stringRedisTemplate;

    /** 缓存配置（含布隆过滤器等） */
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
     * 防缓存击穿：String类型（集成布隆过滤器+本地缓存+Redis）
     */
    @Override
    public String getFromStringWithLock(String key, Supplier<String> dbLoader, long timeout) {
        // 注意：本地缓存检查已由 GlobalCache 统一处理，此处不再检查
        var config = cacheProperties.getBloomFilter();
        // 1. 查布隆过滤器
        if (!config.isEnable() || bloomFilter.mightContain(key)) {
            // 2. 查Redis
            String value = getFromString(key, String.class);
            if (value != null) {
                return value;
            }
            // 3. Redis没有，尝试加锁后查dbLoader
            String lockKey = "lock:%s".formatted(key);
            try (CacheLock lock = lockManager.optimisticLock(lockKey, DB_LOADER_TIME_OUT)) {
                if (!lock.isSuccess()) {
                    Thread.sleep(50);
                    // 4. 加锁失败，再次查本地缓存，防止其他线程已写入
                    if (infra.enableL2Caching()) {
                        value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, key);
                        if (value != null) {
                            return value;
                        }
                    }
                    value = getFromString(key, String.class);
                    // 注意：本地缓存写入由 GlobalCache 统一处理
                    return value;
                }
                // 5. 加锁成功后再查一次本地缓存，防止极端并发
                if (infra.enableL2Caching()) {
                    value = LocalCache.get(L2CachingRegion.GLOBAL_CACHE, key);
                    if (value != null) {
                        return value;
                    }
                }
                value = getFromString(key, String.class);
                if (value != null) {
                    return value;
                }
                // 6. 还没有，查dbLoader
                value = dbLoader.get();
                if (value != null) {
                    addValue(key, value, timeout);
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
            // 7. 布隆过滤器判定不存在，直接查dbLoader并写入缓存
            String value = dbLoader.get();
            if (value != null) {
                addValue(key, value, timeout);
                bloomFilter.put(key);
            }
            return value;
        }
    }

    /**
     * 批量添加缓存到Redis String中的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    @Override
    public void batchAddToString(Map<String, ?> map) {
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            guardStringPayload("batchAddToString", entry.getKey(), entry.getValue());
        }
        redisTemplate.executePipelined(new SessionCallback<String>() {
            @SuppressWarnings("unchecked")
            @Override
            public <K, V> String execute(@Nonnull RedisOperations<K, V> redisOperations) throws DataAccessException {
                for (Map.Entry<String, ?> entry : map.entrySet()) {
                    redisOperations.opsForValue().set((K) entry.getKey(), (V) entry.getValue());
                }
                return null;
            }
        });
        // 刷新布隆过滤器
        if (cacheProperties.getBloomFilter().isEnable()) {
            bloomFilter.putAll(map.keySet());
        }
    }

    /**
     * 批量添加缓存到Redis String中的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map     批量添加的缓存数据
     * @param timeout 超时时间（单位：毫秒）
     */
    @Override
    public void batchAddToString(Map<String, ?> map, long timeout) {
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            guardStringPayload("batchAddToString", entry.getKey(), entry.getValue());
        }
        redisTemplate.executePipelined(new SessionCallback<String>() {
            @SuppressWarnings("unchecked")
            @Override
            public <K, V> String execute(@Nonnull RedisOperations<K, V> redisOperations) throws DataAccessException {
                for (Map.Entry<String, ?> entry : map.entrySet()) {
                    long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                    redisOperations.opsForValue().set((K) entry.getKey(), (V) entry.getValue(), realTimeout, TimeUnit.MILLISECONDS);
                }
                return null;
            }
        });
        // 刷新布隆过滤器
        if (cacheProperties.getBloomFilter().isEnable()) {
            bloomFilter.putAll(map.keySet());
        }
    }

    /**
     * 添加缓存到Redis String中的方法
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时间（单位：毫秒）
     */
    @Override
    public void addValue(String key, Object value, long timeout) {
        guardStringPayload("addValue", key, value);
        long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
        redisTemplate.opsForValue().set(key, value, realTimeout, TimeUnit.MILLISECONDS);
        if (cacheProperties.getBloomFilter().isEnable()) {
            bloomFilter.put(key);
        }
    }

    /**
     * 添加缓存到Redis String中的方法
     *
     * @param key   缓存键
     * @param value 缓存值
     */
    @Override
    public void addValue(String key, Object value) {
        guardStringPayload("addValue", key, value);
        redisTemplate.opsForValue().set(key, value);
        if (cacheProperties.getBloomFilter().isEnable()) {
            bloomFilter.put(key);
        }
    }

    /**
     * 添加缓存到Redis String中的方法（如果不存在）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 超时时间（单位：毫秒）
     * @return 返回添加结果
     */
    @Override
    public boolean addValueIfAbsent(String key, Object value, long timeout) {
        guardStringPayload("addValueIfAbsent", key, value);
        long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
        boolean result = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, realTimeout, TimeUnit.MILLISECONDS));
        if (result && cacheProperties.getBloomFilter().isEnable()) {
            bloomFilter.put(key);
        }
        return result;
    }

    /**
     * 添加缓存到Redis String中的方法（如果不存在）
     *
     * @param key   缓存键
     * @param value 缓存值
     * @return 返回添加结果
     */
    @Override
    public boolean addValueIfAbsent(String key, Object value) {
        guardStringPayload("addValueIfAbsent", key, value);
        boolean result = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value));
        if (result && cacheProperties.getBloomFilter().isEnable()) {
            bloomFilter.put(key);
        }
        return result;
    }

    /**
     * 计数器+1的方法
     *
     * @param key     缓存键
     * @param timeout 超时时间
     * @return 返回最新地计数值
     */
    @Override
    public Long increment(String key, Long timeout) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (timeout != null && timeout > 0) {
            redisTemplate.expire(key, timeout, TimeUnit.MILLISECONDS);
        }
        if (value == null) {
            log.warn("在 redis pipeline 和 transaction 中执行 incr 命令时，返回值永远为 0。");
        }
        return Objects.requireNonNullElse(value, 0L);
    }

    /**
     * 批量更新缓存对象的方法
     *
     * @param batchUpdate 批量更新的数据
     * @param timeout     超时时间
     */
    @Override
    public void batchUpdateIfAbsent(Map<String, ?> batchUpdate, Long timeout) {
        for (Map.Entry<String, ?> e : batchUpdate.entrySet()) {
            guardStringPayload("batchUpdateIfAbsent", e.getKey(), e.getValue());
        }
        redisTemplate.opsForValue().multiSet(batchUpdate);
        if (timeout != null && timeout > 0) {
            batchUpdate.keySet().forEach(key -> {
                long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
            });
        }
    }

    /**
     * 计数器+1的方法
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间
     * @return 返回最新的计数值
     */
    @Override
    public Long increment(String key, long delta, Long timeout) {
        Long value = redisTemplate.opsForValue().increment(key, delta);
        if (timeout != null && timeout > 0) {
            redisTemplate.expire(key, timeout, TimeUnit.MILLISECONDS);
        }
        if (value == null) {
            log.warn("在 redis pipeline 和 transaction 中执行 incr 命令时，返回值永远为 0。");
        }
        return Objects.requireNonNullElse(value, 0L);
    }

    /**
     * 计数器+1的方法
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间
     * @return 返回最新的计数值
     */
    @Override
    public Double increment(String key, double delta, Long timeout) {
        Double value = redisTemplate.opsForValue().increment(key, delta);
        if (timeout != null && timeout > 0) {
            redisTemplate.expire(key, timeout, TimeUnit.MILLISECONDS);
        }
        if (value == null) {
            log.warn("在 redis pipeline 和 transaction 中执行 incr 命令时，返回值永远为 0。");
        }
        return Objects.requireNonNullElse(value, 0.0D);
    }

    /**
     * 计数器-1的方法
     *
     * @param key     缓存键
     * @param timeout 超时时间
     * @return 返回最新的计数值
     */
    @Override
    public long decrement(String key, Long timeout) {
        Long value = redisTemplate.opsForValue().decrement(key);
        if (timeout != null && timeout > 0) {
            redisTemplate.expire(key, timeout, TimeUnit.MILLISECONDS);
        }
        if (value == null) {
            log.warn("在 redis pipeline 和 transaction 中执行 decr 命令时，返回值永远为 0。");
        }
        return Objects.requireNonNullElse(value, 0L);
    }

    /**
     * 计数器-1的方法
     *
     * @param key     缓存键
     * @param delta   增量
     * @param timeout 超时时间
     * @return 返回最新的计数值
     */
    @Override
    public long decrement(String key, long delta, Long timeout) {
        Long value = redisTemplate.opsForValue().decrement(key, delta);
        if (timeout != null && timeout > 0) {
            redisTemplate.expire(key, timeout, TimeUnit.MILLISECONDS);
        }
        if (value == null) {
            log.warn("在 redis pipeline 和 transaction 中执行 decr 命令时，返回值永远为 0。");
        }
        return Objects.requireNonNullElse(value, 0L);
    }

    /**
     * 获取匹配的 KEY 对应值的方法
     * <p style="color: red">（注：此方法可能会破坏分布式锁对值的锁定，慎用！）
     *
     * @param keys      匹配的 KEY 集合
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回 KEY 对应的值，如果某个值不存在则返回null
     */
    @Override
    public <T> Map<String, T> getValueMap(final List<String> keys, TypeReference<T> reference) {
        return redisPerfGuard.<Map<String, T>>execute("RedisStringManager", "getValueMap", RedisOperationCatalog.STRING_MULTI_GET, () -> {
            List<String> workingKeys = keys;
            var config = cacheProperties.getBloomFilter();
            if (config.isEnable()) {
                // 过滤掉布隆过滤器判定不存在的key
                List<String> filteredKeys = new ArrayList<>();
                for (String key : workingKeys) {
                    if (bloomFilter.mightContain(key)) {
                        filteredKeys.add(key);
                    }
                }
                if (filteredKeys.isEmpty()) {
                    return Map.of();
                }
                workingKeys = filteredKeys;
            }
            var objects = redisTemplate.opsForValue().multiGet(workingKeys);
            if (objects == null) {
                return Map.of();
            }
            Map<String, T> result = new HashMap<>(objects.size());
            for (int i = 0; i < objects.size(); i++) {
                var object = objects.get(i);
                if (object instanceof String value) {
                    result.put(workingKeys.get(i), JsonUtils.getInstance().deserialize(value, reference));
                } else {
                    result.put(workingKeys.get(i), JsonUtils.getInstance().convertObject(object, reference));
                }
            }
            return result;
        });
    }

    /**
     * 获取匹配的 KEY 对应值的方法
     * <p style="color: red">（注：此方法可能会破坏分布式锁对值的锁定，慎用！）
     *
     * @param keys      匹配的 KEY 集合
     * @param reference 内省对象
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回 KEY 对应的值，如果某个值不存在则返回null
     */
    @Override
    public <T> List<T> getObjects(final Collection<String> keys, TypeReference<T> reference) {
        return redisPerfGuard.<List<T>>execute("RedisStringManager", "getObjects", RedisOperationCatalog.STRING_MULTI_GET, () -> {
            Collection<String> workingKeys = keys;
            if (workingKeys.size() > BATCH_SIZE) {
                throw new IllegalArgumentException("一次性获取的数据量过大，不允许超过20条。");
            }
            var config = cacheProperties.getBloomFilter();
            if (config.isEnable()) {
                // 过滤掉布隆过滤器判定不存在的key
                List<String> filteredKeys = new ArrayList<>();
                for (String key : workingKeys) {
                    if (bloomFilter.mightContain(key)) {
                        filteredKeys.add(key);
                    }
                }
                if (filteredKeys.isEmpty()) {
                    return Collections.emptyList();
                }
                workingKeys = filteredKeys;
            }
            var objects = redisTemplate.opsForValue().multiGet(workingKeys);
            if (objects == null) {
                return Collections.emptyList();
            }
            List<T> result = new ArrayList<>(objects.size());
            for (Object object : objects) {
                if (object instanceof String value) {
                    result.add(JsonUtils.getInstance().deserialize(value, reference));
                } else {
                    result.add(JsonUtils.getInstance().convertObject(object, reference));
                }
            }
            return result;
        });
    }

    /**
     * 根据资源键获取资源值的方法
     *
     * @param key   资源键
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值
     */
    @Override
    public <T> T getFromString(String key, Class<T> clazz) {
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable() && !bloomFilter.mightContain(key)) {
            return null;
        }
        try {
            StringRedisTemplate stringTemplate = getStringTemplate(key);
            RedisCallback<byte[]> callback = connection -> connection.stringCommands().get(CacheKeyUtils.getRealKey(key).getBytes());
            byte[] bytes = stringTemplate.execute(callback);
            if (Objects.isNull(bytes)) {
                return null;
            }
            String deserialize = (String) stringTemplate.getValueSerializer().deserialize(bytes);
            return JsonUtils.getInstance().deserialize(deserialize, clazz);
        } catch (Exception e) {
            log.warn("无法获取到缓存值（key = {})", key);
            return null;
        }
    }

    /**
     * 根据资源键获取资源值的方法
     *
     * @param key       资源键
     * @param reference 目标缓存类型
     * @param <T>       接收返回值使用的泛型类型
     * @return 返回资源值
     */
    @Override
    public <T> T getFromString(String key, TypeReference<T> reference) {
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable() && !bloomFilter.mightContain(key)) {
            return null;
        }
        try {
            StringRedisTemplate stringTemplate = getStringTemplate(key);
            RedisCallback<byte[]> callback = connection -> connection.stringCommands().get(CacheKeyUtils.getRealKey(key).getBytes());
            byte[] bytes = stringTemplate.execute(callback);
            if (Objects.isNull(bytes)) {
                return null;
            }
            String deserialize = (String) stringTemplate.getValueSerializer().deserialize(bytes);
            return JsonUtils.getInstance().deserialize(deserialize, reference);
        } catch (Exception e) {
            log.warn("无法获取到缓存值（key = {})", key);
            return null;
        }
    }

    private StringRedisTemplate getStringTemplate(String key) {
        if (key.contains("@@")) {
            return stringRedisTemplate.getSlaveTemplate(key.split("@@")[0]);
        }
        return stringRedisTemplate;
    }

    private void guardStringPayload(String method, String key, Object value) {
        redisPerfGuard.checkStringWritePayload("RedisStringManager", method, key, value);
    }

    /**
     * 模糊匹配获取所有值的方法
     *
     * @param match 模糊匹配的key
     * @param count 每次扫描的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值
     */
    @Override
    public <T> Map<String, T> scan(String match, int count, Class<T> clazz) {
        ScanOptions scanOptions = ScanOptions.scanOptions().match("*" + match + "*").count(count).build();
        RedisSerializer<?> keySerializer = redisTemplate.getKeySerializer();
        try (ConvertingCursor<byte[], ?> cursor = redisTemplate.executeWithStickyConnection(redisConnection ->
                new ConvertingCursor<>(redisConnection.keyCommands().scan(scanOptions), keySerializer::deserialize))) {
            List<String> keys = new ArrayList<>(Math.toIntExact(cursor.stream().count()));
            while (cursor.hasNext()) {
                String value = (String) cursor.next();
                if (value == null || value.startsWith(LOCK_KEY)) {
                    continue;
                }
                keys.add(value);
            }
            if (keys.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<String, T> result = new HashMap<>(keys.size());
            for (String key : keys) {
                T value = getFromString(key, clazz);
                result.put(key, value);
            }
            return result;
        }
    }

}
