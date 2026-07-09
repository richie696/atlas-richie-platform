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
package com.richie.component.cache.local.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 本地缓存类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-12 13:22:35
 */
@Getter
@RequiredArgsConstructor
public enum CacheProvider {

    /** JSR-107 EHCACHE 实现 */
    EHCACHE("org.ehcache.jsr107.EhcacheCachingProvider"),

    /** JSR-107 Caffeine 实现 */
    CAFFEINE("com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider"),

    /** JSR-107 cache2k 实现 */
    CACHE2K("org.cache2k.jcache.provider.JCacheProvider");

    /** JSR-107 CachingProvider 全类名 */
    private final String cachingProvider;

    /**
     * 根据 CachingProvider 全类名解析枚举。
     *
     * @param cachingProvider CachingProvider 全类名
     * @return 对应枚举值
     * @throws IllegalArgumentException 未知 provider 时抛出
     */
    public static CacheProvider valueOfCachingProvider(String cachingProvider) {
        for (CacheProvider provider : values()) {
            if (provider.getCachingProvider().equals(cachingProvider)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown caching provider: %s".formatted(cachingProvider));
    }
}
