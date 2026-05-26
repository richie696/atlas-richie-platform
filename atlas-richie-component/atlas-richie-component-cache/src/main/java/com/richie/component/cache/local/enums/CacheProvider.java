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
    CAFFEINE("com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");

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
