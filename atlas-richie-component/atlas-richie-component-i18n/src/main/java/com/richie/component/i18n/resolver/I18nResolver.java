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
package com.richie.component.i18n.resolver;

import java.util.Locale;

/**
 * 国际化服务接口
 *
 * @author richie696
 * @version 1.0
 * @since 2021-10-01 18:18:17
 */
public interface I18nResolver {

    /**
     * 根据国际化资源键和参数获取国际化资源文本的方法
     *
     * @param key 国际化资源键
     * @param args 国际化资源文本动态参数
     * @return 返回国际化文本
     */
    String get(String key, Object... args);

    /**
     * 根据自定义区域和国际化资源键和参数获取国际化资源文本的方法
     *
     * @param locale 自定义区域
     * @param key 国际化资源键
     * @param args 国际化资源文本动态参数
     * @return 返回国际化文本
     */
    String get(Locale locale, String key, Object... args);
}
