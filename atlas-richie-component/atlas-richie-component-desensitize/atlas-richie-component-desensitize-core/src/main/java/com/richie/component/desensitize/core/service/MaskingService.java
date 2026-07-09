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
package com.richie.component.desensitize.core.service;

import com.richie.component.desensitize.core.model.MaskContext;
import com.richie.component.desensitize.core.model.MaskScene;
import com.richie.component.desensitize.core.model.MaskType;

import java.util.LinkedHashMap;
import java.util.Map;
public interface MaskingService {

    /**
     * 使用默认场景执行脱敏。
     *
     * @param raw 原始字符串
     * @param type 脱敏类型
     * @return 脱敏后的字符串
     */
    String mask(String raw, MaskType type);

    /**
     * 在指定场景执行脱敏。
     *
     * @param raw 原始字符串
     * @param type 脱敏类型
     * @param scene 脱敏场景
     * @return 脱敏后的字符串
     */
    String mask(String raw, MaskType type, MaskScene scene);

    /**
     * 在完整上下文中执行脱敏。
     *
     * @param raw 原始字符串
     * @param context 脱敏上下文
     * @param type 脱敏类型
     * @return 脱敏后的字符串
     */
    String mask(String raw, MaskContext context, MaskType type);

    /**
     * 使用默认场景脱敏 Map 中的敏感键值。
     *
     * @param source 原始 Map
     * @return 脱敏后的 Map
     */
    Map<String, Object> maskMap(Map<String, ?> source);

    /**
     * 在指定场景脱敏 Map 中的敏感键值。
     *
     * @param source 原始 Map
     * @param scene 脱敏场景
     * @return 脱敏后的 Map
     */
    Map<String, Object> maskMap(Map<String, ?> source, MaskScene scene);
}
