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
package com.richie.component.cache.local.config;

import com.richie.component.cache.local.enums.ExpiryPolicy;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.concurrent.TimeUnit;

/**
 * 缓存定义
 *
 * @author richie696
 * @version 1.0
 * @since 2023-12-29 10:17:01
 */
@Data
@Accessors(chain = true)
public class CacheDefinition {

    /**
     * 缓存名称
     */
    private String name;

    /**
     * 缓存过期策略
     */
    private ExpiryPolicy expiryPolicy;

    /**
     * 缓存过期时间（默认：5分钟，如果过期策略为{@link ExpiryPolicy#ETERNAL}，则此值无效）}
     */
    private Integer expiry = 5;

    /**
     * 缓存过期时间单位（默认：分钟，如果过期策略为{@link ExpiryPolicy#ETERNAL}，则此值无效）
     */
    private TimeUnit expiryUnit = TimeUnit.MINUTES;

    /**
     * 是否启用缓存统计（默认：false）
     */
    private boolean statisticsEnabled = false;

    /**
     * 设置配置的缓存是否应使用按值存储或按引用存储（true：按值存储；false：按引用存储【默认】）
     */
    private boolean storeByValue = false;

    /**
     * 是否启用缓存加载策略（默认：false）
     * <p style="color: red">此属性需要配合{@link CacheDefinition#cacheLoaderClassName} 使用
     */
    private boolean readThrough = false;

    /**
     * 缓存加载策略实现类绝对路径（当从缓存中获取数据时，如果缓存中不存在，则会调用此类的load方法从数据源中加载数据）
     * {@link javax.cache.integration.CacheLoader}
     * <p style="color: red">当通过javax.cache.cache.loadAll（java.util.Set，boolean，CompletionListener）方法读取缓存或将数据加载到缓存时使用。
     */
    private String cacheLoaderClassName;

    /**
     * 是否启用缓存直写策略（默认：false）
     * 在“直写”模式下，当执行下列方法
     * <ul>
     * <li>{@link javax.cache.Cache#put(Object, Object)}</li>
     * <li>{@link javax.cache.Cache#getAndRemove(Object)}</li>
     * <li>{@link javax.cache.Cache#removeAll()}</li>
     * <li>{@link javax.cache.Cache#getAndPut(Object, Object)}</li>
     * <li>{@link javax.cache.Cache#getAndRemove(Object)}</li>
     * <li>{@link javax.cache.Cache#getAndReplace(Object, Object)}</li>
     * <li>{@link javax.cache.Cache#invoke(Object, javax.cache.processor.EntryProcessor, Object...)}</li>
     * <li>{@link javax.cache.Cache#invokeAll(java.util.Set, javax.cache.processor.EntryProcessor, Object...)}</li>
     * </ul>
     * 之一时，调用的“put”操作而发生的高速缓存更新将适当地导致调用配置的 CacheWriter。
     * <p style="color: red">此属性需要配合{@link CacheDefinition#cacheWriterClassName} 使用
     */
    private boolean writeThrough = false;

    /**
     * 缓存写入策略实现类绝对路径（当缓存中数据发生变化时，会调用此类的write方法将数据写入数据源）
     * {@link javax.cache.integration.CacheWriter}
     */
    private String cacheWriterClassName;

    /**
     * 是否启用 cache2k refresh-ahead（过期前异步刷新），仅当 {@code provider=CACHE2K} 时生效
     * <p>refresh-ahead 会在缓存条目即将过期时提前调用 loader 加载新值，避免到期后因回源造成的延迟。
     * 此功能需同时设置 {@code readThrough=true} 和 {@link #cacheLoaderClassName} 才会实际生效。
     */
    private boolean cache2kRefreshAhead = false;

    /**
     * cache2k loader 线程数，用于 refresh-ahead 的后台刷新。
     * 仅当 {@link #cache2kRefreshAhead} 为 {@code true} 时有效。
     * <p>未设置时使用 cache2k 默认值（通常为 1）。
     */
    private Integer cache2kLoaderThreadCount;
}
