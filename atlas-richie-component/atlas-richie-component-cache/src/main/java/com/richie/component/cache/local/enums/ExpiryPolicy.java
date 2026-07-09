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
package com.richie.component.cache.local.enums;

/**
 * 缓存过期策略枚举
 *
 * @author richie696
 * @version 1.0
 * @since 2023-12-28 17:37:53
 */
public enum ExpiryPolicy {

    /**
     * 根据上次访问缓存条目的时间来定义缓存条目的到期时间（注意：缓存更新不会改变条目的到期时间）
     */
    ACCESSED,

    /**
     * 根据创建缓存条目的时间来定义缓存条目的到期时间（注意：缓存更新不会改变条目的到期时间）
     */
    CREATED,

    /**
     * 永不过期（特定情况下可能会驱逐缓存条目，比如内存不足、缓存底层实现需要时）
     */
    ETERNAL,

    /**
     * 根据缓存条目上次更新的时间定义缓存项的过期持续时间（更新包括创建和更新条目）
     */
    MODIFIED,

    /**
     * 根据缓存项最后一次被触碰的时间定义缓存项的过期持续时间（触摸包括创建、更新或访问）
     */
    TOUCHED
}
