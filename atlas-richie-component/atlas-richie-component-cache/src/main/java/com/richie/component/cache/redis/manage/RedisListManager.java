package com.richie.component.cache.redis.manage;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.cache.bloom.BloomFilterFacade;
import com.richie.component.cache.config.CacheProperties;
import com.richie.component.cache.function.CacheFunction;
import com.richie.component.cache.function.ListFunction;
import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.cache.redis.perf.RedisCommandMeta;
import com.richie.component.cache.redis.perf.RedisOperationCatalog;
import com.richie.component.cache.redis.perf.RedisPerfGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * List类型缓存管理器
 *
 * @author richie696
 * @version 5.0.0
 * @since 2025-09-15 16:49:51
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${platform.cache.cache-provider:REDIS}'=='REDIS'")
public class RedisListManager implements ListFunction {

    /** Redis 模板（JSON 序列化） */
    @Qualifier("jsonTemplate")
    private final MultiRedisTemplate<Object> redisTemplate;

    /** 缓存配置 */
    private final CacheProperties cacheProperties;

    /** 布隆过滤器门面 */
    private final BloomFilterFacade bloomFilter;

    /** 分布式锁管理器 */
    private final RedisLockManager lockManager;

    /** Redis 性能守卫（可选启用） */
    private final RedisPerfGuard redisPerfGuard;

    /**
     * 防缓存击穿：List
     *
     * @param key   资源键
     * @param index 获取值的队列索引
     * @param clazz 目标缓存类型
     * @param dbLoader 回源加载器
     * @param timeout 超时时间
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值
     */
    @Override
    public <T> List<T> getFromListWithLock(String key, long index, Class<T> clazz, Supplier<List<T>> dbLoader, long timeout) {
        var config = cacheProperties.getBloomFilter();
        String bloomListKey = "bloom:%s:list:%d".formatted(key, index);
        if (config.isEnable()) {
            if (!bloomFilter.contains(bloomListKey)) {
                return List.of();
            }
        }
        List<T> value = getFromList(key, index, clazz);
        if (value != null && !value.isEmpty()) return value;
        String lockKey = "lock:%s:list:%d".formatted(key, index);
        try (CacheLock lock = lockManager.optimisticLock(lockKey, DB_LOADER_TIME_OUT)) {
            if (!lock.isSuccess()) {
                Thread.sleep(50);
                return getFromList(key, index, clazz);
            }
            value = getFromList(key, index, clazz);
            if (value != null && !value.isEmpty()) return value;
            value = dbLoader.get();
            if (value != null && !value.isEmpty()) {
                // 使用 addAndReplaceList 替换整个列表，避免并发重复写入
                addAndReplaceList(key, value);
                long realTimeout = timeout + CacheFunction.getRandomExtraMillis();
                redisTemplate.expire(key, realTimeout, TimeUnit.MILLISECONDS);
                if (config.isEnable()) {
                    bloomFilter.add(bloomListKey);
                }
            }
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * 根据资源键获取资源值的方法
     *
     * @param key   资源键
     * @param index 获取值的队列索引（-1：为获取全部）
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回资源值
     */
    @Override
    public <T> List<T> getFromList(String key, long index, Class<T> clazz) {
        RedisCommandMeta meta = index == -1 ? RedisOperationCatalog.LIST_RANGE : RedisCommandMeta.o1("LINDEX 单下标");
        return redisPerfGuard.<List<T>>execute("RedisListManager", "getFromList", meta, () -> {
            // 如果获取锁成功则继续执行
            List<T> result = new ArrayList<>(10);
            if (index == -1) {
                Long size = redisTemplate.opsForList().size(key);
                assert size != null;
                var range = redisTemplate.opsForList().range(key, 0, size - 1);
                if (Objects.nonNull(range)) {
                    range.stream()
                            .map(o -> JsonUtils.getInstance().convertObject(o, clazz))
                            .filter(Objects::nonNull)
                            .forEach(result::add);
                }
            } else {
                var origin = redisTemplate.opsForList().index(key, index);
                T t = JsonUtils.getInstance().convertObject(origin, clazz);
                if (Objects.nonNull(t)) {
                    result.add(t);
                }
            }
            return result;
        });
    }

    /**
     * 批量添加元素到指定列表的方法（本方法将会添加重复值）
     *
     * @param key    列表名称
     * @param values 列表值
     */
    @Override
    public void addAndReplaceList(String key, List<?> values) {
        redisTemplate.delete(key);
        redisTemplate.opsForList().rightPushAll(key, values.toArray());
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            String bloomListKey = "bloom:%s:list:-1".formatted(key);
            bloomFilter.add(bloomListKey);
        }
    }

    /**
     * 批量添加缓存到Redis List的方法
     * <p style="color: red">（注：此方法非原子性操作，有可能出现并发安全问题。）
     *
     * @param map 批量添加的缓存数据
     */
    @Override
    public void batchAddToList(Map<String, List<?>> map) {
        redisTemplate.executePipelined(new SessionCallback<String>() {
            @Override
            public <K, V> String execute(@Nonnull RedisOperations<K, V> redisOperations) throws DataAccessException {
                for (Map.Entry<String, List<?>> entry : map.entrySet()) {
                    redisTemplate.opsForList().rightPushAll(entry.getKey(), entry.getValue().toArray());
                }
                return null;
            }
        });
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            for (String key : map.keySet()) {
                String bloomListKey = "bloom:%s:list:-1".formatted(key);
                bloomFilter.add(bloomListKey);
            }
        }
    }

