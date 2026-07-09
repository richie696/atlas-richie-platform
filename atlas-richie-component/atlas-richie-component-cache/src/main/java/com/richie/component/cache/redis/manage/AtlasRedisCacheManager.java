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

import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import jakarta.annotation.Nonnull;

import java.time.Duration;
import java.util.Map;

/**
 * 自定义RedisCacheManager
 *
 * @author richie696
 * @version 1.0.0
 * @since 2023-11-01 15:42:24
 */
public class AtlasRedisCacheManager extends RedisCacheManager {

    private static final char SEPARATOR = '#';

    /**
     * Constructs a new {@link AtlasRedisCacheManager} instance.
     * @param cacheWriter must not be {@literal null}.
     * @param defaultCacheConfiguration must not be {@literal null}. Maybe just use
     */
    public AtlasRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration) {
        super(cacheWriter, defaultCacheConfiguration);
    }

    /**
     * Constructs a new {@link AtlasRedisCacheManager} instance.
     * @param cacheWriter must not be {@literal null}.
     * @param defaultCacheConfiguration must not be {@literal null}. Maybe just use
     * @param initialCacheNames must not be {@literal null}.
     */
    public AtlasRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, String... initialCacheNames) {
        super(cacheWriter, defaultCacheConfiguration, initialCacheNames);
    }

    /**
     * Constructs a new {@link AtlasRedisCacheManager} instance.
     * @param cacheWriter must not be {@literal null}.
     * @param defaultCacheConfiguration must not be {@literal null}. Maybe just use
     * @param allowInFlightCacheCreation allow create unconfigured cache.
     * @param initialCacheNames must not be {@literal null}.
     */
    public AtlasRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, boolean allowInFlightCacheCreation, String... initialCacheNames) {
        super(cacheWriter, defaultCacheConfiguration, allowInFlightCacheCreation, initialCacheNames);
    }

    /**
     * Constructs a new {@link AtlasRedisCacheManager} instance.
     * @param cacheWriter must not be {@literal null}.
     * @param defaultCacheConfiguration must not be {@literal null}. Maybe just use
     * @param initialCacheConfigurations must not be {@literal null}.
     */
    public AtlasRedisCacheManager(RedisCacheWriter cacheWriter, RedisCacheConfiguration defaultCacheConfiguration, Map<String, RedisCacheConfiguration> initialCacheConfigurations) {
        super(cacheWriter, defaultCacheConfiguration, initialCacheConfigurations);
    }

    @Nonnull
    @Override
    protected RedisCache createRedisCache(@Nonnull String name, RedisCacheConfiguration cacheConfig) {
        if (name.indexOf(SEPARATOR) > 0) {
            String[] cacheName = name.split(String.valueOf(SEPARATOR));
            name = cacheName[0];
            cacheConfig = cacheConfig.entryTtl(Duration.ofSeconds(Long.parseLong(cacheName[1])));
        }
        return super.createRedisCache(name, cacheConfig);
    }

    /**
     * 获取缓存
     * @param name 缓存名称
     * @return 缓存
     */
    @Override
    public Cache getCache(String name) {
        String realName;
        if (name.indexOf(SEPARATOR) > 0) {
            String[] cacheName = name.split(String.valueOf(SEPARATOR));
            realName = cacheName[0];
        } else {
            realName = name;
        }
        Cache cache = this.lookupCache(realName);
        if (cache != null) {
            return cache;
        }
        return super.getCache(name);
    }

}
