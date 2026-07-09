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