    /**
     * 添加元素到指定列表的方法（本方法将会添加重复值）
     *
     * @param key   列表名称
     * @param value 列表值
     */
    @Override
    public void addListItem(String key, Object value) {
        redisTemplate.opsForList().rightPush(key, value);
        // 布隆过滤器同步
        var config = cacheProperties.getBloomFilter();
        if (config.isEnable()) {
            String bloomListKey = "bloom:%s:list:-1".formatted(key);
            bloomFilter.add(bloomListKey);
        }
    }

    /**
     * 添加元素到指定列表的方法（本方法将会添加重复值）
     *
     * @param key   列表名称
     * @param index 要更新的列表索引值
     * @param value 列表值
     */
    @Override
    public void updateListItem(String key, long index, Object value) {
        redisTemplate.opsForList().set(key, index, value);
    }

    /**
     * 删除列表内指定项的方法
     *
     * @param key   列表名称
     * @param value 列表值
     * @param count 删除的数量
     */
    @Override
    public void removeListItem(String key, Object value, int count) {
        redisTemplate.opsForList().remove(key, count, value);
    }


    /**
     * 从列表队首弹出元素的方法
     *
     * @param key   元素KEY
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     */
    @Override
    public <T> T leftPopListElement(String key, Class<T> clazz) {
        var origin = redisTemplate.opsForList().leftPop(key);
        return JsonUtils.getInstance().convertObject(origin, clazz);
    }

    /**
     * 从列表队首弹出元素的方法
     *
     * @param key   元素KEY
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     */
    @Override
    public <T> List<T> leftPopListElement(String key, long count, Class<T> clazz) {
        var objects = redisTemplate.opsForList().leftPop(key, count);
        if (CollectionUtils.isEmpty(objects)) {
            return List.of();
        }
        return objects.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).collect(Collectors.toList());
    }

    /**
     * 从列表队尾弹出元素的方法
     *
     * @param key   元素KEY
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     */
    @Override
    public <T> T rightPopListElement(String key, Class<T> clazz) {
        var origin = redisTemplate.opsForList().rightPop(key);
        return JsonUtils.getInstance().convertObject(origin, clazz);
    }

    /**
     * 从列表队首弹出元素的方法
     *
     * @param key   元素KEY
     * @param count 弹出元素的数量
     * @param clazz 目标缓存类型
     * @param <T>   接收返回值使用的泛型类型
     * @return 返回执行结果
     */
    @Override
    public <T> List<T> rightPopListElement(String key, long count, Class<T> clazz) {
        var objects = redisTemplate.opsForList().rightPop(key, count);
        if (CollectionUtils.isEmpty(objects)) {
            return List.of();
        }
        return objects.stream().map(o -> JsonUtils.getInstance().convertObject(o, clazz)).collect(Collectors.toList());
    }

    /**
     * 从列表队首压入元素的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回执行结果
     */
    @Override
    public Long leftPushListElement(String key, Object value) {
        return redisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 获取List集合元素数量的方法
     *
     * @param key 元素KEY
     * @return 返回执行结果
     */
    @Override
    public Long getListSize(String key) {
        return redisTemplate.opsForList().size(key);
    }

    /**
     * 从列表队尾压入元素的方法
     *
     * @param key   元素KEY
     * @param value 元素值
     * @return 返回执行结果
     */
    @Override
    public Long rightPushListElement(String key, Object value) {
        return redisTemplate.opsForList().rightPush(key, value);
    }

}
