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
package com.richie.component.i18n.handle;

import com.richie.contract.constant.GlobalConstants;
import com.richie.component.cache.GlobalCache;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 国际化字典处理类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-11-04 01:58:54
 */
public record I18nHandle() {

    private static final long TIME_OUT = TimeUnit.DAYS.toMillis(7L);
    /**
     * 注册国际化字典
     *
     * @param i18nDictMap 国际化字典
     */
    public static void addI18nDictionaries(Map<String, Map<String, String>> i18nDictMap) {
        i18nDictMap.forEach((k, v) -> GlobalCache.field().setAll(GlobalConstants.I18N_CACHE_KEY + k, v, TIME_OUT));
    }

    /**
     * 添加国际化字典
     *
     * @param key            字典key
     * @param localeValueMap 国际化字典
     */
    public static void addI18nDictionary(String key, Map<String, String> localeValueMap) {
        localeValueMap.forEach((k, v) -> GlobalCache.field().set(GlobalConstants.I18N_CACHE_KEY + key, k, v));
    }

    /**
     * 获取国际化字典
     *
     * @param key 字典key
     * @return 字典值
     */
    public static Map<String, String> getI18nDictionaries(String key) {
        return Objects.requireNonNullElse(GlobalCache.field().getAll(GlobalConstants.I18N_CACHE_KEY + key, String.class), Map.of());
    }

    /**
     * 获取国际化字典
     *
     * @param key    字典key
     * @param locale 语言
     * @return 字典值
     */
    public static String getI18nDictionary(String key, String locale) {
        String mapCache = GlobalCache.field().get(GlobalConstants.I18N_CACHE_KEY + key, locale, String.class);
        return mapCache == null ? key : mapCache;
    }

    /**
     * 删除国际化字典
     *
     * @param key 字典key
     */
    public static void deleteI18nDictionary(String key) {
        GlobalCache.key().removeCache(GlobalConstants.I18N_CACHE_KEY + key);
    }

}
