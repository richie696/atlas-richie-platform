package com.richie.component.cache.commons;

import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * 缓存键工具类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-07-07 13:50:44
 */
public final class CacheKeyUtils {

    private CacheKeyUtils() {
    }

    /**
     * 获取真实的key
     * @param key key
     * @return 真实的key
     */
    public static String getRealKey(@Nonnull String key) {
        if (key.contains("@@")) {
            return key.split("@@")[1];
        }
        return key;
    }

    /**
     * 获取真实的key
     * @param keys keys
     * @return 真实的key
     */
    public static List<String> getRealKeys(List<String> keys) {
        return keys.stream().map(key -> {
            if (key.contains("@@")) {
                return key.split("@@")[1];
            }
            return key;
        }).toList();
    }
}
