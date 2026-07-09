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
package com.richie.component.cache.local.manage;

import com.richie.component.cache.local.enums.CacheProvider;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 本地缓存静态工具类
 * <p>
 * <img alt="principle" src ="https://www.richie696.cn/file/a.jpg" />
 *
 * @author richie696
 * @version 1.0
 * @since 2023-12-29 15:11:54
 */
@Component
public class LocalCache {

    /** 默认构造函数，供 Spring 实例化使用。 */
    public LocalCache() {
    }

    private static final AtomicReference<LocalCacheManager> MANAGE = new AtomicReference<>();

    /**
     * 初始化本地缓存工具类的方法（该接口由Spring调用）
     *
     * @param localCacheManager 本地缓存管理器
     */
    @Autowired
    public void setLocalCacheManager(LocalCacheManager localCacheManager) {
        if (LocalCache.MANAGE.get() == null) {
            synchronized (LocalCache.class) {
                if (LocalCache.MANAGE.get() == null) {
                    LocalCache.MANAGE.set(localCacheManager);
                }
            }
        }
    }

    /**
     * 获取当前本地缓存提供者（如 EHCACHE、CAFFEINE 等）。
     *
     * @return 缓存提供者枚举
     */
    public static CacheProvider getCacheProvider() {
        return LocalCache.MANAGE.get().getCacheProvider();
    }


    /**
     * 写入缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param value     缓存value
     */
    public static void put(CacheName cacheName, String key, Object value) {
        MANAGE.get().put(cacheName, key, value);
    }

    /**
     * 获取缓存的方法
     * <p style="color: red">此方法不会触发缓存加载策略
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param <T>       缓存值类型
     * @return 返回缓存值
     */
    @Nullable
    public static <T> T get(CacheName cacheName, String key) {
        return MANAGE.get().get(cacheName, key);
    }

    /**
     * 批量获取缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param keys      缓存key集合
     * @param <T>       缓存值类型
     * @return 返回缓存值
     */
    public static <T> Map<String, T> getAll(CacheName cacheName, Set<String> keys) {
        return MANAGE.get().getAll(cacheName, keys);
    }

    /**
     * 检查缓存是否存在的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @return 返回缓存是否存在
     */
    public static boolean containsKey(CacheName cacheName, String key) {
        return MANAGE.get().containsKey(cacheName, key);
    }

    /**
     * 移除缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @return 返回是否移除成功
     */
    public static boolean remove(CacheName cacheName, String key) {
        return MANAGE.get().remove(cacheName, key);
    }

    /**
     * 批量移除缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param keys      缓存key集合
     */
    public static void removeAll(CacheName cacheName, Set<String> keys) {
        MANAGE.get().removeAll(cacheName, keys);
    }

    /**
     * 清空缓存的方法
     *
     * @param cacheName 分级缓存名称
     */
    public static void removeAll(CacheName cacheName) {
        MANAGE.get().removeAll(cacheName);
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
    public static boolean replace(CacheName cacheName, String key, Object oldValue, Object newValue) {
        return MANAGE.get().replace(cacheName, key, oldValue, newValue);
    }

    /**
     * 以原子性操作的方式写入缓存的方法
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
    public static boolean putIfAbsent(CacheName cacheName, String key, Object value) {
        return MANAGE.get().putIfAbsent(cacheName, key, value);
    }

    /**
     * 获取并移除缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param <T>       缓存值类型
     * @return 返回缓存值
     */
    public static <T> T getAndRemove(CacheName cacheName, String key) {
        return MANAGE.get().getAndRemove(cacheName, key);
    }

    /**
     * 获取并替换缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param value     缓存value
     * @param <T>       缓存值类型
     * @return 返回缓存值
     */
    public static <T> T getAndReplace(CacheName cacheName, String key, Object value) {
        return MANAGE.get().getAndReplace(cacheName, key, value);
    }

    /**
     * 获取并更新缓存的方法
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存key
     * @param value     缓存value
     * @param <T>       缓存值类型
     * @return 返回缓存值
     */
    public static <T> T getAndPut(CacheName cacheName, String key, Object value) {
        return MANAGE.get().getAndPut(cacheName, key, value);
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
    public static void loadAll(CacheName cacheName, Set<String> keys, boolean replaceExistingValues, CompletionListener completionListener) {
        MANAGE.get().loadAll(cacheName, keys, replaceExistingValues, completionListener);
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
    public static <T> T invoke(CacheName cacheName, String key, EntryProcessor<String, Object, ExpiryWrapper<T>> entryProcessor,
                               Object... arguments) throws EntryProcessorException {
        return MANAGE.get().invoke(cacheName, key, entryProcessor, arguments);
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
    public static <T> Map<String, T> invokeAll(CacheName cacheName, Set<String> keys,
                                               EntryProcessor<String, Object, ExpiryWrapper<T>> entryProcessor,
                                               Object... arguments) {
        return MANAGE.get().invokeAll(cacheName, keys, entryProcessor, arguments);
    }

    /**
     * 按数量弹出并移除缓存条目，返回键值映射。
     *
     * @param cacheName 分级缓存名称
     * @param count     弹出数量
     * @param <T>       值类型
     * @return 键到值的映射
     */
    public static <T> Map<String, T> popByCount(CacheName cacheName, int count) {
        return MANAGE.get().popByCount(cacheName, count);
    }

    /**
     * 为指定 key 设置过期时间。
     *
     * @param cacheName 分级缓存名称
     * @param key       缓存 key
     * @param timeout   过期时长
     * @param unit       时间单位
     */
    public static void expiry(CacheName cacheName, String key, long timeout, TimeUnit unit) {
        MANAGE.get().setExpiry(cacheName, key, timeout, unit);
    }
}
