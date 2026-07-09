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
package com.richie.component.cache.ops;

import com.richie.component.cache.enums.KeyTypeEnum;
import com.richie.component.cache.enums.L2CachingRegion;
import com.richie.component.cache.local.manage.LocalCache;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 二级缓存（L2）同步辅助类。
 * <p>集中处理{@link LocalCache}的读写、删除和过期逻辑，避免在各 Ops 实现中重复
 * {@code if (enableL2Caching() && enableKeyTypeCache(...))} 判断。</p>
 *
 * @author richie696
 * @version 1.0.0
 * @since 2025-06-05
 */
@Component
@RequiredArgsConstructor
public class L2SyncHelper {

    private static final L2CachingRegion REGION = L2CachingRegion.GLOBAL_CACHE;

    private final CacheInfrastructure infra;

    /**
     * 是否应对指定数据类型启用 L2 同步。
     */
    public boolean isEnabled(KeyTypeEnum keyType) {
        return infra.enableL2Caching() && infra.enableKeyTypeCache(keyType);
    }

    /**
     * 写入本地缓存（不带过期时间）。
     */
    public <T> void put(KeyTypeEnum keyType, String key, T value) {
        if (isEnabled(keyType)) {
            LocalCache.put(REGION, key, value);
        }
    }

    /**
     * 写入本地缓存（带过期时间）。
     */
    public <T> void put(KeyTypeEnum keyType, String key, T value, long timeoutMillis) {
        if (isEnabled(keyType)) {
            LocalCache.put(REGION, key, value);
            LocalCache.expiry(REGION, key, timeoutMillis, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 从本地缓存读取；若未命中则调用 redisLoader 并从 Redis 加载后回写。
     * <p>等价于当前 GlobalCache 中 {@code getWithLocalCache} 的逻辑。</p>
     */
    public <T> T get(KeyTypeEnum keyType, String key, Supplier<T> redisLoader) {
        if (isEnabled(keyType)) {
            T cached = LocalCache.get(REGION, key);
            if (cached != null) {
                return cached;
            }
        }
        T result = redisLoader.get();
        if (result != null && isEnabled(keyType)) {
            LocalCache.put(REGION, key, result);
        }
        return result;
    }

    /**
     * 从本地缓存读取（带锁场景）；逻辑与 {@link #get(KeyTypeEnum, String, Supplier)} 一致。
     */
    public <T> T getWithLock(KeyTypeEnum keyType, String key, Supplier<T> redisLoader) {
        if (isEnabled(keyType)) {
            T cached = LocalCache.get(REGION, key);
            if (cached != null) {
                return cached;
            }
        }
        T result = redisLoader.get();
        if (result != null && isEnabled(keyType)) {
            LocalCache.put(REGION, key, result);
        }
        return result;
    }

    /**
     * 删除本地缓存。
     */
    public void remove(String key) {
        if (infra.enableL2Caching()) {
            LocalCache.remove(REGION, key);
        }
    }

    /**
     * 批量删除本地缓存。
     */
    public void removeAll(Iterable<String> keys) {
        if (infra.enableL2Caching()) {
            keys.forEach(key -> LocalCache.remove(REGION, key));
        }
    }

    /**
     * 注册 key 类型。
     */
    public void registerType(String key, Class<?> clazz) {
        infra.registerType(key, clazz);
    }
}
