/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.cache.enums;

/**
 * 缓存键类型枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-16 02:19:41
 */
public enum KeyTypeEnum {

    /**
     * Redis String 类型，适用于存储简单的字符串值
     */
    STRING,

    /**
     * Redis Hash 类型，适用于存储键值对集合的场景
     */
    HASH,

    /**
     * Redis List 类型，适用于需要有序列表的场景
     */
    LIST,

    /**
     * Redis Set 类型，适用于需要唯一性和集合操作的场景
     */
    SET

}
