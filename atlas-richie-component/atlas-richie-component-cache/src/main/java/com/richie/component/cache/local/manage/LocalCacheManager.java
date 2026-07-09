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
package com.richie.component.cache.local.manage;


import com.richie.component.cache.local.enums.CacheProvider;
import com.richie.component.cache.local.util.DefensiveCopyUtils;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存管理器，封装了JCache（JSR-107）规范的本地缓存操作。
 * <p>
 * 所有缓存操作均强制启用防御性拷贝，确保外部代码对缓存数据的修改不会影响缓存内容。
 * 使用 Fury 序列化框架实现高性能深拷贝，兼容虚拟线程环境。
 *
 * @author richie696
 * @version 1.3
 * @since 2025-06-16 16:55:19
 */
@Slf4j
@Component
@SuppressWarnings("unchecked")
public class LocalCacheManager {

    /** JSR-107 缓存管理器（由 Spring 注入） */
    @Qualifier("cacheManagerJsr107")
    private final CacheManager cacheManager;

    /**
     * 构造函数，供 Spring 注入 JSR-107 CacheManager 使用。
     *
     * @param cacheManager JSR-107 缓存管理器
     */
    public LocalCacheManager(@Qualifier("cacheManagerJsr107") CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 获取缓存的方法
     *
     * @param cacheName 缓存名称
     * @return 缓存
     */
    private Cache<String, Object> getCache(CacheName cacheName) {
        Cache<String, Object> cache = cacheManager.getCache(cacheName.getCache());
        Objects.requireNonNull(cache, "Cache [%s] not found".formatted(cacheName));
        return cache;
    }

    /**
     * 获取缓存提供者的方法
     *
     * @return 返回本地缓存提供者
     */
    public CacheProvider getCacheProvider() {
        return CacheProvider.valueOfCachingProvider(cacheManager.getCachingProvider().getClass().getName());
    }

    /**
     * 写入缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param value     缓存value
     */
    public void put(CacheName cacheName, String key, Object value) {
        Object valueToStore;
        try {
            valueToStore = DefensiveCopyUtils.deepCopy(value);
        } catch (Exception e) {
            log.warn("防御性拷贝失败，使用原对象: key={}, error={}", key, e.getMessage());
            // 降级：使用原对象（需要业务层保证不修改）
            valueToStore = value;
        }

        // 创建一个包含过期时间信息的包装对象
        ExpiryWrapper<Object> wrapper = new ExpiryWrapper<>(valueToStore, Long.MAX_VALUE);
        getCache(cacheName).put(key, wrapper);
    }

    /**
     * 获取缓存的方法
     * <p style="color: red">此方法不会触发缓存加载策略
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param <T>       缓存值类型
     * @return 返回缓存值（深拷贝后的对象）
     */
    @Nullable
    public <T> T get(CacheName cacheName, String key) {
        Cache<String, Object> cache = getCache(cacheName);
        Object value = cache.get(key);
        if (value instanceof ExpiryWrapper<?> wrapper) {
            if (wrapper.isExpired()) {
                cache.remove(key);
                return null;
            }
            T originalValue = (T) wrapper.getValue();

            try {
                return DefensiveCopyUtils.deepCopy(originalValue);
            } catch (Exception e) {
                log.warn("防御性拷贝失败，返回原对象: key={}, error={}", key, e.getMessage());
                // 降级：返回原对象
                return originalValue;
            }
        }
        return (T) value;
    }

    /**
     * 批量获取缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param keys      缓存key集合
     * @param <T>       缓存值类型
     * @return 返回缓存值（深拷贝后的对象）
     */
    public <T> Map<String, T> getAll(CacheName cacheName, Set<String> keys) {
        var result = new HashMap<String, T>();
        var values = (Map<String, T>) getCache(cacheName).getAll(keys);

        values.forEach((key, value) -> {
            if (value instanceof ExpiryWrapper<?> wrapper) {
                if (wrapper.isExpired()) {
                    getCache(cacheName).remove(key);
                } else {
                    T originalValue = (T) wrapper.getValue();
                    try {
                        result.put(key, DefensiveCopyUtils.deepCopy(originalValue));
                    } catch (Exception e) {
                        log.warn("防御性拷贝失败，返回原对象: key={}, error={}", key, e.getMessage());
                        result.put(key, originalValue);
                    }
                }
            }
        });
        return result;
    }

    /**
     * 检查缓存是否存在的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @return 返回缓存是否存在
     */
    public boolean containsKey(CacheName cacheName, String key) {
        return getCache(cacheName).containsKey(key);
    }

    /**
     * 移除缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @return 返回是否移除成功
     */
    public boolean remove(CacheName cacheName, String key) {
        return getCache(cacheName).remove(key);
    }

    /**
     * 批量移除缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param keys      缓存key集合
     */
    public void removeAll(CacheName cacheName, Set<String> keys) {
        getCache(cacheName).removeAll(keys);
    }

    /**
     * 清空缓存的方法
     *
     * @param cacheName 分级缓存名称
     */
    public void removeAll(CacheName cacheName) {
        getCache(cacheName).removeAll();
    }

    /**
     * 替换缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param oldValue  旧值
     * @param newValue  新值
     * @return 返回是否替换成功
     */
    public boolean replace(CacheName cacheName, String key, Object oldValue, Object newValue) {
        var oldWrapper = (ExpiryWrapper<Object>) getCache(cacheName).get(key);
        if (oldWrapper.getValue().equals(oldValue)) {
            var newWrapper = new ExpiryWrapper<>(newValue, oldWrapper.getExpiryTime());
            return getCache(cacheName).replace(key, oldWrapper, newWrapper);
        }
        return false;
    }

    /**
     * 以原子性操作的方式写入缓存的方法（强制启用防御性拷贝）
     * <pre>
     * 这相当于：{@snippet :
     * if (!cache.containsKey(key)) {
     *       cache.put(key, value);
     *       return true;
     *   } else {
     *       return false;
     *   }
     *}
     *   如果缓存配置为直写，并且此方法返回 true，则将调用关联的 CacheWriter.write(Cache.Entry) 方法。
     *   </pre>
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param value     缓存value
     * @return 返回是否写入成功
     */
    public boolean putIfAbsent(CacheName cacheName, String key, Object value) {
        Object valueToStore;
        try {
            valueToStore = DefensiveCopyUtils.deepCopy(value);
        } catch (Exception e) {
            log.warn("防御性拷贝失败，使用原对象: key={}, error={}", key, e.getMessage());
            valueToStore = value;
        }

        var wrapper = new ExpiryWrapper<>(valueToStore, Long.MAX_VALUE);
        return getCache(cacheName).putIfAbsent(key, wrapper);
    }

    /**
     * 获取并移除缓存的方法
     * <p>
     * 注意：由于缓存已被移除，返回的对象不会影响缓存，因此不需要防御性拷贝
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param <T>       缓存值类型
     * @return 返回缓存值
     */
    public <T> T getAndRemove(CacheName cacheName, String key) {
        var cache = getCache(cacheName);
        var value = (T) cache.getAndRemove(key);
        if (value instanceof ExpiryWrapper<?> wrapper) {
            if (wrapper.isExpired()) {
                cache.remove(key);
                return null; // 如果已过期，返回null
            }
            return (T) wrapper.getValue();
        }
        return value; // 如果不是过期包装对象，直接返回值
    }

    /**
     * 获取并替换缓存的方法（新值强制启用防御性拷贝）
     * <p>
     * 注意：新值需要防御性拷贝（因为要存入缓存），但旧值不需要防御性拷贝（已被替换，不影响缓存）
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param value     缓存value（新值）
     * @param <T>       缓存值类型
     * @return 返回旧缓存值（不需要防御性拷贝）
     */
    public <T> T getAndReplace(CacheName cacheName, String key, Object value) {
        Cache<String, Object> cache = getCache(cacheName);
        var oldWrapper = (ExpiryWrapper<Object>) cache.get(key);
        if (oldWrapper == null || oldWrapper.isExpired()) {
            cache.remove(key);
            return null; // 返回null表示没有旧值
        }

        Object valueToStore;
        try {
            valueToStore = DefensiveCopyUtils.deepCopy(value);
        } catch (Exception e) {
            log.warn("防御性拷贝失败，使用原对象: key={}, error={}", key, e.getMessage());
            valueToStore = value;
        }

        var newWrapper = new ExpiryWrapper<>(valueToStore, oldWrapper.getExpiryTime());
        T oldValue = (T) oldWrapper.getValue();

        cache.getAndReplace(key, newWrapper);
        return oldValue;
    }

    /**
     * 获取并更新缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param value     缓存value
     * @param <T>       缓存值类型
     * @return 返回旧缓存值（深拷贝后的对象）
     */
    public <T> T getAndPut(CacheName cacheName, String key, Object value) {
        var cache = getCache(cacheName);
        var oldWrapper = (ExpiryWrapper<Object>) cache.get(key);

        Object valueToStore;
        try {
            valueToStore = DefensiveCopyUtils.deepCopy(value);
        } catch (Exception e) {
            log.warn("防御性拷贝失败，使用原对象: key={}, error={}", key, e.getMessage());
            valueToStore = value;
        }

        var newWrapper = new ExpiryWrapper<>(valueToStore, oldWrapper != null ? oldWrapper.getExpiryTime() : Long.MAX_VALUE);
        cache.getAndPut(key, newWrapper);

        T oldValue = (T) (oldWrapper != null ? oldWrapper.getValue() : null);

        if (oldValue != null) {
            try {
                return DefensiveCopyUtils.deepCopy(oldValue);
            } catch (Exception e) {
                log.warn("防御性拷贝失败，返回原对象: key={}, error={}", key, e.getMessage());
                return oldValue;
            }
        }

        return oldValue;
    }

    /**
     * 通过指定的 CacheLoader 实现类异步从数据源（可以是本地cache文件、数据库、redis等）中加载缓存的方法
     * <p style="color: red">此方法会触发缓存加载策略
     * <ul>
     *     <li>如果缓存中已存在键的条目，则当且仅当replaceExistingValues为true时，才会加载值。</li>
     *     <li>如果没有为缓存配置加载程序，则不会加载任何对象。</li>
     *     <li>如果在检索或加载对象的过程中遇到问题，则会向CompletionListener提供异常。</li>
     *     <li>操作完成后，将通知指定的CompletionListener接口的实现类。</li>
     *     <li>实现可以选择并行地从所提供的集合加载多个密钥。但是，迭代不能并行发生，因此允许使用非线程安全集。</li>
     *     <li>调用CompletionListener完成监听器的线程依赖于实现。实现还可以选择序列化对不同CompletionListener的调用，而不是每个Completion监听器使用一个线程。</li>
     * </ul>
     *
     * @param cacheName             分级缓存名称
     * @param keys                  缓存key集合
     * @param replaceExistingValues 是否替换已存在的值
     * @param completionListener    缓存加载完成监听器
     */
    public void loadAll(CacheName cacheName, Set<String> keys, boolean replaceExistingValues, CompletionListener completionListener) {
        getCache(cacheName).loadAll(keys, replaceExistingValues, completionListener);
    }

    /**
     * 获取缓存的方法
     *
     * @param cacheName      分级缓存名称
     * @param key            缓存key
     * @param entryProcessor 缓存处理器
     * @param arguments      缓存处理器参数
     * @param <T>            缓存值类型
     * @return 返回缓存值
     * @throws EntryProcessorException 缓存处理器异常
     */
    public <T> T invoke(CacheName cacheName, String key, EntryProcessor<String, Object, ExpiryWrapper<T>> entryProcessor,
                        Object... arguments) throws EntryProcessorException {
        var wrapper = getCache(cacheName).invoke(key, entryProcessor, arguments);
        if (wrapper == null) {
            return null; // 如果缓存中没有值，直接返回null
        }
        if (wrapper.isExpired()) {
            getCache(cacheName).remove(key);
            return null; // 如果已过期，返回null
        }
        return wrapper.getValue(); // 返回实际值
    }

    /**
     * 批量获取缓存的方法
     *
     * @param cacheName      分级缓存名称
     * @param keys           缓存key集合
     * @param entryProcessor 缓存处理器
     * @param arguments      缓存处理器参数
     * @param <T>            缓存值类型
     * @return 返回缓存值
     */
    public <T> Map<String, T> invokeAll(CacheName cacheName, Set<String> keys,
                                        EntryProcessor<String, Object, ExpiryWrapper<T>> entryProcessor,
                                        Object... arguments) {
        var values = getCache(cacheName).invokeAll(keys, entryProcessor, arguments);
        Map<String, T> result = new HashMap<>(values.size());
        values.forEach((key, processorResult) -> {
            ExpiryWrapper<T> wrapper = processorResult.get();
            if (wrapper == null || wrapper.isExpired()) {
                getCache(cacheName).remove(key); // 如果已过期，移除缓存
            } else {
                result.put(key, wrapper.getValue());
            }
        });
        return result;
    }

    /**
     * 获取指定数量的元素
     *
     * @param cacheName 分级缓存名称
     * @param count     获取的元素输了
     * @param <T>       元素值类型
     * @return 返回缓存值
     */
    public <T> Map<String, T> popByCount(CacheName cacheName, int count) {
        Iterator<Cache.Entry<String, Object>> iterator = getCache(cacheName).iterator();
        Map<String, T> result = new HashMap<>();
        int seed = 0;
        while (iterator.hasNext()) {
            Cache.Entry<String, Object> next = iterator.next();
            var wrapper = (ExpiryWrapper<T>) next.getValue();
            if (wrapper == null || wrapper.isExpired()) {
                getCache(cacheName).remove(next.getKey()); // 如果已过期，移除缓存
                continue; // 跳过已过期的元素
            }
            result.put(next.getKey(), wrapper.getValue());
            iterator.remove();
            if (seed++ >= count) {
                break;
            }
        }
        return result;
    }

    /**
     * 设置指定key的过期时间
     * 注意：此方法通过重新设置值的方式来实现过期时间控制
     * 实际过期时间可能会有几秒钟的误差
     *
     * @param cacheName 缓存名称
     * @param key       缓存key
     * @param timeout   过期时间
     * @param unit      时间单位
     */
    public void setExpiry(CacheName cacheName, String key, long timeout, TimeUnit unit) {
        Cache<String, Object> cache = getCache(cacheName);
        Object value = cache.get(key);
        if (value == null) {
            log.warn("Cannot set expiry for key [{}] in cache [{}] because it does not exist.", key, cacheName);
            return;
        }
        ExpiryWrapper<Object> wrapper;
        if (value instanceof ExpiryWrapper) {
            // 如果值已经是过期包装对象，更新过期时间
            wrapper = (ExpiryWrapper<Object>) value;
            wrapper.setExpiryTime(System.currentTimeMillis() + unit.toMillis(timeout));
        } else {
            // 创建一个包含过期时间信息的包装对象
            wrapper = new ExpiryWrapper<>(value, System.currentTimeMillis() + unit.toMillis(timeout));
        }
        cache.put(key, wrapper);
    }

}
